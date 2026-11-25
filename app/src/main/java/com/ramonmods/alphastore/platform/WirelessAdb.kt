package com.ramonmods.alphastore.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.edit
import com.ramonmods.alphastore.R
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/** Cliente ADB sem fio embutido que fala com o adbd deste dispositivo. As instalações rodam como usuário shell, o mesmo caminho do
 *  `adb install`, que o Android isenta da espera de 24 horas de desenvolvedor não verificado. Emparehe uma vez; depois o AlphaStore
 *  se reconecta sozinho e, mantendo WRITE_SECURE_SETTINGS (auto-concedido via ADB), reativa a Depuração sem fio após reinicializações. */
object WirelessAdb {
    enum class Phase { IDLE, WAITING, PAIRING, READY, FAILED }
    data class Status(val phase: Phase = Phase.IDLE, val msg: String = "")

    private val _status = MutableStateFlow(Status())
    val status = _status.asStateFlow()
    val supported get() = Build.VERSION.SDK_INT >= 30
    val connected get() = m?.isConnected == true

    const val KEY_CODE = "code"
    private const val CHANNEL = "alphastore_wireless_adb"
    private const val NOTIF = 7310
    private const val ADB_WIFI = "adb_wifi_enabled"
    // adbd listens on every interface, so localhost works without depending on the Wi-Fi address
    private const val HOST = "127.0.0.1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Identidade RSA que o adbd lembra após o emparelhamento. Fica em armazenamento sem backup para que uma restauração em outro telefone não leve junto. */
    private class Manager(ctx: Context) : AbsAdbConnectionManager() {
        private val pk: PrivateKey
        private val crt: Certificate
        init {
            setApi(Build.VERSION.SDK_INT)
            setTimeout(10, TimeUnit.SECONDS)
            val dir = File(ctx.noBackupFilesDir, "adb").apply { mkdirs() }
            val kf = File(dir, "key"); val cf = File(dir, "cert")
            if (kf.exists() && cf.exists()) {
                pk = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
                crt = cf.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            } else {
                val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val name = X500Name("CN=AlphaStore"); val now = System.currentTimeMillis()
                crt = JcaX509CertificateConverter().getCertificate(
                    JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now), Date(now + 3650L * 86_400_000), name, kp.public)
                        .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private)))
                pk = kp.private
                kf.writeBytes(pk.encoded); cf.writeBytes(crt.encoded)
            }
        }
        override fun getPrivateKey() = pk
        override fun getCertificate() = crt
        override fun getDeviceName() = "AlphaStore"
    }

    @Volatile private var m: Manager? = null
    private fun mgr(ctx: Context) = m ?: synchronized(this) { m ?: Manager(ctx.applicationContext).also { m = it } }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("wireless_adb", Context.MODE_PRIVATE)

    fun onWifi(ctx: Context): Boolean = ctx.getSystemService(ConnectivityManager::class.java).let {
        it.getNetworkCapabilities(it.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    private fun global(ctx: Context, key: String) = runCatching { Settings.Global.getInt(ctx.contentResolver, key, 0) == 1 }.getOrDefault(false)
    fun devOptions(ctx: Context) = global(ctx, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
    fun wirelessOn(ctx: Context) = global(ctx, ADB_WIFI)
    /** AlphaStore has reached adbd before (pairing done), so connecting is worth a try. */
    fun paired(ctx: Context) = prefs(ctx).getInt("port", 0) > 0
    private fun canToggle(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun aboutPhoneIntent(): Intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    /** Opções do desenvolvedor, rolada até a linha Depuração sem fio onde a plataforma suporta o extra de destaque. */
    private fun wirelessDebuggingIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(":settings:fragment_args_key", "toggle_adb_wireless").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private suspend fun discover(ctx: Context, type: String, ms: Long): Int? {
        val found = CompletableDeferred<Int>()
        val mdns = AdbMdns(ctx, type) { h, p -> if (h != null && p > 0) found.complete(p) }
        mdns.start()
        return try { withTimeoutOrNull(ms) { found.await() } } finally { mdns.stop() }
    }

    private fun tryConnect(mg: Manager, port: Int) = runCatching { mg.connect(HOST, port) }.getOrDefault(false) || mg.isConnected

    /** Conecta ao adbd: primeiro à última porta conhecida (instantâneo), depois mDNS, ligando a Depuração sem fio quando o AlphaStore tem permissão para. */
    suspend fun connect(ctx: Context): Unit = lock.withLock {
        withContext(Dispatchers.IO) {
            val mg = mgr(ctx)
            if (mg.isConnected) return@withContext
            val last = prefs(ctx).getInt("port", 0)
            if (last <= 0 || !tryConnect(mg, last)) {
                if (!onWifi(ctx)) throw NativeError("Conecte-se ao Wi-Fi para instalar sem a espera.")
                val cr = ctx.contentResolver; val toggle = canToggle(ctx)
                // Como o Shizuku: com WRITE_SECURE_SETTINGS, o AlphaStore devolve as Opções do desenvolvedor e a Depuração sem fio sozinhos, e desliga
                // o tempo limite de autorização de 7 dias que deixaria o Android esquecer o emparelhamento
                if (toggle) runCatching {
                    if (!devOptions(ctx)) Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                    Settings.Global.putLong(cr, "adb_allowed_connection_time", 0)
                    if (!wirelessOn(ctx)) Settings.Global.putInt(cr, ADB_WIFI, 1)
                }
                var port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, 15_000)
                // Ligado mas silencioso (Wi-Fi mudou, adbd travado): reinicia a Depuração sem fio uma vez
                if (port == null && toggle) {
                    runCatching { Settings.Global.putInt(cr, ADB_WIFI, 0); delay(1500); Settings.Global.putInt(cr, ADB_WIFI, 1) }
                    port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, 15_000)
                }
                val found = port ?: throw NativeError(if (toggle) "A depuração sem fio não começou neste Wi-Fi. Abra a depuração sem fio uma vez e permita esta rede." else "A depuração sem fio está desligada. Ative-a ou rode a configuração novamente.")
                if (!tryConnect(mg, found)) throw NativeError("A depuração sem fio recusou o AlphaStore. Rode a configuração novamente para reemparelhar.")
                prefs(ctx).edit { putInt("port", found) }
            }
            if (!canToggle(ctx)) runCatching { exec(mg, "pm grant ${ctx.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}") }
        }
    }

    /** Conecta sem esperar, iniciado quando um download começa para a própria instalação não esperar pela descoberta. */
    fun warm(ctx: Context) { if (!connected) scope.launch { runCatching { connect(ctx.applicationContext) } } }

    private fun exec(mg: Manager, cmd: String, input: File? = null): String = mg.openStream("exec:$cmd").use { s ->
        // AdbOutputStream.close only flushes, so the stream stays open for the reply
        input?.let { f -> s.openOutputStream().use { o -> f.inputStream().use { it.copyTo(o) } } }
        s.openInputStream().bufferedReader().readText()
    }

    /** [opts]: além das escolhas de instalação; o usuário shell pode passar todos os switches de instalação do pm, igual ao root e Shizuku. */
    suspend fun install(ctx: Context, f: File, opts: Collection<String>? = null) = withContext(Dispatchers.IO) {
        val parts = Bundles.parts(ctx, f)
        if (parts.size > 1) { installMany(ctx, parts, opts); return@withContext }
        val one = parts.firstOrNull() ?: f
        val cmd = "pm install -r${opts?.let { com.ramonmods.alphastore.data.installArgs(it, Build.VERSION.SDK_INT, "") }.orEmpty()} -S ${one.length()}"
        val pi = runCatching { ctx.packageManager.getPackageArchiveInfo(one.absolutePath, 0) }.getOrNull()
        val t0 = System.currentTimeMillis()
        connect(ctx)
        // O adbd às vezes deixa o stream aberto depois que o pm imprime Success, então a resposta nunca termina. O pacote diz quando chegou.
        val reply = CompletableDeferred<Result<String>>()
        scope.launch {
            reply.complete(runCatching {
                try { exec(mgr(ctx), cmd, one) } catch (e: Exception) {
                    if (reply.isCompleted) throw e
                    // Stale socket after a Wi-Fi drop or adbd restart: reconnect once
                    runCatching { mgr(ctx).disconnect() }; connect(ctx); exec(mgr(ctx), cmd, one)
                }
            })
        }
        fun landed() = pi != null && runCatching {
            val p = ctx.packageManager.getPackageInfo(pi.packageName, 0)
            p.lastUpdateTime >= t0 - 2000 && androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(p) == androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi)
        }.getOrDefault(false)
        val out = withTimeoutOrNull(5 * 60_000L) {
            while (!reply.isCompleted) {
                delay(700)
                if (landed()) { reply.complete(Result.success("Success")); runCatching { mgr(ctx).disconnect() } } // libera o stream travado; a próxima chamada reconecta na porta salva
            }
            reply.await().getOrThrow()
        } ?: if (landed()) "Success" else throw NativeError("A depuração sem fio parou de responder. Tente novamente.")
        if ("Success" !in out) throw NativeError(out.trim().ifEmpty { "INSTALL_FAILED" })
    }

    private suspend fun installMany(ctx: Context, files: List<File>, opts: Collection<String>?) {
        connect(ctx)
        val args = opts?.let { com.ramonmods.alphastore.data.installArgs(it, Build.VERSION.SDK_INT, "") }.orEmpty()
        val created = exec(mgr(ctx), "pm install-create -r$args -S ${files.sumOf { it.length() }}")
        val id = Regex("\\[(\\d+)]").find(created)?.groupValues?.get(1) ?: throw NativeError(created.trim().ifEmpty { "INSTALL_FAILED" })
        try {
            files.forEachIndexed { i, f ->
                val wrote = exec(mgr(ctx), "pm install-write -S ${f.length()} $id ${if (i == 0) "base.apk" else "split$i.apk"} -", f)
                if ("Failure" in wrote) throw NativeError(wrote.trim())
            }
            val done = exec(mgr(ctx), "pm install-commit $id")
            if ("Success" !in done) throw NativeError(done.trim().ifEmpty { "INSTALL_FAILED" })
        } catch (e: Exception) {
            runCatching { exec(mgr(ctx), "pm install-abandon $id") }
            throw e
        }
    }

    /** Roda um comando shell sobre ADB como usuário shell, reconectando uma vez num socket obsoleto. Uma resposta que o adbd nunca fecha é cortada após [ms]. */
    suspend fun shell(ctx: Context, cmd: String, ms: Long = 20_000): String = withContext(Dispatchers.IO) {
        connect(ctx)
        val reply = scope.async {
            runCatching {
                try { exec(mgr(ctx), cmd) } catch (_: Exception) {
                    runCatching { mgr(ctx).disconnect() }; connect(ctx); exec(mgr(ctx), cmd)
                }
            }
        }
        withTimeoutOrNull(ms) { reply.await() }?.getOrThrow()
            ?: run { runCatching { mgr(ctx).disconnect() }; throw NativeError("A depuração sem fio parou de responder. Tente novamente.") }
    }

    // ---------------- Configuração guiada ----------------
    // Roda da aba de notificações: as Configurações desligam o diálogo de emparelhamento ao sair da tela, e o AlphaStore não pode
    // abrir páginas em segundo plano, então cada passo é uma notificação tocável que avança sozinho quando as chaves mudam.

    private data class Step(val open: Intent, val title: String, val text: String, val code: Boolean)
    private var observer: ContentObserver? = null

    fun startSetup(ctx: Context) {
        val app = ctx.applicationContext
        if (_status.value.phase == Phase.READY || _status.value.phase == Phase.FAILED) _status.value = Status()
        if (observer == null) observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = advance(app, false)
        }.also { o -> listOf(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, ADB_WIFI).forEach { app.contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, o) } }
        advance(app, true)
    }

    private fun stopSetup(ctx: Context) { observer?.let { ctx.contentResolver.unregisterContentObserver(it) }; observer = null }

    private fun advance(ctx: Context, open: Boolean) {
        if (_status.value.phase == Phase.PAIRING || _status.value.phase == Phase.READY) return
        val step = when {
            !devOptions(ctx) -> Step(aboutPhoneIntent(), "Passo 1 de 3 · Desbloqueie as Opções do desenvolvedor",
                "Toque no Número da versão 7 vezes (em alguns telefones fica em Informações do software). O AlphaStore avança sozinho.", false)
            !wirelessOn(ctx) -> Step(wirelessDebuggingIntent(), "Passo 2 de 3 · Ative a depuração sem fio",
                "Toque aqui, abra a depuração sem fio, ative-a e toque em Permitir.", false)
            else -> Step(wirelessDebuggingIntent(), "Passo 3 de 3 · Empareie o AlphaStore",
                "Em Depuração sem fio toque em Emparelhar dispositivo com código de emparelhamento, depois digite o código de 6 dígitos aqui.", true)
        }
        _status.value = Status(Phase.WAITING, step.title)
        notify(ctx, step.title, step.text, code = step.code, open = step.open)
        if (open) runCatching { ctx.startActivity(step.open) }
    }

    suspend fun pair(ctx: Context, code: String) = withContext(Dispatchers.IO) {
        _status.value = Status(Phase.PAIRING, "Emparehando...")
        notify(ctx, "Emparehando...", "Mantenha o diálogo do código de emparelhamento aberto.", quiet = true)
        try {
            val port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_PAIRING, 10_000)
                ?: throw NativeError("Diálogo de emparelhamento não encontrado. Abra novamente e use o novo código.")
            try { mgr(ctx).pair(HOST, port, code) } catch (_: Exception) { throw NativeError("Código inválido ou expirado. Tente novamente com um novo.") }
            connect(ctx)
            stopSetup(ctx)
            _status.value = Status(Phase.READY, "Emparelhado. As instalações agora pulam a espera.")
            notify(ctx, "AlphaStore emparejado", "Apps agora instalam instantaneamente, sem a espera de 24 horas.", done = true)
        } catch (e: Exception) {
            val msg = (e as? NativeError)?.message ?: "Falha no emparelhamento. Tente novamente."
            _status.value = Status(Phase.FAILED, msg)
            notify(ctx, "Falha no emparelhamento", msg, code = true, open = wirelessDebuggingIntent())
        }
    }

    fun pairAsync(ctx: Context, code: String) = scope.launch { pair(ctx.applicationContext, code) }

    @SuppressLint("MissingPermission")
    private fun notify(ctx: Context, title: String, text: String, code: Boolean = false, done: Boolean = false, open: Intent? = null, quiet: Boolean = false) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH).setName("Configuração de instalação sem fio").build())
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setOnlyAlertOnce(quiet).setAutoCancel(done).apply { if (done) setTimeoutAfter(10_000) }
        open?.let { n.setContentIntent(PendingIntent.getActivity(ctx, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
        if (code) {
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, AdbPairReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            n.addAction(NotificationCompat.Action.Builder(0, "Inserir código", pi)
                .addRemoteInput(RemoteInput.Builder(KEY_CODE).setLabel("Código de emparelhamento de 6 dígitos").build()).build())
        }
        nm.notify(NOTIF, n.build())
    }
}

class AdbPairReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val code = RemoteInput.getResultsFromIntent(i)?.getCharSequence(WirelessAdb.KEY_CODE)?.filter(Char::isDigit)?.toString() ?: return
        val done = goAsync()
        WirelessAdb.pairAsync(ctx, code).invokeOnCompletion { done.finish() }
    }
}
