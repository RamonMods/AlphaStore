package com.ramonmods.alphastore.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.ramonmods.alphastore.AlphaStoreActivity
import com.ramonmods.alphastore.data.*
import com.ramonmods.alphastore.platform.ApkInsights
import com.ramonmods.alphastore.platform.AppTracker
import com.ramonmods.alphastore.platform.DhizukuBridge
import com.ramonmods.alphastore.platform.Downloads
import com.ramonmods.alphastore.platform.ResultHost
import com.ramonmods.alphastore.platform.WirelessAdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the opened file is, read from the APK itself. */
private class Apk(val file: File, val label: String, val pkg: String, val version: String, val code: Long, val icon: Bitmap?, val installed: String?, val installedCode: Long?, val clash: Clash?)

private sealed interface ApkStep {
    data object Reading : ApkStep
    data class Ready(val apk: Apk) : ApkStep
    data class Installing(val apk: Apk) : ApkStep
    data class Scan(val apk: Apk, val vt: com.ramonmods.alphastore.platform.VtScan) : ApkStep
    data class Inspect(val apk: Apk) : ApkStep
    data class Done(val apk: Apk) : ApkStep
    data class Failed(val apk: Apk?, val title: String, val body: String) : ApkStep
}

/** Opens APKs from file managers, browsers and share sheets as a sheet over the app that sent them, then installs them
 *  through the fastest route the user set up: root, Shizuku, Dhizuku or built-in wireless debugging, which skips Android's 24-hour wait. */
