package com.ramonmods.alphastore.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.ramonmods.alphastore.InstallReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Uma sessão do PackageInstaller para um APK único ou um conjunto dividido (XAPK, APKM, APKS). */
object SessionInstall {
    private val sessions = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String?>>>()
    private val confirm = ConcurrentHashMap.newKeySet<Int>()

    suspend fun commit(ctx: Context, files: List<File>, pkg: String?, ask: Boolean): Pair<Int, String?> = withContext(Dispatchers.IO) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (!pkg.isNullOrBlank()) setAppPackageName(pkg)
            setSize(files.sumOf { it.length() }); setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (!ask && Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params); val done = CompletableDeferred<Pair<Int, String?>>(); sessions[id] = done
        if (ask) confirm += id
        try {
            pi.openSession(id).use { session ->
                files.forEachIndexed { i, f ->
                    session.openWrite(if (i == 0) "base.apk" else "split$i.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                }
                val intent = Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName)
                session.commit(PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)
            }
            withTimeoutOrNull(if (ask) 5 * 60_000L else 3 * 60_000L) { done.await() }
                ?: (PackageInstaller.STATUS_FAILURE to "O Android não respondeu a tempo. O AutoUpdate tenta de novo no próximo ciclo.")
        } catch (e: Exception) {
            runCatching { pi.abandonSession(id) }; PackageInstaller.STATUS_FAILURE to e.message
        } finally { sessions -= id; confirm -= id }
    }

    /** Resultados da sessão. Uma instalação em segundo plano descarta o diálogo de confirmação. Uma instalação que o usuário acabou de iniciar mostra. Verdadeiro quando esta sessão é nossa. */
    fun onResult(ctx: Context, i: Intent): Boolean {
        val id = i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessions[id] == null && id !in confirm) return false
        val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && id in confirm) {
            @Suppress("DEPRECATION")
            val next = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra("android.content.pm.extra.INTENT", Intent::class.java) else i.getParcelableExtra("android.content.pm.extra.INTENT")
            if (next != null) {
                next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(next) }.onFailure { sessions[id]?.complete(status to "O Android pediu para confirmar a instalação.") }
                return true
            }
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) runCatching { ctx.packageManager.packageInstaller.abandonSession(id) }
        confirm -= id
        sessions[id]?.complete(status to i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        return true
    }
}