class ApkInstallActivity : ComponentActivity() {
    private val tracker by lazy { AppTracker(applicationContext) }
    private var step by mutableStateOf<ApkStep>(ApkStep.Reading)
    private var pending: ((Int, Intent?) -> Unit)? = null
    private val results = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> pending?.also { pending = null }?.invoke(r.resultCode, r.data) }
    private var previousHost: ((Intent, (Int, Intent?) -> Unit) -> Unit)? = null
    /** What is inside the APK, read in the background while the sheet already shows; null until then (or when it can't be read). */
    private var insights by mutableStateOf<ApkInsights.Report?>(null); private var inspecting by mutableStateOf(false)
    /** Beyond install for this sheet: starts as the store's setting, picks start from the last ones used. */
    private var optsOn by mutableStateOf(false); private var opts by mutableStateOf(emptySet<String>()); private var optInstaller by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(applicationContext); Downloads.attach(applicationContext)
        previousHost = ResultHost.launch
        ResultHost.launch = { i, cb -> pending = cb; results.launch(i) }
        tracker.root = Store.settings.flow.value.rootMode
        Store.settings.flow.value.let { st ->
            optsOn = st.installOptions; opts = st.installFlags.filterNot { it.startsWith("installer=") }.toSet()
            optInstaller = st.installFlags.firstOrNull { it.startsWith("installer=") }?.substringAfter('=').orEmpty()
        }
        read(intent)
        setContent {
            val s by Store.settings.flow.collectAsState()
            CompositionLocalProvider(LocalS provides s) { AlphaStoreTheme(s) { Sheet(s) } }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); read(intent) }

    override fun onDestroy() {
        ResultHost.launch = previousHost
        (step as? ApkStep.Ready)?.apk?.file?.delete(); (step as? ApkStep.Failed)?.apk?.file?.delete(); (step as? ApkStep.Scan)?.apk?.file?.delete(); (step as? ApkStep.Inspect)?.apk?.file?.delete()
        super.onDestroy()
    }

    private fun read(i: Intent?) {
        val uri = i?.data ?: (if (i?.action == Intent.ACTION_SEND) @Suppress("DEPRECATION") i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null)
        if (uri == null) { finish(); return }
        step = ApkStep.Reading; insights = null
        lifecycleScope.launch {
            step = withContext(Dispatchers.IO) { runCatching { parse(uri) }.getOrNull() }?.let { ApkStep.Ready(it) }
                ?: ApkStep.Failed(null, "Este arquivo não pode ser instalado", "Não é um APK funcional nem um pacote dividido (.apks, .xapk, .apkm).")
            // Wireless debugging connects while the sheet is read, so Install doesn't sit on discovery
            if (step is ApkStep.Ready && (optionRoute(Store.settings.flow.value) ?: route(Store.settings.flow.value)?.first) == "adb") WirelessAdb.warm(applicationContext)
            (step as? ApkStep.Ready)?.apk?.let { a ->
                inspecting = true
                insights = withContext(Dispatchers.IO) { ApkInsights.read(applicationContext, com.ramonmods.alphastore.platform.Bundles.parts(applicationContext, a.file).firstOrNull() ?: a.file) }
                inspecting = false
                // Switches this APK can't install without are picked for the user; turning options on stays their call
                needed(a).takeIf { it.isNotEmpty() }?.let { opts = opts + it }
            }
        }
    }

    /** Copies the file in first: content URIs only stay readable while this activity lives, and every install route needs a real file. */
    private fun parse(uri: Uri): Apk? {
        // Files from earlier sheets that were swiped away mid-copy
        Downloads.dir().listFiles { x -> x.name.startsWith("Ext_") && System.currentTimeMillis() - x.lastModified() > 3_600_000 }?.forEach { it.delete() }
        val name = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull()
        val f = Downloads.file("Ext_${System.currentTimeMillis()}.${fileExt(name ?: uri.toString())}") ?: return null
        contentResolver.openInputStream(uri)?.use { inp -> f.outputStream().use { inp.copyTo(it) } } ?: return null
        val base = com.ramonmods.alphastore.platform.Bundles.parts(applicationContext, f).firstOrNull() ?: run { f.delete(); return null }
        val pm = packageManager
        val pi = pm.getPackageArchiveInfo(base.absolutePath, 0) ?: run { f.delete(); return null }
        val ai = pi.applicationInfo?.apply { sourceDir = base.absolutePath; publicSourceDir = base.absolutePath }
        val label = ai?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }?.takeIf { it.isNotBlank() && it != pi.packageName } ?: name?.substringBeforeLast('.') ?: pi.packageName
        val inst = tracker.appInfo(pi.packageName).takeIf { it.installed }
        return Apk(f, label, pi.packageName, pi.versionName.orEmpty(), androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi),
            ai?.let { runCatching { it.loadIcon(pm).toBitmap(144, 144) }.getOrNull() }, inst?.version, inst?.versionCode, tracker.clash(f)?.second)
    }

    /** The same order the store uses for its own installs. */
    private fun route(s: Settings): Pair<String, String>? = when {
        s.rootMode -> "root" to "Instala com root, sem avisos e sem a espera de 24 horas"
        s.useShizuku -> "shizuku" to "Instala pelo Shizuku, sem avisos e sem a espera de 24 horas"
        s.useDhizuku -> "dhizuku" to "Instala pelo Dhizuku, sem avisos"
        s.directInstall && WirelessAdb.supported -> "adb" to "Instala instantaneamente pela depuração sem fio, pulando a espera de 24 horas"
        else -> null
    }

    /** Beyond install runs pm install with switches: root, Shizuku or wireless debugging can, Dhizuku and Android's installer can't. */
    private fun optionRoute(s: Settings): String? = when {
        s.rootMode -> "root"
        s.useShizuku || tracker.elevatedReady() -> "shizuku"
        s.directInstall && WirelessAdb.supported -> "adb"
        else -> null
    }

    /** The switches this APK needs on this phone: older than installed, a test build, or a target SDK Android blocks. */
    private fun needed(a: Apk): Set<String> = buildSet {
        if (a.installedCode != null && a.code < a.installedCode) add("downgrade")
        insights?.let { r ->
            if (r.testOnly) add("test")
            val sdk = android.os.Build.VERSION.SDK_INT
            if (sdk >= 34 && r.targetSdk in 1 until (if (sdk >= 35) 24 else 23)) add("lowsdk")
        }
    }

    private fun install(apk: Apk, s: Settings, regular: Boolean = false) = lifecycleScope.launch {
        val withOpts = !regular && optsOn && optionRoute(s) != null
        val picked = if (withOpts) pickedOptions(opts, optInstaller).also { p -> Store.settings.update { it.copy(installFlags = p) } } else null
        val r = if (regular) null else if (withOpts) optionRoute(s) else route(s)?.first
        if (r == null && !tracker.canRequestInstalls()) {
            step = ApkStep.Failed(apk, "Permita que a AlphaStore instale apps", "Ative \"Permitir desta fonte\" para a AlphaStore, depois volte e toque em Instalar novamente.")
            runCatching { startActivity(tracker.installSettingsIntent()) }; return@launch
        }
        step = ApkStep.Installing(apk)
        val err = runCatching {
            when (r) {
                "root", "shizuku" -> { if (r == "shizuku" && !tracker.elevatedReady()) tracker.requestElevated(); tracker.installShizuku(apk.file.name, s.updateLock, picked ?: emptyList()) }
                "dhizuku" -> DhizukuBridge.install(applicationContext, apk.file)
                "adb" -> { if (!WirelessAdb.connected) WirelessAdb.connect(applicationContext); WirelessAdb.install(applicationContext, apk.file, picked) }
                else -> tracker.install(apk.file.name, "system", "")
            }
        }.exceptionOrNull()
        // The system installer reports cancel and failure alike; what is on the phone afterwards is the truth
        val now = withContext(Dispatchers.IO) { tracker.appInfo(apk.pkg) }
        val ok = err == null || (r == null && now.installed && now.versionCode == apk.code && now.versionCode != apk.installedCode)
        if (ok) { apk.file.delete(); step = ApkStep.Done(apk); return@launch }
        val msg = err?.message.orEmpty()
        if (r == null && "INSTALL_CANCELED" in msg) { step = ApkStep.Ready(apk); return@launch }
        // A connection problem is not an install failure: say what wireless debugging needs instead of a generic error
        if (r == "adb" && "INSTALL_" !in msg) { step = ApkStep.Failed(apk, "A depuração sem fio não respondeu", "${msg.ifBlank { "A AlphaStore não conseguiu alcançar a depuração sem fio." }}\n\nTente novamente ou use o instalador do Android."); return@launch }
        val (t, b) = installFailureText(msg, apk.label)
        step = ApkStep.Failed(apk, t, b)
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Sheet(s: Settings) {
        val cs = MaterialTheme.colorScheme
        ModalBottomSheet({ finish() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = cs.surfaceContainerLow,
            sheetMaxWidth = 560.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
                when (val st = step) {
                    ApkStep.Reading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                    is ApkStep.Ready -> {
                        Header(st.apk)
                        val ins = insights; val need = needed(st.apk); val optR = optionRoute(s)
                        // Everything above the buttons scrolls, so the option list never pushes Install off screen
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            st.apk.clash?.let { c -> val (t, b) = clashText(c, st.apk.label); Note(Sym.GppMaybe, t, b, cs.errorContainer, cs.onErrorContainer) }
                            // Refusals Android would only give after the install starts, said before it
                            ins?.let { r ->
                                val sdk = android.os.Build.VERSION.SDK_INT
                                if (r.minSdk > sdk) Note(Sym.ErrorOutline, "Requer Android ${ApkInsights.androidName(r.minSdk)}", "Este celular usa Android ${ApkInsights.androidName(sdk)}, então o Android vai recusar este APK.", cs.errorContainer, cs.onErrorContainer)
                                if (!r.runsHere) Note(Sym.ErrorOutline, "Não feito para este celular", "Ele só traz código ${r.abis.joinToString()}; este celular precisa de ${android.os.Build.SUPPORTED_ABIS.first()}.", cs.errorContainer, cs.onErrorContainer)
                            }
                            val r = route(s)
                            if (optsOn && optR != null) Note(Sym.Tune, "Além da instalação", "Instala via ${when (optR) { "root" -> "root"; "shizuku" -> "Shizuku"; else -> "depuração sem fio" }} com as opções abaixo.", cs.secondaryContainer, cs.onSecondaryContainer)
                            else if (r != null) Note(Sym.Bolt, "Instalação rápida", r.second, cs.secondaryContainer, cs.onSecondaryContainer)
                            else if (WirelessAdb.supported) Note(Sym.Schedule, "Pular a espera de 24 horas", "O Android pode reter apps de desenvolvedores não verificados por um dia. Ative Pular a espera de 24 horas nas configurações da AlphaStore para instalá-los na hora.",
                                cs.surfaceContainerHigh, cs.onSurface) { TextButton({ startActivity(Intent(this@ApkInstallActivity, AlphaStoreActivity::class.java).setData("alphastore://settings/installer".toUri())); finish() }) { Text("Configurar") } }
                            InsightsCard(st.apk, ins)
                            if (optR != null) {
                                Surface({ optsOn = !optsOn }, Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(20.dp), color = if (optsOn) cs.secondaryContainer else cs.surfaceContainerHigh) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Sym.Tune, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Além da instalação", style = MaterialTheme.typography.titleSmall)
                                            Text(if (need.isNotEmpty() && !optsOn) "Este APK precisa de ${need.joinToString { id -> INSTALL_OPTIONS.first { it.id == id }.label.lowercase() }}" else "Downgrade, conceder permissões, instalação como Play Store e mais",
                                                style = MaterialTheme.typography.bodySmall, color = if (need.isNotEmpty() && !optsOn) cs.tertiary else cs.onSurfaceVariant)
                                        }
                                        Switch(optsOn, { optsOn = it })
                                    }
                                }
                                androidx.compose.animation.AnimatedVisibility(optsOn) {
                                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                        InstallOptionsList(opts, optInstaller, { opts = it }, { optInstaller = it }, need) {}
                                    }
                                }
                            }
                        }
                        FilledTonalButton({ step = ApkStep.Scan(st.apk, com.ramonmods.alphastore.platform.VtScan(tracker, "apk:${st.apk.pkg}", st.apk.label, st.apk.pkg, s.virusTotalApiKey)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shapes = ButtonDefaults.shapes()) {
                            Icon(Sym.Security, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Verificar com VirusTotal")
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text("Cancelar") }
                            Button({ install(st.apk, s) }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.InstallMobile, null); Spacer(Modifier.width(8.dp))
                                Text(when { st.apk.installed == null -> "Instalar"; st.apk.installedCode != null && st.apk.code > st.apk.installedCode -> "Atualizar"; else -> "Reinstalar" }, maxLines = 1)
                            }
                        }
                    }
                    is ApkStep.Inspect -> {
                        Header(st.apk)
                        insights?.let { r -> Column(Modifier.weight(1f, fill = false).padding(top = 8.dp).verticalScroll(rememberScrollState())) { InspectPage(r) } }
                        TextButton({ step = ApkStep.Ready(st.apk) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Voltar para a instalação") }
                    }
                    // Your own VirusTotal key, saved with the store's: checks the exact file before it goes in
                    is ApkStep.Scan -> {
                        Header(st.apk)
                        Box(Modifier.padding(top = 16.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                            VtPanel(st.vt, com.ramonmods.alphastore.platform.ScanTarget(com.ramonmods.alphastore.platform.ScanTargetType.DOWNLOADED_APK, st.apk.label, st.apk.file.name), s.virusTotalApiKey,
                                save = { k -> Store.settings.update { it.copy(virusTotalApiKey = k) } }, revoke = { Store.settings.update { it.copy(virusTotalApiKey = "") }; st.vt.reset(); st.vt.view = com.ramonmods.alphastore.platform.VtView.KEY },
                                open = { tracker.open(it) }, onDone = { step = ApkStep.Ready(st.apk) })
                        }
                        if (st.vt.view != com.ramonmods.alphastore.platform.VtView.SCANNING) TextButton({ step = ApkStep.Ready(st.apk) }, Modifier.fillMaxWidth()) { Text("Voltar para a instalação") }
                    }
                    is ApkStep.Installing -> {
                        Header(st.apk)
                        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(40.dp)); Spacer(Modifier.width(12.dp)); Text("Instalando...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    is ApkStep.Done -> {
                        Header(st.apk)
                        Note(Sym.CheckCircle, "Instalado", "${st.apk.label} está pronto. Se ele estiver no catálogo da AlphaStore, a AlphaStore o manterá atualizado daqui para frente.", cs.primaryContainer, cs.onPrimaryContainer)
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text("Concluir") }
                            packageManager.getLaunchIntentForPackage(st.apk.pkg)?.let { li ->
                                Button({ runCatching { startActivity(li) }; finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                    Icon(Sym.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Abrir")
                                }
                            }
                        }
                    }
                    is ApkStep.Failed -> {
                        st.apk?.let { Header(it) }
                        Note(Sym.ErrorOutline, st.title, st.body, cs.errorContainer, cs.onErrorContainer)
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text("Fechar") }
                            st.apk?.let { a -> Button({ install(a, s, regular = route(s) != null) }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                Text(if (route(s) != null) "Usar o instalador do Android" else "Tentar novamente", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } }
                        }
                    }
                }
            }
        }
    }

    /** The at-a-glance read of what is inside: one chip per finding, warnings tinted, and the full page one tap away. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun InsightsCard(a: Apk, r: ApkInsights.Report?) {
        val cs = MaterialTheme.colorScheme
        if (r == null && !inspecting) return
        Surface({ if (r != null) step = ApkStep.Inspect(a) }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = r != null, shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Science, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                    Text("Por dentro deste APK", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (r == null) LoadingIndicator(Modifier.size(24.dp)) else Icon(Sym.ChevronRight, "Ver tudo", tint = cs.onSurfaceVariant)
                }
                if (r == null) { Text("Lendo código, bibliotecas e permissões...", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant); return@Column }
                InsightChips(r, Modifier.padding(top = 10.dp))
            }
        }
    }

    @Composable
    private fun Header(a: Apk) {
        val cs = MaterialTheme.colorScheme
        Row(verticalAlignment = Alignment.CenterVertically) {
            a.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))) } ?: Blob(Sym.Android, cs.primary, 64)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(a.version.ifBlank { null }?.let { "Versão $it" }, a.installed?.let { "instalado $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${CatalogProcessor.formatSize(a.file.length())} · ${if (a.installed == null) "App novo" else if (a.installedCode != null && a.code < a.installedCode) "Mais antigo que o instalado" else "Atualização"}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, action: (@Composable () -> Unit)? = null) =
        Surface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(20.dp), color = bg, contentColor = fg) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
                    action?.invoke()
                }
            }
        }
}

@Composable
fun InsightChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warn: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = Pill, color = if (warn) cs.tertiaryContainer else cs.surfaceContainerHighest, contentColor = if (warn) cs.onTertiaryContainer else cs.onSurface) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

fun abiShort(abi: String) = when (abi) { "arm64-v8a" -> "arm64"; "armeabi-v7a" -> "arm32"; "armeabi" -> "arm (antigo)"; else -> abi }

/** Everything AlphaStore read from the APK, section by section. */
@Composable
fun InspectPage(r: ApkInsights.Report) {
    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val sdk = android.os.Build.VERSION.SDK_INT; val phone = android.os.Build.SUPPORTED_ABIS.toSet()
    var allPerms by remember { mutableStateOf(false) }; var allLibs by remember { mutableStateOf(false) }
    @Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Text(title, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp), style = ty.labelLarge, color = cs.primary)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
    }
    @Composable fun Line(label: String, value: String, warn: Boolean = false, sub: String? = null) = Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, style = ty.bodyMedium)
            sub?.let { Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = ty.labelLarge, color = if (warn) cs.tertiary else cs.onSurfaceVariant, maxLines = 2)
    }

    Section("Feito com") {
        if (r.frameworks.isEmpty()) Text("Android puro: Java ou Kotlin com as views nativas do Android.", style = ty.bodyMedium)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { r.frameworks.forEach { InsightChip(Sym.Layers, it) } }
        Text("${r.dexCount} arquivo${if (r.dexCount == 1) "" else "s"} dex · ${"%,d".format(r.classCount)} classes", style = ty.bodySmall, color = cs.onSurfaceVariant)
    }
    Section("Compatibilidade") {
        Line("Mínimo", "Android ${ApkInsights.androidName(r.minSdk)} (API ${r.minSdk})", warn = r.minSdk > sdk, sub = if (r.minSdk > sdk) "Este celular usa Android ${ApkInsights.androidName(sdk)}" else null)
        Line("Alvo", "Android ${ApkInsights.androidName(r.targetSdk)} (API ${r.targetSdk})", warn = sdk >= 34 && r.targetSdk < 24,
            sub = if (sdk >= 34 && r.targetSdk < 24) "O Android bloqueia apps tão antigos. Em Além da instalação há a opção Ignorar bloqueio de SDK alvo" else null)
        if (r.compileSdk > 0) Line("Compilado com", "API ${r.compileSdk}")
        Line("Processador", if (r.abis.isEmpty()) "Qualquer" else r.abis.joinToString { abiShort(it) }, warn = !r.runsHere,
            sub = if (r.abis.isEmpty()) "Sem código nativo, então roda em qualquer celular" else if (r.runsHere) "Roda neste celular (${phone.filter { it in r.abis }.joinToString { abiShort(it) }})" else "Não roda neste celular")
        r.pageReady?.let { ok ->
            Line("Páginas de 16 KB", if (ok) "Pronto" else "${r.unaligned.size} não prontos", warn = !ok,
                sub = if (ok) "Todas as bibliotecas de 64 bits funcionam em celulares Android 15+ com páginas de memória de 16 KB" else "Celulares com páginas de 16 KB rodam em modo de compatibilidade ou recusam: ${r.unaligned.joinToString { it.name }.take(160)}")
        }
        if (r.debuggable || r.testOnly) Line("Compilação", listOfNotNull("Depurável".takeIf { r.debuggable }, "Somente teste".takeIf { r.testOnly }).joinToString(" · "), warn = true,
            sub = if (r.testOnly) "Precisa de Permitir builds de teste em Além da instalação" else null)
    }
    Section("Rastreadores") {
        if (r.trackers.isEmpty()) Text("Nenhum SDK de rastreador conhecido foi encontrado no código.", style = ty.bodyMedium)
        else r.trackers.forEach { (n, c) -> Line(n, c, warn = c == "Ads" || c == "Attribution") }
        Text("Verificado offline contra assinaturas de rastreadores, do mesmo modo que o Exodus Privacy.", style = ty.bodySmall, color = cs.onSurfaceVariant)
    }
    Section("Permissões · ${r.perms.size}") {
        if (r.perms.isEmpty()) Text("Não pede nada.", style = ty.bodyMedium)
        (if (allPerms) r.perms else r.perms.take(8)).forEach { p ->
            Line(ApkInsights.permLabel(p.name), listOfNotNull("Nova".takeIf { p.isNew }, "Sensível".takeIf { p.dangerous }).joinToString(" · "), warn = p.dangerous, sub = p.name)
        }
        if (r.perms.size > 8) TextButton({ allPerms = !allPerms }) { Text(if (allPerms) "Mostrar menos" else "Mostrar todas as ${r.perms.size}") }
    }
    Section("Componentes") {
        r.components.forEach { c -> Line(c.kind, "${c.total}" + if (c.exported > 0) " · ${c.exported} abertos para outros apps" else "") }
    }
    if (r.libs.isNotEmpty()) Section("Bibliotecas nativas · ${r.libs.size}") {
        (if (allLibs) r.libs else r.libs.take(8)).forEach { l ->
            val bad = l.abi in ApkInsights.ABI64 && l.align != null && l.align < ApkInsights.PAGE_16K
            Line(l.name, "${abiShort(l.abi)} · ${CatalogProcessor.formatSize(l.size)}", warn = bad, sub = listOfNotNull(l.known, "Não alinhado para 16 KB".takeIf { bad }).joinToString(" · ").ifEmpty { null })
        }
        if (r.libs.size > 8) TextButton({ allLibs = !allLibs }) { Text(if (allLibs) "Mostrar menos" else "Mostrar todas as ${r.libs.size}") }
    }
    Section("Tamanho") {
        val total = r.sizes.sumOf { it.second }.coerceAtLeast(1)
        r.sizes.forEach { (g, b) ->
            Column {
                Line(g, CatalogProcessor.formatSize(b))
                LinearProgressIndicator({ b.toFloat() / total }, Modifier.fillMaxWidth().padding(top = 4.dp), drawStopIndicator = {})
            }
        }
    }
    r.signer?.let { sig ->
        Section("Assinatura") {
            Line("SHA-256", if (r.debugSigned) "Chave de depuração" else "", warn = r.debugSigned, sub = if (r.debugSigned) "Assinado com a chave de depuração pública do Android, então qualquer um pode assinar uma atualização para ele" else null)
            Text(sig, style = ty.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = cs.onSurfaceVariant)
            TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(sig)) }) { Icon(Sym.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copiar") }
        }
    }
}

/** One chip per finding, warnings tinted: the at-a-glance read used by the installer sheet and the app page. */
@Composable
fun InsightChips(r: ApkInsights.Report, modifier: Modifier = Modifier) =
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        r.frameworks.forEach { InsightChip(Sym.Layers, it) }
        InsightChip(Sym.Android, "Alvo: Android ${ApkInsights.androidName(r.targetSdk)}")
        InsightChip(Sym.Memory, if (r.abis.isEmpty()) "Sem código nativo" else r.abis.joinToString(" · ") { abiShort(it) }, warn = !r.runsHere)
        r.pageReady?.let { InsightChip(if (it) Sym.CheckCircle else Sym.WarningAmber, if (it) "Pronto para 16 KB" else "Não pronto para 16 KB", warn = !it) }
        InsightChip(Sym.Radar, when (r.trackers.size) { 0 -> "Nenhum rastreador encontrado"; 1 -> "1 rastreador"; else -> "${r.trackers.size} rastreadores" }, warn = r.trackers.isNotEmpty())
        InsightChip(Sym.Policy, "${r.perms.size} permissões" + r.newPerms.size.takeIf { it > 0 }?.let { " · $it novas" }.orEmpty(), warn = r.newPerms.any { it.dangerous })
        if (r.debuggable || r.debugSigned) InsightChip(Sym.BugReport, "Versão de depuração", warn = true)
    }
