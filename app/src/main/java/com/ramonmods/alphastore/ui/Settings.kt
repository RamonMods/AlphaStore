package com.ramonmods.alphastore.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.ramonmods.alphastore.data.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ramonmods.alphastore.R
import com.ramonmods.alphastore.platform.DhizukuBridge
import com.ramonmods.alphastore.platform.Installer
import com.ramonmods.alphastore.platform.AlphaStoreAi
import com.ramonmods.alphastore.platform.WirelessAdb
import com.ramonmods.alphastore.platform.ResultHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

val Pink = Color(0xFFEC4899); val Orange = Color(0xFFF97316); val Purple = Color(0xFFA855F7)
val Green = Color(0xFF22C55E); val Yellow = Color(0xFFEAB308); val SkyC = Color(0xFF38BDF8)
val Fuchsia = Color(0xFFD946EF); val Teal = Color(0xFF14B8A6); val Cyan = Color(0xFF06B6D4)


// region identity (.osf) port of handleExportIdentity / handleImportIdentity
private const val ID_SALT = "ALPHASTORE_PROTOCOL_OMEGA_8842_SECURE_HASH_V1"
private fun jsNum(f: Float) = if (f % 1f == 0f) JsonPrimitive(f.toLong()) else JsonPrimitive(f.toString().toDouble())

fun identityExport(s: Settings, favorites: List<String>, now: Long): String {
    val data = buildJsonObject {
        put("adWatchCount", s.adWatchCount); put("submissionCount", s.submissionCount); put("changeRequestCount", s.changeRequestCount)
        put("lastSubmissionTime", s.lastSubmissionTime); put("lastLeaderboardSubmissionTime", s.lastLeaderboardSubmissionTime)
        put("isLegend", s.isLegend); put("isContributor", s.isContributor); put("isDevUnlocked", s.isDevUnlocked)
        put("theme", s.theme); put("palette", s.palette); put("appFont", s.appFont); put("customFonts", json.encodeToJsonElement(s.customFonts)); put("customPalettes", json.encodeToJsonElement(s.customPalettes)); put("storeLayout", s.storeLayout)
        put("isOled", s.isOled); put("hiddenTabs", json.encodeToJsonElement(s.hiddenTabs)); put("disableAnimations", s.disableAnimations)
        put("compactMode", s.compactMode); put("showAppAbout", s.showAppAbout); put("bottomNavScale", jsNum(s.bottomNavScale)); put("highRefreshRate", s.highRefreshRate); put("immersive", s.immersive); put("perfMode", s.perfMode)
        put("pullToRefreshCharacter", s.pullToRefreshCharacter); put("hapticEnabled", s.hapticEnabled); put("glassEffect", s.glassEffect)
        put("quickTiles", s.quickTiles?.let { json.encodeToJsonElement(it) } ?: JsonNull); put("settingsOrder", json.encodeToJsonElement(s.settingsOrder))
        put("autoUpdateEnabled", s.autoUpdateEnabled); put("wifiOnly", s.wifiOnly); put("stableOnly", s.stableOnly); put("deleteApk", s.deleteApk); put("useShizuku", s.useShizuku)
        put("installerPreference", s.installerPreference); put("installerPackage", s.installerPackage); put("installerLabel", s.installerLabel)
        put("useRemoteJson", s.useRemoteJson); put("loadLocalData", s.loadLocalData); put("appStreams", json.encodeToJsonElement(s.appStreams))
        put("ignoredUpdates", json.encodeToJsonElement(s.ignoredUpdates)); put("appManage", json.encodeToJsonElement(s.appManage)); put("skipOtherStores", s.skipOtherStores)
        put("myAppsFilters", json.encodeToJsonElement(s.myAppsFilters)); put("userProfile", s.userProfile?.let { json.encodeToJsonElement(it) } ?: JsonNull)
        put("customBundles", json.encodeToJsonElement(s.customBundles)); put("removedBundleApps", json.encodeToJsonElement(s.removedBundleApps))
        put("extraBundleApps", json.encodeToJsonElement(s.extraBundleApps)); put("favorites", json.encodeToJsonElement(favorites)); put("timestamp", now)
    }
    val pkg = buildJsonObject { put("data", data); put("sig", sha256Hex((data.toString() + ID_SALT).toByteArray())); put("ver", "1.1") }
    return Base64.encodeToString(pkg.toString().toByteArray(), Base64.NO_WRAP)
}

class IdentityError(msg: String) : Exception(msg)

private inline fun <reified T> JsonObject.or(k: String, fb: T): T =
    get(k)?.takeIf { it !is JsonNull }?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() } ?: fb

/** Returns merged settings plus favorites (null = keep current). Throws IdentityError with the user-facing message. */
fun identityImport(raw: String, c: Settings): Pair<Settings, List<String>?> {
    val text = runCatching { String(Base64.decode(raw.trim(), Base64.DEFAULT)) }.getOrNull()?.takeIf { it.trimStart().startsWith("{") } ?: raw
    val d: JsonObject; val sig: String
    try {
        val pkg = json.parseToJsonElement(text).jsonObject
        d = pkg["data"] as? JsonObject ?: throw IllegalStateException("Invalid Save File")
        sig = (pkg["sig"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("Invalid Save File")
    } catch (_: Exception) { throw IdentityError("Arquivo de backup corrompido ou incompatível.") }
    if (sha256Hex((d.toString() + ID_SALT).toByteArray()) != sig) throw IdentityError("Adulteração detectada. A verificação de integridade do backup falhou.")
    val merged = try { mergeIdentity(d, c) } catch (_: Exception) { throw IdentityError("Arquivo de backup corrompido ou incompatível.") }
    // The signing salt ships in the APK, so a valid signature alone doesn't prove the numbers are real
    if (!Xp.plausible(merged, System.currentTimeMillis())) throw IdentityError("Este backup tem mais progresso do que seria possível ganhar. Ele não foi restaurado.")
    // Tier badges follow the level: drop the claimed ones and the level watcher grants back whatever the restored XP really earns
    val fair = merged.copy(unlockedBadges = merged.unlockedBadges.filter { b -> TIER_BADGES.none { it.second == b } })
    return fair to (d["favorites"] as? JsonArray)?.let { d.or<List<String>?>("favorites", null) }
}

/** The identity fields, read leniently one by one: shared by backup import and the web carry-over fallback. */
fun mergeIdentity(d: JsonObject, c: Settings): Settings =
    c.copy(
        adWatchCount = d.or("adWatchCount", c.adWatchCount), submissionCount = d.or("submissionCount", c.submissionCount),
        changeRequestCount = d.or("changeRequestCount", c.changeRequestCount), lastSubmissionTime = d.or("lastSubmissionTime", c.lastSubmissionTime),
        lastLeaderboardSubmissionTime = d.or("lastLeaderboardSubmissionTime", c.lastLeaderboardSubmissionTime),
        isLegend = d.or("isLegend", c.isLegend), isContributor = d.or("isContributor", c.isContributor), isDevUnlocked = d.or("isDevUnlocked", c.isDevUnlocked),
        theme = d.or("theme", c.theme), palette = d.or("palette", c.palette), appFont = d.or("appFont", c.appFont), customFonts = d.or("customFonts", c.customFonts), customPalettes = d.or("customPalettes", c.customPalettes),
        storeLayout = d.or("storeLayout", c.storeLayout), isOled = d.or("isOled", c.isOled), hiddenTabs = d.or("hiddenTabs", c.hiddenTabs),
        disableAnimations = d.or("disableAnimations", c.disableAnimations), compactMode = d.or("compactMode", c.compactMode), showAppAbout = d.or("showAppAbout", c.showAppAbout),
        bottomNavScale = d.or("bottomNavScale", c.bottomNavScale).takeIf { it.isFinite() } ?: c.bottomNavScale,
        highRefreshRate = d.or("highRefreshRate", c.highRefreshRate), immersive = d.or("immersive", c.immersive), perfMode = d.or("perfMode", c.perfMode), pullToRefreshCharacter = d.or("pullToRefreshCharacter", c.pullToRefreshCharacter),
        hapticEnabled = d.or("hapticEnabled", c.hapticEnabled), glassEffect = d.or("glassEffect", c.glassEffect),
        quickTiles = d.or("quickTiles", c.quickTiles), settingsOrder = d.or("settingsOrder", c.settingsOrder),
        autoUpdateEnabled = d.or("autoUpdateEnabled", c.autoUpdateEnabled), wifiOnly = d.or("wifiOnly", c.wifiOnly), stableOnly = d.or("stableOnly", c.stableOnly), deleteApk = d.or("deleteApk", c.deleteApk),
        useShizuku = d.or("useShizuku", c.useShizuku), installerPreference = d.or("installerPreference", c.installerPreference),
        installerPackage = d.or("installerPackage", c.installerPackage), installerLabel = d.or("installerLabel", c.installerLabel),
        useRemoteJson = d.or("useRemoteJson", c.useRemoteJson), loadLocalData = d.or("loadLocalData", c.loadLocalData),
        appStreams = d.or("appStreams", c.appStreams), ignoredUpdates = d.or("ignoredUpdates", c.ignoredUpdates),
        appManage = d.or("appManage", c.appManage), skipOtherStores = d.or("skipOtherStores", c.skipOtherStores), myAppsFilters = d.or("myAppsFilters", c.myAppsFilters), userProfile = d.or("userProfile", c.userProfile),
        customBundles = d.or("customBundles", c.customBundles), removedBundleApps = d.or("removedBundleApps", c.removedBundleApps),
        extraBundleApps = d.or("extraBundleApps", c.extraBundleApps),
    )

/** First-run carry-over from the web build's zustand settings store, whose fields share Settings' names. A whole-object decode keeps
 *  everything (badges, tokens, tracking); if one field's type clashed, fall back to the identity fields alone. */
fun webSettingsImport(raw: String, c: Settings): Settings {
    val st = runCatching { json.parseToJsonElement(raw).jsonObject["state"]?.jsonObject }.getOrNull() ?: return c
    return runCatching { json.decodeFromJsonElement<Settings>(st) }.getOrElse { mergeIdentity(st, c) }
}
// endregion

// region catalog diagnostic, port of utils/catalogScanner.ts
data class MissionResult(val appId: String, val appName: String, val issues: List<String>)
private val headClient by lazy { Net.client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build() }
private suspend fun alive(url: String) = url.startsWith("http") && withContext(Dispatchers.IO) {
    runCatching { headClient.newCall(Request.Builder().url(url).head().cacheControl(CacheControl.FORCE_NETWORK).build()).execute().use { it.code in 200..399 } }.getOrDefault(false)
}
private suspend fun scanApp(a: AppItem): MissionResult? {
    val i = mutableListOf<String>()
    if (a.author.isBlank() || a.author.equals("unknown", true)) i += "Nome do desenvolvedor ausente"
    if (a.description.trim().length < 20) i += "Descrição ausente ou muito curta"
    if (a.screenshots.isEmpty()) i += "Nenhuma captura de tela fornecida"
    if (a.icon.isNotEmpty()) { if (!alive(a.icon)) i += "URL do ícone inacessível: ${a.icon}" } else i += "URL do ícone ausente"
    if (a.downloadUrl.isNotEmpty()) { if (!alive(a.downloadUrl)) i += "URL de download inacessível: ${a.downloadUrl}" } else i += "URL de download ausente"
    a.screenshots.firstOrNull()?.let { if (!alive(it)) i += "URL da captura de tela inacessível: $it" }
    return if (i.isEmpty()) null else MissionResult(a.id, a.name, i)
}
suspend fun runCatalogDiagnostic(apps: List<AppItem>, onProgress: (Int, Int) -> Unit): List<MissionResult> {
    val out = mutableListOf<MissionResult>(); var n = 0
    apps.chunked(10).forEach { b -> out += coroutineScope { b.map { async { scanApp(it) } }.awaitAll() }.filterNotNull(); n += b.size; onProgress(n, apps.size) }
    return out
}
// endregion

private suspend fun pickDocument(types: Array<String>): Uri? {
    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, types)
    return ResultHost.run(i).let { (code, data) -> data?.data?.takeIf { code == android.app.Activity.RESULT_OK } }
}

// region building blocks
/** Accent of the open settings page; rows and headers tint from it. */
private val LocalAccent = compositionLocalOf { Color(0xFF6366F1) }

private data class Cat(val id: String, val icon: ImageVector, val color: Color, val title: String, val desc: String, val shape: RoundedPolygon)
private val CATS = listOf(
    Cat("queue", Sym.Downloading, Blue, "Downloads", "Fila, instalações e atualizações pendentes", MaterialShapes.Cookie9Sided),
    Cat("visuals", Sym.Palette, Purple, "Visual e sensação", "Tema, fonte, movimento e densidade", MaterialShapes.Flower),
    Cat("network", Sym.Wifi, SkyC, "Rede", "Regras de Wi-Fi e atualização automática", MaterialShapes.Clover4Leaf),
    Cat("storage", Sym.CleaningServices, Orange, "Armazenamento", "Limpeza e APKs salvos", MaterialShapes.Sunny),
    Cat("installer", Sym.Shield, Green, "AlphaStore Xtra", "Guardião, Sentinela e instalador", MaterialShapes.Gem),
    Cat("identity", Sym.Backup, Pink, "Backup", "Salve ou restaure sua identidade", MaterialShapes.Cookie6Sided),
    Cat("interface", Sym.ViewQuilt, Teal, "Interface", "Layout da loja e abas visíveis", MaterialShapes.Pentagon),
    Cat("developer", Sym.Terminal, Yellow, "Desenvolvedor", "Opções de depuração e ferramentas", MaterialShapes.SoftBurst),
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(title, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = tonal(LocalAccent.current).second)
    Column(Modifier.clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
private fun SRow(icon: ImageVector, title: String, desc: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) =
    ListItem(
        { Text(title, style = MaterialTheme.typography.titleMedium) },
        Modifier.clip(RoundedCornerShape(4.dp)).then(if (onClick != null) Modifier.clickable(enabled, onClick = onClick) else Modifier),
        supportingContent = desc?.let { { Text(it) } }, leadingContent = { TonalIcon(icon, LocalAccent.current) }, trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )

@Composable
private fun Toggle(icon: ImageVector, title: String, desc: String, checked: Boolean, onChange: () -> Unit) {
    val vm = LocalVm.current
    SRow(icon, title, desc, onClick = { vm.haptic(H.SELECTION); onChange() }) {
        Switch(checked, { vm.haptic(H.SELECTION); onChange() },
            thumbContent = { Icon(if (checked) Sym.Check else Sym.Close, null, Modifier.size(SwitchDefaults.IconSize)) })
    }
}

@Composable
private fun Chevron(meta: String? = null) = Row(verticalAlignment = Alignment.CenterVertically) {
    meta?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = tonal(LocalAccent.current).second, fontWeight = FontWeight.Bold) }
    Icon(Sym.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InfoCard(text: String, title: String? = null, icon: ImageVector = Sym.Info, action: (@Composable () -> Unit)? = null) {
    val (bg, fg) = tonal(LocalAccent.current)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(bg).padding(16.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg) }
            Text(text, style = MaterialTheme.typography.bodySmall, color = fg.copy(.85f))
            action?.invoke()
        }
    }
}

@Composable
private fun Selectable(selected: Boolean, title: String, meta: String? = null, onClick: () -> Unit) {
    val r by animateDpAsState(if (selected) 28.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sel")
    Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(r),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            if (selected) Icon(Sym.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) else Icon(Sym.KeyboardArrowRight, null)
        }
    }
}

/** Decorative MaterialShape that slowly turns; used behind hub tiles and page heroes. */
@Composable
private fun Spinner(shape: RoundedPolygon, color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, periodMs: Int = 24_000) {
    val still = LocalS.current.calm
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "a")
    Box(modifier.size(size).graphicsLayer { rotationZ = turn }.background(color, shape.toShape()))
}

/** Quick Settings tile: rests as a rounded rectangle, turns into a filled pill when on. */
@Composable
private fun QuickTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, state: String = if (on) "Ligado" else "Desligado", onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val r by animateDpAsState(if (on) 36.dp else 20.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "r")
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHigh, fx, label = "bg")
    val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurface, fx, label = "fg")
    val bounce = remember { Animatable(1f) }; val sp = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(on) { bounce.snapTo(.6f); bounce.animateTo(1f, sp) }
    Row(modifier.height(76.dp).springClick(RoundedCornerShape(r), onClick = onClick).background(bg).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }, tint = fg)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state, style = MaterialTheme.typography.labelMedium, color = fg.copy(.75f), maxLines = 1)
        }
    }
}
// endregion

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = LocalS.current; val d = LocalD.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val menu = vm.settingsMenu
    var modal by remember { mutableStateOf("none") }
    var picker by remember { mutableStateOf<String?>(null) }
    var installerStep by remember { mutableStateOf("mode") }
    var shizukuError by remember { mutableStateOf<String?>(null) }
    var importStatus by remember { mutableStateOf("" to "neutral") }
    var fontError by remember { mutableStateOf<String?>(null) }; var importingFont by remember { mutableStateOf(false) }
    var installers by remember { mutableStateOf<List<Installer>>(emptyList()) }
    var installersLoading by remember { mutableStateOf(false) }; var installersError by remember { mutableStateOf<String?>(null) }
    val all = vm.allApps()
    val active = d.activeDownloads.size; val ready = d.readyToInstall.size
    val pending = vm.updates.count { it.id !in d.activeDownloads && it.id !in d.readyToInstall }
    fun back() {
        vm.settingsMenu = "none"; importStatus = "" to "neutral"; shizukuError = null; picker = null; installerStep = "mode"
    }
    BackHandler { if (vm.settingsMenu != "none") back() else vm.close(Overlay.SETTINGS) }
    LaunchedEffect(menu) {
        if (menu != "installer") return@LaunchedEffect
        installersLoading = true; installersError = null
        runCatching { withContext(Dispatchers.IO) { vm.tracker.installers() } }.onSuccess { installers = it }.onFailure { installersError = "Falha ao carregar instaladores." }
        installersLoading = false
    }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val saved = rememberSaveableStateHolder()
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(menu, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "menu") { m ->
            saved.SaveableStateProvider(m) {
            val avs = this@AnimatedContent
            val cat = CATS.firstOrNull { it.id == m }
            if (cat == null) Hub(vm, s, d, active, ready, pending) { c ->
                Modifier.sharedBounds(rememberSharedContentState("cat-${c.id}"), avs, resizeMode = PAGE_RESIZE)
            } else Page(cat, Modifier.sharedBounds(rememberSharedContentState("cat-${cat.id}"), avs, resizeMode = PAGE_RESIZE),
                onBack = ::back) {
                when (m) {
                    "identity" -> {
                        Section("Seus dados ficam aqui") {
                            InfoCard("Salve seu progresso em um arquivo, ou deixe o AlphaStore manter uma cópia na nuvem que você escolher. Nada vai para um servidor do AlphaStore.", "O AlphaStore é sem servidor", Sym.CloudOff)
                        }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val saving = remember { mutableStateOf(BackupPhase.IDLE) }
                            Button({
                                scope.backupRun(saving, vm) {
                                    runCatching { vm.tracker.saveFile(identityExport(vm.s, vm.d.favorites, System.currentTimeMillis()), "alphastore_identity_${System.currentTimeMillis()}.osf") }
                                        .onSuccess { importStatus = "Identidade salva com sucesso." to "success" }
                                        .onFailure { importStatus = "Falha ao exportar: ${it.message}" to "error" }.isSuccess
                                }
                            }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shapes = ButtonDefaults.shapes()) {
                                BackupGlyph(saving.value, 32.dp, MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp))
                                AnimatedContent(saving.value, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "save") { p ->
                                    Column {
                                        Text(when (p) { BackupPhase.WORKING -> "Salvando..."; BackupPhase.DONE -> "Salvo"; BackupPhase.FAILED -> "Falhou"; else -> "Fazer backup" }, fontWeight = FontWeight.Bold)
                                        Text(if (p == BackupPhase.DONE) "Confira seus arquivos" else "Arquivo .osf assinado", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            FilledTonalButton({
                                scope.launch {
                                    val uri = pickDocument(arrayOf("*/*")) ?: return@launch
                                    try {
                                        val raw = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                                        val (merged, fav) = identityImport(raw, vm.s)
                                        vm.set { merged }; fav?.let { f -> vm.upd { it.copy(favorites = f) } }
                                        importStatus = "Identidade restaurada com sucesso!" to "success"; vm.haptic(H.SUCCESS)
                                        delay(1500); vm.reload()
                                    } catch (e: IdentityError) { importStatus = (e.message ?: "") to "error"; vm.haptic(H.ERROR) }
                                    catch (_: Exception) { importStatus = "Arquivo de backup corrompido ou incompatível." to "error"; vm.haptic(H.ERROR) }
                                }
                            }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.FileDownload, null); Spacer(Modifier.width(8.dp)); Column { Text("Restaurar", fontWeight = FontWeight.Bold); Text("Importar um backup", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        AnimatedVisibility(importStatus.first.isNotEmpty()) {
                            val c = when (importStatus.second) { "success" -> Safe; "error" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (importStatus.second == "error") Sym.ErrorOutline else Sym.CheckCircle, null, tint = c); Spacer(Modifier.width(8.dp))
                                Text(importStatus.first, fontWeight = FontWeight.Bold, color = c)
                            }
                        }
                        CloudBackup(vm, s)
                    }
                    "network" -> Section("Downloads") {
                        Toggle(Sym.Wifi, "Apenas Wi-Fi", "Pular downloads em dados móveis", s.wifiOnly) { vm.set { it.copy(wifiOnly = !it.wifiOnly) } }
                        Toggle(Sym.Autorenew, "Atualizar apps automaticamente", "Apps instalados que o AlphaStore gerencia, incluindo apps do Orbit que não estão pausados ou só em acompanhamento. Pule um pelo menu da página dele.", s.autoUpdateEnabled) { vm.set { it.copy(autoUpdateEnabled = !it.autoUpdateEnabled) } }
                        Toggle(Sym.Wifi, "Atualizar automaticamente só no Wi-Fi", "Atualizações em segundo plano aguardam o Wi-Fi. Downloads que você iniciar ainda seguem a opção 'Apenas Wi-Fi' acima.", s.autoWifiOnly) { vm.set { it.copy(autoWifiOnly = !it.autoWifiOnly) } }
                        InfoCard("A atualização automática cobre apps em Meus Apps que o AlphaStore gerencia, e apps do Orbit que não estão pausados nem definidos como só acompanhar. Quando um app do Orbit também está na biblioteca, a cópia da biblioteca é a atualizada. Pule a atualização automática na página de um app, ou escolha Não gerenciar, para deixá-lo de fora. Uma atualização manual ainda funciona.")
                        var checkMenu by remember { mutableStateOf(false) }
                        val checks = listOf(0 to "Apenas com o AlphaStore aberto", 15 to "A cada 15 minutos", 30 to "A cada 30 minutos", 60 to "A cada hora", 180 to "A cada 3 horas", 360 to "A cada 6 horas", 720 to "A cada 12 horas", 1440 to "Todo dia", 10080 to "Toda semana")
                        SRow(Sym.Schedule, "Verificar atualizações", checks.firstOrNull { it.first == s.checkEveryMin }?.second ?: "A cada ${s.checkEveryMin} minutos", onClick = { checkMenu = true }) {
                            Box {
                                Icon(Sym.ExpandMore, null)
                                DropdownMenu(checkMenu, { checkMenu = false }, shape = RoundedCornerShape(20.dp)) {
                                    checks.forEach { (mins, label) -> DropdownMenuItem({ Text(label) }, { vm.set { it.copy(checkEveryMin = mins) }; checkMenu = false; vm.haptic(H.SELECTION) }) }
                                }
                            }
                        }
                        var ageMenu by remember { mutableStateOf(false) }
                        val ages = listOf(0 to "Desligado", 1 to "1 dia", 2 to "2 dias", 3 to "3 dias", 7 to "7 dias")
                        SRow(Sym.History, "Aguardar antes de oferecer um lançamento do Orbit", ages.firstOrNull { it.first == s.orbitMinAgeDays }?.second ?: "${s.orbitMinAgeDays} dias", onClick = { ageMenu = true }) {
                            Box {
                                Icon(Sym.ExpandMore, null)
                                DropdownMenu(ageMenu, { ageMenu = false }, shape = RoundedCornerShape(20.dp)) {
                                    ages.forEach { (days, label) -> DropdownMenuItem({ Text(label) }, { vm.set { it.copy(orbitMinAgeDays = days) }; ageMenu = false; vm.haptic(H.SELECTION) }) }
                                }
                            }
                        }
                        if (s.autoUpdateEnabled) {
                            Toggle(Sym.SystemUpdate, "Instalar automaticamente", "Instalar atualizações baixadas sobre o app atual. Qualquer falha aparece na próxima vez que você abrir o AlphaStore.", s.autoInstall) {
                                vm.set { it.copy(autoInstall = !it.autoInstall) }
                            }
                            if (s.autoInstall) InfoCard(when {
                                s.rootMode -> "O modo root está ligado: toda atualização é instalada silenciosamente."
                                s.useShizuku -> "Instalações silenciosas rodam pelo Shizuku, desde que ele esteja em execução."
                                android.os.Build.VERSION.SDK_INT >= 31 -> "Apps instalados pelo AlphaStore são atualizados silenciosamente. Apps de outras lojas precisam de um toque, ou adote-os com o modo root."
                                else -> "Esta versão do Android pede confirmação antes de cada instalação. Root ou Shizuku tornam as atualizações silenciosas."
                            })
                        }
                        Toggle(Sym.Verified, "Apenas versões estáveis", "Pular versões Beta, Alpha e Nightly quando houver uma estável", s.stableOnly) { vm.set { it.copy(stableOnly = !it.stableOnly) } }
                        Toggle(Sym.FilterList, "Pular apps de outras lojas", "Apps instalados pela Play Store ou outra loja saem de Meus Apps e não recebem atualizações aqui. Traga um de volta pela página dele.", s.skipOtherStores) {
                            vm.set { it.copy(skipOtherStores = !it.skipOtherStores) }
                        }
                    }
                    "installer" -> {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureTile(Sym.Shield, "Guardião", "Permissões, exportar APK, debloat", Green, MaterialShapes.Cookie9Sided, Modifier.weight(1f)) { modal = "guardian" }
                            FeatureTile(Sym.MonitorHeart, "Sentinela", "Verificação de malware nos apps instalados", Blue, MaterialShapes.Sunny, Modifier.weight(1f)) { modal = "sentinel" }
                        }
                        val ch = ptr(s.pullToRefreshCharacter)
                        Section("Puxar para atualizar") {
                            SRow(Sym.AutoFixHigh, "Personagem de atualização", ch.name, onClick = { picker = "ptr" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PtrBadge(ch, 48.dp)
                                    Chevron()
                                }
                            }
                        }
                        Section("Comportamento de instalação") {
                            SRow(Sym.Android, "Instalador de APK", when (s.installerPreference) {
                                "package" -> s.installerLabel.ifEmpty { "Instalador específico" }; "chooser" -> "Perguntar sempre"; else -> "Instalador do sistema"
                            }, onClick = { installerStep = "mode"; picker = "installer" }) { Chevron() }
                            // AlphaStore is also an installer: any APK from a file manager or browser opens on AlphaStore's sheet
                            SRow(Sym.InstallMobile, "Instalar um arquivo APK", "Escolha qualquer APK neste celular e instale com o AlphaStore", onClick = {
                                vm.haptic(H.LIGHT); vm.pickApk()
                            }) { Chevron() }
                            if (WirelessAdb.supported) Toggle(Sym.Speed, "Pular a espera de 24 horas", "Instale na hora pela depuração sem fio integrada", s.directInstall) {
                                if (vm.s.directInstall) vm.set { it.copy(directInstall = false) } else { picker = "direct" }
                            }
                            Toggle(Sym.Bolt, "Instalação silenciosa", "Instalar apps em segundo plano", s.useShizuku) {
                                shizukuError = null
                                if (!vm.s.useShizuku) scope.launch {
                                    try { vm.tracker.requestElevated(); vm.set { it.copy(useShizuku = !it.useShizuku) } }
                                    catch (e: Exception) { shizukuError = if ((e.message ?: "").contains("Shizuku is NOT running")) "O serviço do Shizuku não está em execução." else "A permissão foi negada pelo usuário." }
                                } else vm.set { it.copy(useShizuku = !it.useShizuku) }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= 26) Toggle(Sym.AdminPanelSettings, "Dhizuku", "Instalações e desinstalações silenciosas pelo proprietário do dispositivo do Dhizuku, sem Shizuku ou root", s.useDhizuku) {
                                shizukuError = null
                                if (vm.s.useDhizuku) vm.set { it.copy(useDhizuku = false) }
                                else scope.launch {
                                    runCatching { DhizukuBridge.request(ctx) }.onSuccess { ok -> if (ok) { vm.set { it.copy(useDhizuku = true) }; vm.haptic(H.SUCCESS) } else shizukuError = "A permissão do Dhizuku foi negada." }
                                        .onFailure { shizukuError = it.message ?: "O Dhizuku não está disponível." }
                                }
                            }
                            Toggle(Sym.Tune, "Além da instalação", "Escolha downgrade, permissões, instalação pela Play Store e mais antes de cada instalação. Usa Shizuku ou root.", s.installOptions) {
                                shizukuError = null
                                if (!vm.s.installOptions && !vm.s.rootMode) scope.launch {
                                    try { vm.tracker.requestElevated(); vm.set { it.copy(installOptions = true) } }
                                    catch (e: Exception) { shizukuError = if ((e.message ?: "").contains("Shizuku is NOT running")) "O serviço do Shizuku não está em execução." else "A permissão foi negada pelo usuário." }
                                } else vm.set { it.copy(installOptions = !it.installOptions) }
                            }
                            shizukuError?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold) }
                            InfoCard("Instalações silenciosas precisam do Shizuku ou Shizuku-Next em execução, ou do Dhizuku ativado como proprietário do dispositivo.") {
                                Row {
                                    TextButton({ vm.navigateFromSettings("shizuku") }, contentPadding = PaddingValues(0.dp)) { Text("Obter Shizuku") }
                                    Spacer(Modifier.width(16.dp))
                                    TextButton({ vm.tracker.open(DhizukuBridge.URL) }, contentPadding = PaddingValues(0.dp)) { Text("Obter Dhizuku") }
                                }
                            }
                        }
                        Section("Raiz") {
                            var adopt by remember { mutableStateOf<String?>(null) }
                            ConfirmDialog(adopt, "Adotar", onConfirm = vm::adoptApps, onDismiss = { adopt = null })
                            Toggle(Sym.Terminal, "Modo root", "Instalações silenciosas e todas as ferramentas do Guardian via su, sem Shizuku ou depuração sem fio", s.rootMode) {
                                if (vm.s.rootMode) vm.set { it.copy(rootMode = false) }
                                else scope.launch { if (vm.tracker.requestRoot()) { vm.set { it.copy(rootMode = true) }; vm.haptic(H.SUCCESS) } else vm.error("O acesso root foi negado ou este dispositivo não tem root.") }
                            }
                            if (s.rootMode) {
                                if (android.os.Build.VERSION.SDK_INT >= 34) Toggle(Sym.Lock, "Bloqueio de atualização", "Outras lojas devem pedir sua permissão antes de atualizar apps instalados pelo AlphaStore", s.updateLock) { vm.set { it.copy(updateLock = !it.updateLock) } }
                                SRow(Sym.Sync, "Adotar apps instalados", "Torne o AlphaStore o atualizador dos apps do catálogo que você obteve em outro lugar. Mantém os dados deles.", onClick = {
                                    adopt = "O AlphaStore reinstala o mesmo APK de cada app do catálogo que você instalou de outra fonte e se torna o instalador dele. Os dados do app permanecem. A outra loja deixa de atualizá-los."
                                }) { Chevron() }
                                SRow(Sym.DeleteSweep, "Limpar cache de todos os apps", "Libere espaço em todos os apps de uma vez", onClick = vm::trimCaches) { Chevron() }
                            }
                        }
                        Section("Proteção contra sideloading") {
                            val vs = vm.verifierState
                            SRow(if (vs == com.ramonmods.alphastore.platform.Verifier.State.ACTIVE) Sym.GppBad else Sym.GppGood, "Verificação de desenvolvedor", when (vs) {
                                com.ramonmods.alphastore.platform.Verifier.State.ACTIVE -> "Ativada. Toque para desativar com Proteger"
                                com.ramonmods.alphastore.platform.Verifier.State.OFF -> "Desativada, o sideloading está protegido. Toque para reverter"
                                else -> "Não disponível neste celular"
                            }, onClick = { vm.haptic(H.LIGHT); vm.openVerifier() }) { Chevron() }
                            Toggle(Sym.Shield, "Escudo de proteção", "Mostrar o escudo no cabeçalho. Ele abre Proteger e Reverter com um toque", s.verifierBadge) { vm.set { it.copy(verifierBadge = !it.verifierBadge) } }
                        }
                        Section("Recuperação") {
                            SRow(Sym.Sync, "Forçar nova verificação de pacotes", "Recarregar a lista de todos os apps instalados", onClick = { vm.reload(); vm.scheduleInstalledSync(); vm.close(Overlay.SETTINGS) })
                        }
                    }
                    "storage" -> {
                        StatHero("${d.pendingCleanup.size}", if (d.pendingCleanup.size == 1) "APK aguardando limpeza" else "APKs aguardando limpeza") {
                            if (d.pendingCleanup.isNotEmpty()) Button({ vm.batchCleanup(); vm.haptic(H.SUCCESS) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.CleaningServices, null); Spacer(Modifier.width(8.dp)); Text("Limpar agora")
                            }
                        }
                        Section("Limpeza") {
                            Toggle(Sym.CleaningServices, "Limpeza automática do instalador", "Remover APKs depois de instalados", s.deleteApk) { vm.set { it.copy(deleteApk = !it.deleteApk) } }
                            InfoCard("O modo de limpeza remove os arquivos APK instalados na próxima abertura do app.")
                        }
                    }
                    "visuals" -> {
                        ThemePicker(vm, s)
                        PalettePicker(vm, s)
                        Section("Cor e tipografia") {
                            Toggle(Sym.AutoAwesome, "Material You", "Cores dinâmicas do papel de parede (Android 12+)", s.materialYouEnabled) { vm.set { it.copy(materialYouEnabled = !it.materialYouEnabled) } }
                            SRow(Sym.FontDownload, "Fonte", fontLabel(s), onClick = { fontError = null; picker = "font" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("Aa", style = MaterialTheme.typography.headlineSmallEmphasized); Chevron() }
                            }
                        }
                        Text("Desempenho", Modifier.padding(start = 32.dp, top = 16.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = tonal(LocalAccent.current).second)
                        Text(if (s.perfMode == "auto") "O Auto escolhe ${s.perf.replaceFirstChar { it.uppercase() }} para este celular. Leve para movimentos decorativos, Turbo mantém mais imagens prontas."
                            else "Leve para movimentos decorativos, Turbo mantém mais imagens prontas. Mudanças nas imagens valem após reiniciar.", Modifier.padding(horizontal = 32.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // One connected group across the page, the same 16dp margins as the groups below: no scrolling, nothing off to one side
                        val modes = listOf("auto" to "Automático", "lite" to "Leve", "balanced" to "Equilibrado", "boost" to "Turbo")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            modes.forEachIndexed { i, (id, label) ->
                                ToggleButton(s.perfMode == id, { vm.haptic(H.SELECTION); vm.set { it.copy(perfMode = id) } }, Modifier.weight(if (id == "balanced") 1.3f else 1f),
                                    shapes = when (i) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() },
                                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
                            }
                        }
                        Section("Movimento e toque") {
                            Toggle(Sym.TouchApp, "Retorno tátil", "Vibração ao tocar em botões e opções", s.hapticEnabled) { vm.set { it.copy(hapticEnabled = !it.hapticEnabled) } }
                            Toggle(Sym.Speed, "Movimento suave", "Preferir rolagem com alta taxa de atualização", s.highRefreshRate) { vm.set { it.copy(highRefreshRate = !it.highRefreshRate) } }
                            Toggle(Sym.MotionPhotosOff, "Desativar animações", "Todas as transições ficam instantâneas", s.disableAnimations) { vm.set { it.copy(disableAnimations = !it.disableAnimations) } }
                            Toggle(Sym.BlurOn, "Dock de vidro", "Navegação flutuante levemente translúcida", s.glassEffect) { vm.set { it.copy(glassEffect = !it.glassEffect) } }
                            Toggle(Sym.OpenInFull, "Tela cheia", "Ocultar a barra de status enquanto o AlphaStore estiver aberto. Deslize de cima para espiar", s.immersive) { vm.set { it.copy(immersive = !it.immersive) } }
                            Toggle(Sym.Compress, "Modo compacto", "Linhas menores com espaçamento mais denso", s.compactMode) { vm.set { it.copy(compactMode = !it.compactMode) } }
                        }
                        if (!Device.isTv) IconPicker(vm, s)
                        DockPicker(vm, s)
                        DockSize(vm, s)
                        DockPosition(vm, s)
                    }
                    "interface" -> {
                        LayoutPicker(vm, s)
                        Section("Abas de navegação") {
                            listOf(Triple("android", "Apps", "Apps Android"), Triple("pc", "PC", "Softwares para PC e desktop"),
                                Triple("tv", "TV", "Apps para TV e telas grandes"), Triple("orbit", "Orbit", "Apps que você acompanha direto da fonte"), Triple("myapps", "Meus Apps", "Biblioteca e histórico de downloads")).forEach { (t, title, desc) ->
                                Toggle(when (t) { "android" -> Sym.Android; "pc" -> Sym.DesktopWindows; "tv" -> Sym.Tv; "orbit" -> Sym.Orbit; else -> Sym.GridView }, title, desc, t !in s.hiddenTabs) {
                                    vm.set { it.toggleHiddenTab(t) }
                                }
                            }
                        }
                        Section("AlphaStore AI") {
                            Toggle(Sym.AutoAwesome, "Estrela de IA", "Uma pequena estrela que você pode arrastar para qualquer lugar; toque para abrir a AlphaStore AI", s.aiButton) { vm.set { it.copy(aiButton = !it.aiButton) } }
                            Toggle(Sym.Notifications, "Efeitos sonoros da IA", "Sons leves quando você conversa com a AlphaStore AI", s.aiSounds) { vm.set { it.copy(aiSounds = !it.aiSounds) } }
                            SRow(Sym.Key, "Provedor e chave", AlphaStoreAi.provider(s.aiProvider).label + if (vm.ai.ready(s)) " · conectado" else " · não conectado",
                                onClick = { modal = "ai" }) { Chevron() }
                            SRow(Sym.Chat, "Abrir AlphaStore AI", "Encontre apps descrevendo o que você precisa", onClick = { vm.close(Overlay.SETTINGS); vm.open(Overlay.AI) }) { Chevron() }
                        }
                    }
                    "developer" -> {
                        Section("Testes e depuração") {
                            InfoCard("Estas opções são para testes e para modificar o comportamento central do app. Podem causar instabilidade.", icon = Sym.Warning)
                            Toggle(Sym.Storage, "Ativar dados integrados", "Carregar dados empacotados em vez de fontes remotas", s.loadLocalData) { vm.set { it.copy(loadLocalData = !it.loadLocalData) } }
                            Toggle(Sym.Construction, "Modo de manutenção local", "Trabalhar em pacotes e dados localmente", s.localMaintenanceMode) {
                                vm.set { it.copy(localMaintenanceMode = !it.localMaintenanceMode) }
                            }
                            SRow(Sym.Shield, "Testar proteção contra sideloading", "Exibe o popup de Proteger com um sucesso simulado. Nada muda no celular", onClick = { vm.haptic(H.LIGHT); vm.testVerifier("done") }) { Chevron() }
                            SRow(Sym.GppMaybe, "Testar falha da proteção contra sideloading", "O mesmo popup terminando em erro simulado, para verificar o Tentar de novo", onClick = { vm.haptic(H.LIGHT); vm.testVerifier("failed") }) { Chevron() }
                            SRow(Sym.Radar, "Executar verificador do catálogo (Admin)", "Verifica todos os apps em busca de texto ausente e links quebrados", onClick = { modal = "scanner" }) { Chevron() }
                            SRow(Sym.DeleteSweep, "Limpar cache do sistema", "Redefinir limpezas pendentes e instalações prontas", onClick = {
                                vm.upd { it.copy(pendingCleanup = emptyMap(), readyToInstall = emptyMap()) }; vm.haptic(H.WARNING)
                            })
                        }
                        DevConfigEditor(vm, s)
                        DevTools(vm)
                    }
                }
            }
            }
        }
    }
    when (picker) {
        "ptr" -> PtrPickerSheet(s.pullToRefreshCharacter, { c -> vm.set { it.withPtrCharacter(c) }; picker = null }) { picker = null }
        "font" -> FontSheet(vm, s, fontError, importingFont, onImport = {
            scope.launch {
                fontError = null
                val uri = pickDocument(arrayOf("font/ttf", "font/otf", "font/woff2", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) ?: return@launch
                val (name, size) = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) cur.getString(cur.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) to cur.getLong(cur.getColumnIndexOrThrow(OpenableColumns.SIZE)) else null
                } ?: ("" to 0L)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in setOf("ttf", "otf", "woff2")) { fontError = "Escolha um arquivo TTF, OTF ou WOFF2."; return@launch }
                if (size > 8 * 1024 * 1024) { fontError = "As fontes devem ter 8 MB ou menos."; return@launch }
                importingFont = true
                val id = "font-${System.currentTimeMillis()}-${(1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val f = File(File(ctx.filesDir, "fonts/custom").apply { mkdirs() }, "$id.$ext")
                        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }; f
                    }
                }.onSuccess { f ->
                    val font = CustomFont(id, name.substringBeforeLast('.').trim().ifEmpty { "Fonte personalizada" }, name, ctx.contentResolver.getType(uri) ?: "font/$ext", f.absolutePath, System.currentTimeMillis())
                    vm.set { it.copy(customFonts = it.customFonts + font, appFont = "custom:$id") }
                }.onFailure { fontError = "Não foi possível ler esta fonte." }
                importingFont = false
            }
        }) { picker = null }
        "installer" -> InstallerSheet(vm, s, installerStep, { installerStep = it }, installers, installersLoading, installersError) { picker = null; installerStep = "mode" }
        "direct" -> DirectInstallSheet(vm) { picker = null }
    }
    when (modal) {
        "guardian" -> GuardianSheet(vm) { modal = "none" }
        "ai" -> AiSettingsSheet(vm) { modal = "none" }
        "sentinel" -> SentinelSheet(vm) { modal = "none" }
        "scanner" -> ScannerSheet(all) { modal = "none" }
    }
}

/** Pages are measured once and scaled through the container transform; remeasuring Look & feel every frame was the jank. */
private val PAGE_RESIZE = SharedTransitionScope.ResizeMode.scaleToBounds(androidx.compose.ui.layout.ContentScale.FillWidth, Alignment.TopCenter)

/** One closing tip per page, so short pages end on something useful instead of empty surface. */
private val TIPS = mapOf(
    "queue" to "Os downloads continuam em segundo plano, com progresso ao vivo nas suas notificações.",
    "visuals" to "O botão de tema na barra de busca alterna entre Sistema, Claro, Crepúsculo, Escuro e OLED de qualquer lugar.",
    "network" to "A atualização automática enfileira novas versões sempre que o catálogo é atualizado. Apenas Wi-Fi bloqueia downloads em dados móveis.",
    "storage" to "A limpeza remove APKs sobrando uma semana após a instalação, ou na próxima abertura com Excluir APK ligado.",
    "installer" to "Com o Shizuku, instalações e atualizações passam sem um aviso de confirmação a cada vez.",
    "identity" to "Seu backup é um único arquivo .osf pequeno. Guarde uma cópia na sua nuvem e restaure em qualquer celular.",
    "interface" to "Abas ocultas voltam por aqui a qualquer momento. A aba Dev sempre fica.",
    "developer" to "Feche as opções de desenvolvedor no fim desta página quando terminar.",
)

@Composable
private fun PageFooter(cat: Cat) {
    val (_, fg) = tonal(cat.color)
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Spinner(cat.shape, fg.copy(.12f), 112.dp, periodMs = 30_000)
            Icon(cat.icon, null, Modifier.size(40.dp), tint = fg.copy(.7f))
        }
        Text(TIPS[cat.id] ?: cat.desc, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Settings home: quick toggles on top, then a bento of categories that expand into their pages. No staggered rise here: it fought the slide-in and read as jitter. */
@Composable
private fun Hub(vm: MainViewModel, s: Settings, d: DataState, active: Int, ready: Int, pending: Int, shared: @Composable (Cat) -> Modifier) {
    val cs = MaterialTheme.colorScheme
    fun cat(id: String) = CATS.first { it.id == id }
    Column(Modifier.fillMaxSize().background(cs.surface).readableWidth().verticalScroll(rememberScrollState()).readableWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(bottom = 32.dp)) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Configurações", style = MaterialTheme.typography.displaySmallEmphasized)
                Text("AlphaStore v$STORE_VERSION · ${vm.allApps().size} apps no catálogo", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            // Announcements already read leave the header; the latest one stays readable here until a new one arrives
            val ann = vm.config?.announcement?.takeIf { it.isNotBlank() }
            var showAnn by remember { mutableStateOf(false) }
            if (ann != null) IconButton({ vm.haptic(H.LIGHT); showAnn = true }) { Icon(Sym.Campaign, "Último aviso", tint = cs.onSurfaceVariant) }
            if (showAnn && ann != null) AlertDialog({ showAnn = false }, icon = { Icon(Sym.Campaign, null) }, title = { Text("Da equipe AlphaStore") },
                text = { Text(ann, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton({ showAnn = false; vm.dismissAnnouncement() }) { Text("Entendi") } })
        }
        var editing by remember { mutableStateOf(false) }
        HubHeader("Atalhos rápidos") { IconButton({ editing = true }) { Icon(Sym.Edit, "Editar atalhos rápidos", tint = cs.primary) } }
        val quick = (s.quickTiles ?: DEFAULT_QUICK).mapNotNull { id -> QUICK.firstOrNull { it.id == id && it.shown(s) } }
        if (quick.isEmpty()) Text("Nenhum atalho rápido. Toque no lápis para adicionar seus favoritos.", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        // Three across on big screens, so the tiles keep a tile's shape instead of stretching into long bars
        val per = if (LocalWidthClass.current.wide) 3 else 2
        quick.chunked(per).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { q -> QuickTile(q.icon, q.label, q.get(s), Modifier.weight(1f)) { vm.haptic(H.SELECTION); vm.set(q.flip) } }
                repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (editing) QuickTilesSheet(vm, s) { editing = false }

        // Long-press a card and drag: cards trade places live as you pass over them, the displaced one gliding to its new spot.
        // Slots keep their sizes, so the bento never breaks. The gesture belongs to the slot where it started (keyed by slot index),
        // so trading places mid-drag never cancels it.
        fun orderOf(st: Settings) = (st.settingsOrder + HUB_ORDER).distinct().filter { it in HUB_ORDER }
        var live by remember { mutableStateOf<List<String>?>(null) }
        val order = live ?: orderOf(s)
        var dragging by remember { mutableStateOf<String?>(null) }; var pos by remember { mutableStateOf(Offset.Zero) }
        val slots = remember { mutableMapOf<Int, Rect>() }
        val glide = remember { mutableStateMapOf<String, Animatable<Offset, AnimationVector2D>>() }
        val motion = rememberCoroutineScope(); val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()
        fun center(i: Int) = slots[i]?.center ?: Offset.Zero
        fun slide(id: String, from: Offset) = motion.launch { Animatable(from, Offset.VectorConverter).also { glide[id] = it }.animateTo(Offset.Zero, spring) }
        fun start(slot: Int) { val o = live ?: orderOf(vm.s); live = o; dragging = o[slot]; pos = center(slot); vm.haptic(H.HEAVY) }
        fun move(delta: Offset) {
            val id = dragging ?: return; val o = live ?: return; pos += delta
            val cur = o.indexOf(id); val to = slots.entries.firstOrNull { it.key != cur && it.value.contains(pos) }?.key ?: return
            val other = o[to]
            live = o.toMutableList().also { it[cur] = other; it[to] = id }
            slide(other, center(to) - center(cur)); vm.haptic(H.SELECTION)
        }
        fun end() {
            val id = dragging; val o = live
            if (id != null && o != null) {
                slide(id, pos - center(o.indexOf(id)))
                if (o != orderOf(vm.s)) { vm.set { it.copy(settingsOrder = o) }; vm.haptic(H.SUCCESS) }
            }
            dragging = null
        }
        // The saved order lands a frame after the drop: keep showing the live one until it does, so nothing flickers back
        LaunchedEffect(s.settingsOrder) { if (dragging == null) live = null }
        fun Modifier.movable(slot: Int, id: String) = onGloballyPositioned { slots[slot] = it.boundsInRoot() }
            .zIndex(if (dragging == id) 1f else 0f)
            .graphicsLayer {
                val t = if (dragging == id) pos - center(slot) else glide[id]?.value ?: Offset.Zero
                translationX = t.x; translationY = t.y; val k = if (dragging == id) 1.05f else 1f; scaleX = k; scaleY = k
            }
            .pointerInput(slot) { detectDragGesturesAfterLongPress({ start(slot) }, ::end, ::end) { c, delta -> c.consume(); move(delta) } }
        fun z(vararg ids: Int) = if (ids.any { order[it] == dragging || glide[order[it]]?.isRunning == true }) 1f else 0f
        val queued = active + ready + pending
        @Composable fun Slot(i: Int, modifier: Modifier, compact: Boolean = false) {
            val c = cat(order[i])
            val badge = when (c.id) { "queue" -> queued.takeIf { it > 0 }; "storage" -> d.pendingCleanup.size.takeIf { it > 0 }; else -> null }?.toString()
            Bento(vm, c, shared, modifier.movable(i, c.id), compact, badge) {
                if (!compact) when (c.id) {
                    "queue" -> {
                        Text(if (queued == 0) "Tudo em dia" else "$active ativo · $ready pronto · $pending atualizações", style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(.8f))
                        val prog = d.activeDownloads.keys.mapNotNull { d.downloadProgress[it] }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                        if (prog != null) LinearWavyProgressIndicator({ prog / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp), color = LocalContentColor.current)
                    }
                    "visuals" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            listOf(cs.primary, cs.secondary, cs.tertiary).forEach { Box(Modifier.size(28.dp).clip(CircleShape).background(cs.surface).padding(2.dp).clip(CircleShape).background(it)) }
                        }
                        Text(fontLabel(s) + " · " + s.theme.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(.8f), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        HubHeader("Todo o resto", "Segure um cartão para movê-lo") {
            if (s.settingsOrder.isNotEmpty()) TextButton({ vm.set { it.copy(settingsOrder = emptyList()) }; vm.haptic(H.MEDIUM) }) { Text("Redefinir") }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Slot(0, Modifier.zIndex(z(0)).fillMaxWidth().height(156.dp))
            Row(Modifier.zIndex(z(1, 2, 3)).height(268.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot(1, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.zIndex(z(2, 3)).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slot(2, Modifier.fillMaxWidth().weight(1f), compact = true)
                    Slot(3, Modifier.fillMaxWidth().weight(1f), compact = true)
                }
            }
            Slot(4, Modifier.zIndex(z(4)).fillMaxWidth().height(140.dp))
            Row(Modifier.zIndex(z(5, 6)).height(132.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot(5, Modifier.weight(1f).fillMaxHeight(), compact = true)
                Slot(6, Modifier.weight(1f).fillMaxHeight(), compact = true)
            }
            if (s.isDevUnlocked) Bento(vm, cat("developer"), shared, Modifier.fillMaxWidth().height(108.dp))
        }
    }
}

/** Hub section header with an optional trailing action. */
@Composable
private fun HubHeader(title: String, hint: String? = null, action: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 2.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action()
    }

/** Bento slots, top to bottom, in their default order. Developer stays pinned last. */
private val HUB_ORDER = listOf("queue", "visuals", "network", "storage", "installer", "identity", "interface")

/** Every setting a quick tile can flip. Ones that need a grant first (Shizuku, root, wireless pairing) stay on their pages. */
private class Quick(val id: String, val icon: ImageVector, val label: String, val get: (Settings) -> Boolean, val shown: (Settings) -> Boolean = { true }, val flip: (Settings) -> Settings)
private val QUICK = listOf(
    Quick("autoUpdate", Sym.Autorenew, "Atualização automática", { it.autoUpdateEnabled }) { it.copy(autoUpdateEnabled = !it.autoUpdateEnabled) },
    Quick("wifiOnly", Sym.Wifi, "Apenas Wi-Fi", { it.wifiOnly }) { it.copy(wifiOnly = !it.wifiOnly) },
    Quick("haptics", Sym.Vibration, "Vibração", { it.hapticEnabled }) { it.copy(hapticEnabled = !it.hapticEnabled) },
    Quick("materialYou", Sym.Wallpaper, "Material You", { it.materialYouEnabled }, { android.os.Build.VERSION.SDK_INT >= 31 }) { it.copy(materialYouEnabled = !it.materialYouEnabled) },
    Quick("smooth", Sym.Speed, "Movimento suave", { it.highRefreshRate }) { it.copy(highRefreshRate = !it.highRefreshRate) },
    Quick("compact", Sym.Compress, "Compacto", { it.compactMode }) { it.copy(compactMode = !it.compactMode) },
    Quick("stableOnly", Sym.Verified, "Só estáveis", { it.stableOnly }) { it.copy(stableOnly = !it.stableOnly) },
    Quick("cleanup", Sym.CleaningServices, "Limpeza automática", { it.deleteApk }) { it.copy(deleteApk = !it.deleteApk) },
    Quick("noMotion", Sym.MotionPhotosOff, "Sem animações", { it.disableAnimations }) { it.copy(disableAnimations = !it.disableAnimations) },
    Quick("glass", Sym.BlurOn, "Dock de vidro", { it.glassEffect }) { it.copy(glassEffect = !it.glassEffect) },
    Quick("updateLock", Sym.Lock, "Bloqueio de atualização", { it.updateLock }, { it.rootMode && android.os.Build.VERSION.SDK_INT >= 34 }) { it.copy(updateLock = !it.updateLock) },
    Quick("localData", Sym.Storage, "Dados integrados", { it.loadLocalData }, { it.isDevUnlocked }) { it.copy(loadLocalData = !it.loadLocalData) },
    Quick("maintenance", Sym.Construction, "Manutenção", { it.localMaintenanceMode }, { it.isDevUnlocked }) { it.copy(localMaintenanceMode = !it.localMaintenanceMode) },
)
private val DEFAULT_QUICK = listOf("autoUpdate", "wifiOnly", "haptics", "materialYou", "smooth", "compact")

/** Your tiles on top: hold and drag to reorder (they trade places live), tap to remove. Everything else below: tap to add. */
@Composable
private fun QuickTilesSheet(vm: MainViewModel, s: Settings, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val cs = MaterialTheme.colorScheme
    fun pickedOf(st: Settings) = (st.quickTiles ?: DEFAULT_QUICK).filter { id -> QUICK.any { it.id == id && it.shown(st) } }
    val picked = pickedOf(s)
    var live by remember { mutableStateOf<List<String>?>(null) }; var dragging by remember { mutableStateOf<String?>(null) }
    var dy by remember { mutableFloatStateOf(0f) }; var step by remember { mutableFloatStateOf(1f) }
    val glide = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val motion = rememberCoroutineScope(); val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(); val gap = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.toPx() }
    fun slide(id: String, from: Float) = motion.launch { Animatable(from).also { glide[id] = it }.animateTo(0f, spring) }
    fun move(d: Float) {
        val id = dragging ?: return; val o = live ?: return; dy += d
        val i = o.indexOf(id); val j = when { dy > step / 2 && i < o.lastIndex -> i + 1; dy < -step / 2 && i > 0 -> i - 1; else -> return }
        val other = o[j]
        live = o.toMutableList().also { it[i] = other; it[j] = id }; dy -= (j - i) * step; slide(other, (j - i) * step); vm.haptic(H.SELECTION)
    }
    fun end() {
        dragging?.let { slide(it, dy) }; dragging = null; dy = 0f
        live?.takeIf { it != pickedOf(vm.s) }?.let { o -> vm.set { it.copy(quickTiles = o) }; vm.haptic(H.SUCCESS) }
    }
    LaunchedEffect(picked) { if (dragging == null) live = null }
    val tiles = live ?: picked
    fun toggle(id: String) { vm.haptic(H.SELECTION); vm.set { st -> pickedOf(st).let { cur -> st.copy(quickTiles = if (id in cur) cur - id else cur + id) } } }
    SheetTitle("Atalhos rápidos", Sym.Edit, "Segure e arraste para reordenar. Toque para adicionar ou remover.")
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (tiles.isNotEmpty()) Text("Seus atalhos", Modifier.padding(start = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = cs.primary)
        tiles.forEachIndexed { i, id -> key(id) {
            val q = QUICK.first { it.id == id }
            SegmentedListItem({ toggle(id) }, ListItemDefaults.segmentedShapes(i, tiles.size),
                modifier = Modifier.onSizeChanged { step = it.height + gap }.zIndex(if (dragging == id) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging == id) dy else glide[id]?.value ?: 0f; val k = if (dragging == id) 1.03f else 1f; scaleX = k; scaleY = k }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress({ live = live ?: pickedOf(vm.s); dragging = id; dy = 0f; vm.haptic(H.HEAVY) }, ::end, ::end) { c, d -> c.consume(); move(d.y) }
                    },
                colors = ListItemDefaults.segmentedColors(containerColor = if (dragging == id) cs.primaryContainer else cs.secondaryContainer),
                leadingContent = { Icon(q.icon, null) }, trailingContent = { Icon(Sym.DragHandle, "Segure para mover") }) {
                Text(q.label, fontWeight = FontWeight.SemiBold)
            }
        } }
        val rest = QUICK.filter { it.shown(s) && it.id !in tiles }
        if (rest.isNotEmpty()) Text("Adicionar atalhos", Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = cs.primary)
        rest.forEachIndexed { i, q ->
            SegmentedListItem({ toggle(q.id) }, ListItemDefaults.segmentedShapes(i, rest.size), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh),
                leadingContent = { Icon(q.icon, null) }, trailingContent = { Icon(Sym.Add, "Adicionar") }) {
                Text(q.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ vm.set { it.copy(quickTiles = null) }; vm.haptic(H.MEDIUM) }, modifier = Modifier.weight(1f), enabled = s.quickTiles != null, shapes = ButtonDefaults.shapes()) {
            Icon(Sym.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Redefinir")
        }
        Button(LocalSheetClose.current, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Icon(Sym.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Concluir") }
    }
}

@Composable
private fun Bento(vm: MainViewModel, cat: Cat, shared: @Composable (Cat) -> Modifier, modifier: Modifier, compact: Boolean = false, badge: String? = null, extra: @Composable ColumnScope.() -> Unit = {}) {
    val (bg, fg) = tonal(cat.color)
    Box(modifier.then(shared(cat)).springClick(RoundedCornerShape(28.dp)) { vm.haptic(H.LIGHT); if (cat.id == "queue") vm.open(Overlay.DOWNLOADS) else vm.settingsMenu = cat.id }.background(bg)) {
        // Short tiles (the full-width Developer strip) get the small shape, so it hugs the corner like the others instead of filling the strip
        Spinner(cat.shape, fg.copy(.1f), if (compact || cat.id == "developer") 120.dp else 168.dp, Modifier.align(Alignment.BottomEnd).offset(36.dp, 36.dp))
        CompositionLocalProvider(LocalContentColor provides fg) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(fg.copy(.14f), cat.shape.toShape()), contentAlignment = Alignment.Center) { Icon(cat.icon, null, Modifier.size(24.dp)) }
                    Spacer(Modifier.weight(1f))
                    badge?.let { Badge(containerColor = fg, contentColor = bg) { Text(it, Modifier.padding(horizontal = 4.dp)) } }
                }
                Spacer(Modifier.weight(1f))
                Text(cat.title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
                if (!compact) Text(cat.desc, style = MaterialTheme.typography.bodySmall, color = fg.copy(.8f), maxLines = 2)
                extra()
            }
        }
    }
}

/** Category page: expands out of its hub tile, opens on a tinted hero with a turning shape. */
@Composable
private fun Page(cat: Cat, modifier: Modifier, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme; val (bg, fg) = tonal(cat.color)
    CompositionLocalProvider(LocalAccent provides cat.color) {
        val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        // At least a screen tall, so the footer settles at the bottom of short pages (weight works once the scroll column has a min height).
        // The tinted hero runs edge to edge on every screen; only the text and rows keep to a readable column
        Column(modifier.fillMaxSize().background(cs.surface).verticalScroll(rememberScrollState()).heightIn(min = screen).padding(bottom = 40.dp)) {
            Box(Modifier.fillMaxWidth().clipToBounds().background(Brush.verticalGradient(listOf(bg, cs.surface)))) {
                Spinner(cat.shape, fg.copy(.08f), 260.dp, Modifier.align(Alignment.TopEnd).offset(70.dp, (-30).dp), periodMs = 40_000)
                Column(Modifier.readableWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(bottom = 12.dp)) {
                    Box(Modifier.padding(start = 24.dp, top = 24.dp).size(72.dp), contentAlignment = Alignment.Center) {
                        Spinner(cat.shape, fg.copy(.16f), 72.dp, periodMs = 18_000)
                        Icon(cat.icon, null, Modifier.size(34.dp), tint = fg)
                    }
                    Text(cat.title, Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(cat.desc, Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
            Column(Modifier.readableWidth().reveal(1)) { content() }
            Spacer(Modifier.weight(1f))
            Box(Modifier.readableWidth()) { PageFooter(cat) }
        }
    }
}

/** Auto backup through Android's storage picker: Drive, OneDrive, Proton Drive or any app with a document provider, with no account linking inside AlphaStore. */
@Composable
private fun CloudBackup(vm: MainViewModel, s: Settings) = Section("Backup na nuvem") {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val pm = ctx.packageManager
    val uri = s.cloudBackupUri.takeIf { it.isNotEmpty() }?.toUri()
    val where = remember(uri) { uri?.authority?.let { a -> runCatching { pm.resolveContentProvider(a, 0)?.loadLabel(pm)?.toString() }.getOrNull() ?: a } }
    val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    fun connect() = scope.launch {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "alphastore_identity.osf")
        val u = ResultHost.run(i).let { (code, data) -> data?.data?.takeIf { code == android.app.Activity.RESULT_OK } } ?: return@launch
        if (runCatching { ctx.contentResolver.takePersistableUriPermission(u, rw) }.isFailure) { vm.error("Esse app de armazenamento não permite backups automáticos. Escolha outro."); return@launch }
        vm.set { it.copy(cloudBackupUri = u.toString(), cloudBackupEvery = it.cloudBackupEvery.takeIf { e -> e != "off" } ?: "week") }; vm.cloudBackup(true)
    }
    SRow(Sym.Cloud, if (uri == null) "Conectar uma nuvem" else "Fazendo backup em $where", if (uri == null) "Google Drive, OneDrive, Proton Drive ou qualquer app de armazenamento" else "Toque para escolher outro arquivo", onClick = ::connect) { Chevron() }
    if (uri != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("off" to "Desligado", "day" to "Diário", "week" to "Semanal", "month" to "Mensal").forEach { (id, label) -> Choice(s.cloudBackupEvery == id, label) { vm.set { it.copy(cloudBackupEvery = id) } } }
        }
        BackupNowRow(vm, s, where)
        SRow(Sym.CloudOff, "Desconectar", "Parar de fazer backup. O arquivo na sua nuvem continua lá.", onClick = {
            runCatching { ctx.contentResolver.releasePersistableUriPermission(uri, rw) }; vm.set { it.copy(cloudBackupUri = "", cloudBackupEvery = "off") }
        })
    }
}

private enum class BackupPhase { IDLE, WORKING, DONE, FAILED }

/** Runs [work] as a visible backup: working for at least [minMs] so the motion reads, then done or failed for a beat, then back to idle. */
private fun kotlinx.coroutines.CoroutineScope.backupRun(phase: MutableState<BackupPhase>, vm: MainViewModel, minMs: Long = 900, work: suspend () -> Boolean) {
    if (phase.value != BackupPhase.IDLE) return
    launch {
        val started = System.currentTimeMillis(); phase.value = BackupPhase.WORKING; vm.haptic(H.LIGHT)
        val ok = runCatching { work() }.getOrDefault(false)
        delay((minMs - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        phase.value = if (ok) BackupPhase.DONE else BackupPhase.FAILED; vm.haptic(if (ok) H.SUCCESS else H.ERROR)
        delay(2400); phase.value = BackupPhase.IDLE
    }
}

/** Backup glyph: the arrow waits inside a wavy ring while working, then lands as a check on a turning cookie, or a cross on failure. */
@Composable
private fun BackupGlyph(phase: BackupPhase, size: androidx.compose.ui.unit.Dp = 40.dp, tint: Color = LocalAccent.current) {
    val cs = MaterialTheme.colorScheme
    val (bg0, fg0) = tonal(when (phase) { BackupPhase.DONE -> Safe; BackupPhase.FAILED -> cs.error; else -> tint })
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(bg0, fx, label = "bg"); val fg by animateColorAsState(fg0, fx, label = "fg")
    val done by animateFloatAsState(if (phase == BackupPhase.DONE) 1f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "done")
    val morph = remember { androidx.graphics.shapes.Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = done * 90f }.clip(MorphShape(morph, done)).background(bg))
        if (phase == BackupPhase.WORKING) CircularWavyProgressIndicator(Modifier.matchParentSize(), color = fg, trackColor = Color.Transparent)
        val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedContent(phase, transitionSpec = {
            (scaleIn(pop, .3f) + fadeIn()) togetherWith (scaleOut(targetScale = .3f) + fadeOut())
        }, label = "glyph") { p ->
            val icon = when (p) { BackupPhase.DONE -> Sym.Check; BackupPhase.FAILED -> Sym.Close; else -> Sym.FileUpload }
            Icon(icon, null, Modifier.size(size * .5f), tint = fg)
        }
    }
}

/** Cloud "Back up now" with feedback: the glyph runs, the caption follows each step, and the row warms green when the copy lands. */
@Composable
private fun BackupNowRow(vm: MainViewModel, s: Settings, where: String?) {
    val scope = rememberCoroutineScope(); val cs = MaterialTheme.colorScheme
    val phase = remember { mutableStateOf(BackupPhase.IDLE) }; val p = phase.value
    val last = if (s.lastCloudBackup == 0L) "Nunca feito backup" else "Último backup " + android.text.format.DateUtils.getRelativeTimeSpanString(s.lastCloudBackup)
    val caption = when (p) { BackupPhase.WORKING -> "Enviando sua identidade para ${where ?: "sua nuvem"}..."; BackupPhase.DONE -> "Backup feito agora mesmo"; BackupPhase.FAILED -> "Falha no backup, toque para tentar de novo"; else -> last }
    val container by animateColorAsState(if (p == BackupPhase.DONE) androidx.compose.ui.graphics.lerp(cs.surfaceContainer, tonal(Safe).first, .6f) else cs.surfaceContainer,
        MaterialTheme.motionScheme.slowEffectsSpec(), label = "row")
    ListItem(
        { Text("Fazer backup agora", style = MaterialTheme.typography.titleMedium) },
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(p == BackupPhase.IDLE) {
            scope.backupRun(phase, vm) { val before = System.currentTimeMillis(); vm.cloudBackup(true).join(); vm.s.lastCloudBackup >= before }
        },
        supportingContent = {
            AnimatedContent(caption, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "cap") { Text(it) }
        },
        leadingContent = { BackupGlyph(p) },
        trailingContent = {
            val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            AnimatedContent(p, transitionSpec = { (scaleIn(pop) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "trail") {
                when (it) {
                    BackupPhase.WORKING -> LoadingIndicator(Modifier.size(32.dp))
                    BackupPhase.DONE -> Text("Salvo", style = MaterialTheme.typography.labelLargeEmphasized, color = tonal(Safe).second)
                    else -> Chevron()
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = container),
    )
}

@Composable
private fun FeatureTile(icon: ImageVector, title: String, desc: String, c: Color, shape: RoundedPolygon, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = tonal(c)
    Column(modifier.height(176.dp).springClick(RoundedCornerShape(28.dp), onClick = onClick).background(bg).padding(18.dp)) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Spinner(shape, fg.copy(.16f), 52.dp, periodMs = 16_000); Icon(icon, null, tint = fg) }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = fg)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = fg.copy(.8f), maxLines = 2)
    }
}

/** The download queue as a compact sheet over whatever is open, like the profile card: active downloads, ready installs and pending
 *  updates. Tapping a row opens that app; the sheet closes so the app page lands on top. */
@Composable
fun DownloadsSheet(vm: MainViewModel, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val cs = MaterialTheme.colorScheme
    val active = d.activeDownloads.size; val ready = d.readyToInstall.size
    val updates = vm.updates.filter { it.id !in d.activeDownloads && it.id !in d.readyToInstall }
    val all = vm.allApps()
    fun app(id: String) = all.firstOrNull { it.id == id }
    fun open(id: String) { vm.close(Overlay.DOWNLOADS); vm.navigateFromSettings(id) }
    CompositionLocalProvider(LocalAccent provides Blue) {
        SheetTitle("Downloads", Sym.Downloading, if (active + ready + updates.size == 0) "Tudo em dia" else "$active ativo(s) · $ready pronto(s) · ${updates.size} atualização(ões)")
        if (active + ready > 0) TextButton({ vm.clearQueue() }, Modifier.padding(horizontal = 16.dp)) { Text("Limpar fila") }
        val silent = s.useShizuku || s.useDhizuku || s.directInstall || s.rootMode
        val rows = ready + active + updates.size
        if (rows == 0) EmptyState(Sym.DownloadDone, "A fila está vazia", "Downloads e atualizações aparecem aqui.", Modifier.navigationBarsPadding())
        else Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            var i = 0
            val colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh)
            d.readyToInstall.forEach { (id, file) ->
                val a = app(id)
                SegmentedListItem({ a?.let { open(it.id) } }, ListItemDefaults.segmentedShapes(i++, rows), colors = colors,
                    leadingContent = { a?.let { AppIcon(it, 44.dp) } ?: TonalIcon(Sym.Inventory, Safe) }, supportingContent = { Text("Pronto para instalar") },
                    trailingContent = {
                        if (vm.installingId == id) IconButton({ vm.installingId = null }) { Icon(Sym.Close, "Parar de aguardar") }
                        else Button({
                            val target = a
                            if (target == null) vm.toast("Este download não está mais vinculado a um app") else vm.installFile(target, file)
                        }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text("Instalar") }
                    }) { Text(a?.name ?: id, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            d.activeDownloads.keys.forEach { id ->
                val a = app(id); val p = d.downloadProgress[id] ?: 0f
                SegmentedListItem({ a?.let { open(it.id) } }, ListItemDefaults.segmentedShapes(i++, rows), colors = colors,
                    leadingContent = { a?.let { AppIcon(it, 44.dp) } ?: TonalIcon(Sym.Downloading, Blue) },
                    supportingContent = {
                        Column {
                            Text(if (p > 0f) "Baixando ${p.roundToInt()}%" else "Iniciando download")
                            if (p <= 0f) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp)) else LinearWavyProgressIndicator({ p / 100f }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    },
                    trailingContent = { IconButton({ vm.cancelDownload(id) }) { Icon(Sym.Close, "Cancelar download") } }) {
                    Text(a?.name ?: id, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            updates.forEach { a ->
                SegmentedListItem({ open(a.id) }, ListItemDefaults.segmentedShapes(i++, rows), colors = colors,
                    leadingContent = { AppIcon(a, 44.dp) }, supportingContent = { Text(s.installedVersions[a.id]?.takeIf { it.isNotEmpty() }?.let { "v${it.removePrefix("v")} → v${a.latestVersion.removePrefix("v")}" } ?: "Atualizar para v${a.latestVersion.removePrefix("v")}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { FilledTonalButton({ vm.download(a) }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text("Atualizar") } }) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (silent && ready > 1) CtaButton("Instalar tudo ($ready)", vm::batchInstall, Modifier.padding(top = 12.dp), Sym.Bolt)
            else if (updates.size > 1) CtaButton("Atualizar tudo (${updates.size})", { vm.haptic(H.MEDIUM); updates.filter { it.installsHere() }.forEach { vm.download(it) } }, Modifier.padding(top = 12.dp), Sym.SystemUpdate, tonal = true)
            Text(TIPS.getValue("queue"), Modifier.fillMaxWidth().padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** A big number with a caption, for pages whose state is a count. */
@Composable
private fun StatHero(value: String, caption: String, action: @Composable () -> Unit = {}) {
    val (bg, fg) = tonal(LocalAccent.current)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AnimatedContent(value, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "n") {
                Text(it, style = MaterialTheme.typography.displayMediumEmphasized, color = fg)
            }
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
        }
        action()
    }
}

/** Seed palettes as Android-style split swatches in the current theme, then the user's own and a tile to make one.
 *  Picking one turns Material You off; the toggle below turns it back on. */
@Composable
private fun PalettePicker(vm: MainViewModel, s: Settings) {
    val cs = MaterialTheme.colorScheme; val reveal = LocalThemeReveal.current
    val theme = if (s.isOled) "oled" else s.theme
    var maker by remember { mutableStateOf<CustomPalette?>(null) }; var making by remember { mutableStateOf(false) }
    @Composable fun Swatch(p: Palette, onLong: (() -> Unit)? = null) {
        val sc = p.scheme(theme); val on = !s.materialYouEnabled && s.palette == p.id
        val at = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp).trackCenter(at).springClick(if (on) MaterialShapes.Cookie9Sided.toShape() else CircleShape, onLongClick = onLong) {
                vm.haptic(H.SELECTION)
                val apply = { vm.set { it.copy(palette = p.id, materialYouEnabled = false) } }
                if (reveal != null && !on) reveal.run(at.value, apply) else apply()
            }, contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(sc.primary))
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(sc.secondaryContainer))
                        Box(Modifier.weight(1f).fillMaxHeight().background(sc.tertiary))
                    }
                }
                if (on) Box(Modifier.size(24.dp).clip(CircleShape).background(sc.surface), contentAlignment = Alignment.Center) { Icon(Sym.Check, null, Modifier.size(16.dp), tint = sc.primary) }
            }
            Text(p.label, Modifier.padding(top = 8.dp), style = if (on) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                color = if (on) cs.primary else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Section("Paleta") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PALETTES.forEach { Swatch(it) }
            s.customPalettes.forEach { cp -> Swatch(customScheme(cp)) { vm.haptic(H.LIGHT); maker = cp; making = true } }
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).springClick(MaterialShapes.Cookie9Sided.toShape()) { maker = null; making = true }.background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Sym.Add, "Criar uma paleta", tint = cs.onPrimaryContainer)
                }
                Text("Criar", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
        }
        s.customPalettes.firstOrNull { it.id == s.palette && !s.materialYouEnabled }?.let { cp ->
            SRow(Sym.Edit, "Editar ${cp.name}", "Mude suas cores, estilo ou nome. Segure qualquer uma das suas paletas para editá-la.", onClick = { maker = cp; making = true }) { Chevron() }
        }
    }
    if (making) PaletteMakerSheet(vm, maker) { making = false }
}

/** Theme previews drawn from each real scheme; picking one sweeps the change across the screen from the tap. */
@Composable
private fun ThemePicker(vm: MainViewModel, s: Settings) {
    val reveal = LocalThemeReveal.current
    val current = if (s.isOled) "oled" else s.theme
    Section("Tema") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
            listOf("system" to "Sistema", "light" to "Claro", "dusk" to "Crepúsculo", "dark" to "Escuro", "oled" to "OLED").forEach { (t, label) ->
                val p = previewScheme(if (t == "system") if (sysDark) "dark" else "light" else t, s.palette, s.customPalettes); val on = if (t == "system") s.theme == "system" && !s.isOled else current == t
                val at = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val r by animateDpAsState(if (on) 24.dp else 14.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
                val ring by animateDpAsState(if (on) 3.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ring")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.62f).trackCenter(at).springClick(RoundedCornerShape(r)) {
                        vm.haptic(H.SELECTION)
                        val apply = { vm.set { it.copy(theme = if (t == "oled") "dark" else t, isOled = t == "oled") } }
                        if (reveal != null) reveal.run(at.value, apply) else apply()
                    }.border(ring, MaterialTheme.colorScheme.primary, RoundedCornerShape(r)).background(p.surface).padding(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(10.dp).clip(Pill).background(p.surfaceContainerHigh))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(p.primaryContainer))
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(p.tertiaryContainer))
                            }
                            Box(Modifier.fillMaxWidth(.7f).height(6.dp).clip(Pill).background(p.onSurface.copy(.5f)))
                            Box(Modifier.fillMaxWidth(.5f).height(6.dp).clip(Pill).background(p.onSurface.copy(.3f)))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp).clip(Pill).background(p.primary))
                        if (on) Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Icon(Sym.Check, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(label, Modifier.padding(top = 8.dp), style = if (on) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Modern vs classic store layout, chosen by picture rather than by switch. */
@Composable
private fun LayoutPicker(vm: MainViewModel, s: Settings) = Section("Layout da loja") {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("modern" to "Moderno", "classic" to "Clássico").forEach { (id, label) ->
            val on = s.storeLayout == id
            val r by animateDpAsState(if (on) 28.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
            Column(Modifier.weight(1f).springClick(RoundedCornerShape(r)) { vm.haptic(H.SELECTION); vm.set { it.copy(storeLayout = id) } }
                .background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh).padding(12.dp)) {
                Column(Modifier.fillMaxWidth().height(116.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (id == "modern") {
                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(cs.tertiaryContainer)) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(cs.outlineVariant)) } }
                    } else repeat(4) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(cs.tertiaryContainer)); Spacer(Modifier.width(6.dp))
                            Box(Modifier.fillMaxWidth(.8f).height(8.dp).clip(Pill).background(cs.outlineVariant))
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(on, null); Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
    Toggle(Sym.Description, "Sobre este app", "As linhas clássicas mostram uma prévia da descrição de cada app; toque em Ver mais para ler tudo", s.showAppAbout) { vm.set { it.copy(showAppAbout = !it.showAppAbout) } }
}

/** Floating dock size, previewed live at the chosen scale. */
@Composable
private fun DockSize(vm: MainViewModel, s: Settings) = Section("Tamanho do dock") {
    val cs = MaterialTheme.colorScheme
    val scale = s.bottomNavScale.takeIf { it.isFinite() } ?: .85f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).padding(16.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(Pill).background(cs.surfaceContainerHighest).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Sym.AndroidFilled, Sym.DesktopWindows, Sym.Tv, Sym.GridView, Sym.Code).forEachIndexed { i, ic ->
                    val on = i == 0
                    Row(Modifier.height(44.dp).clip(Pill).background(if (on) cs.secondaryContainer else Color.Transparent).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ic, null, Modifier.size(20.dp), tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                        if (on) Text("Apps", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                    }
                }
            }
        }
        Slider(scale, { v -> vm.set { it.copy(bottomNavScale = (v * 20).roundToInt() / 20f) } }, valueRange = .85f..1.15f, steps = 5)
        Row { Text("Compacto", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLargeEmphasized); Text("Grande", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.labelMedium) }
    }
}

/** Tablets, foldables and TVs: keep the side rail, or bring the phone dock to the bottom or top. Phones always keep the bottom dock. */
@Composable
private fun DockPosition(vm: MainViewModel, s: Settings) = Section("Navegação em telas grandes") {
    val cs = MaterialTheme.colorScheme
    val opts = listOf(Triple("rail", "Lateral", Sym.ViewQuilt), Triple("bottom", "Inferior", Sym.ArrowDownward), Triple("top", "Superior", Sym.ArrowUpward))
    val cur = s.wideNav.takeIf { w -> opts.any { it.first == w } } ?: "rail"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            opts.forEachIndexed { i, (id, label, icon) ->
                ToggleButton(cur == id, { vm.haptic(H.SELECTION); vm.set { it.copy(wideNav = id) } }, Modifier.weight(1f),
                    shapes = when (i) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); opts.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() },
                    contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("Para tablets, dobráveis, TVs e telas divididas largas. Celulares sempre mantêm o dock embaixo.",
            Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

private fun fontLabel(s: Settings) = if (s.appFont.startsWith("custom:")) s.customFonts.firstOrNull { "custom:${it.id}" == s.appFont }?.name ?: "Fonte personalizada"
    else Defaults.FONTS.firstOrNull { it.first == s.appFont }?.second ?: "Space Grotesk"

@Composable
private fun DevConfigEditor(vm: MainViewModel, s: Settings) = Section("Fonte da config de dev") {
    val url = s.devConfigUrl.trim(); val loading = vm.devStatus == "loading"
    Toggle(Sym.Science, "Ativar config de dev", "Carregar uma URL de config.json isolada em vez da config de produção", s.devConfigEnabled) {
        vm.set { it.copy(devConfigEnabled = !it.devConfigEnabled) }
    }
    Column(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(s.devConfigUrl, { v -> vm.set { it.copy(devConfigUrl = v) } }, Modifier.fillMaxWidth(), label = { Text("URL da config de dev") },
            placeholder = { Text("https://example.com/config.json") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(vm::reload, modifier = Modifier.weight(1f), enabled = s.devConfigEnabled && url.isNotEmpty() && !loading, shapes = ButtonDefaults.shapes()) {
                Text(if (loading) "Recarregando..." else "Recarregar config de dev")
            }
            OutlinedButton({ vm.set { it.copy(devConfigEnabled = false, devConfigUrl = "") }; vm.devStatus = "idle"; vm.devError = null; vm.reload() }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                Text("Desativar e limpar")
            }
        }
        Text(if (s.devConfigEnabled) "Status: ${when (vm.devStatus) { "loading" -> "carregando"; "loaded" -> "carregado"; "error" -> "falhou"; else -> "ocioso" }}" else "Status: modo de produção",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (s.devConfigEnabled && url.isEmpty()) Text("Digite uma URL de config para isolar totalmente os testes da config de produção.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.devConfigEnabled && vm.devStatus == "loaded") Text("Último carregamento: $url", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.devError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FontSheet(vm: MainViewModel, s: Settings, error: String?, importing: Boolean, onImport: () -> Unit, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    SheetTitle("Fontes", Sym.FontDownload, "Selecione a fonte do app")
    LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Aa Bb Gg 123", style = MaterialTheme.typography.displaySmallEmphasized)
                    Text("A rápida raposa marrom salta sobre o cão preguiçoso.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        @Composable fun FontRow(key: String, label: String, tag: String?, onDelete: (() -> Unit)? = null) {
            val on = s.appFont == key
            ListItem({ Text(label, fontWeight = if (on) FontWeight.Black else FontWeight.Medium) },
                Modifier.clip(RoundedCornerShape(if (on) 24.dp else 8.dp)).clickable { vm.haptic(H.SELECTION); vm.set { it.copy(appFont = key) } },
                supportingContent = tag?.let { { Text(it) } },
                trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                    onDelete?.let { IconButton(it) { Icon(Sym.DeleteOutline, "Excluir $label") } }
                    if (on) Icon(Sym.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                } },
                colors = ListItemDefaults.colors(containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer))
        }
        items(Defaults.FONTS) { (k, l) -> FontRow(k, l, when (k) { "spaceGrotesk" -> "Padrão"; "systemDefault" -> "Sistema"; else -> null }) }
        items(s.customFonts, key = { it.id }) { f -> FontRow("custom:${f.id}", f.name, "Personalizada") { vm.set { it.removeCustomFont(f.id) } } }
        item {
            OutlinedButton(onImport, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !importing, shapes = ButtonDefaults.shapes()) {
                Icon(Sym.Upload, null); Spacer(Modifier.width(8.dp)); Text(if (importing) "Importando..." else "Importar fonte personalizada")
            }
            error?.let { Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ativa: ", style = MaterialTheme.typography.bodySmall); Text(fontLabel(s), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Button(onDismiss, shapes = ButtonDefaults.shapes()) { Text("Concluir") }
            }
        }
    }
    Spacer(Modifier.navigationBarsPadding())
}

/** One-time guided setup for direct installs: Wi-Fi, Developer options, then pairing through the notification shade. */
@Composable
internal fun DirectInstallSheet(vm: MainViewModel, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val ctx = LocalContext.current; val close = LocalSheetClose.current
    val st by WirelessAdb.status.collectAsState()
    var wifi by remember { mutableStateOf(WirelessAdb.onWifi(ctx)) }; var dev by remember { mutableStateOf(WirelessAdb.devOptions(ctx)) }
    var wl by remember { mutableStateOf(WirelessAdb.wirelessOn(ctx)) }
    var code by remember { mutableStateOf("") }; var enabled by remember { mutableStateOf(false) }
    fun enable() { if (enabled) return; enabled = true; vm.set { it.copy(directInstall = true) }; vm.haptic(H.SUCCESS); vm.toast("As instalações agora pulam a espera de 24 horas"); close() }
    // Already paired: no setup needed
    LaunchedEffect(Unit) { if (runCatching { WirelessAdb.connect(ctx) }.isSuccess) enable() }
    LaunchedEffect(st.phase) { if (st.phase == WirelessAdb.Phase.READY) enable() }
    // Re-check while the user is away in system Settings
    LaunchedEffect(Unit) { while (true) { delay(1000); wifi = WirelessAdb.onWifi(ctx); dev = WirelessAdb.devOptions(ctx); wl = WirelessAdb.wirelessOn(ctx) } }

    Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text("Pular a espera de 24 horas", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmallEmphasized)
        Text("Configuração única. O AlphaStore instala pela depuração sem fio, a rota ADB que o Google deixa aberta, para os apps instalarem na hora.",
            Modifier.padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SetupStep(1, "Conectar ao Wi-Fi", if (wifi) "Conectado" else "A depuração sem fio só funciona no Wi-Fi.", wifi)
            SetupStep(2, "Desbloquear Opções do desenvolvedor", if (dev) "Desbloqueado" else "Toque em Número da versão 7 vezes em Sobre o telefone.", dev)
            SetupStep(3, "Ativar depuração sem fio", if (wl) "Ativada" else "Ative e toque em Permitir.", wl)
            SetupStep(4, "Parear o AlphaStore", "Toque em Parear dispositivo com código de pareamento e digite o código na notificação do AlphaStore.", st.phase == WirelessAdb.Phase.READY)
        }
        Text("A notificação do AlphaStore guia você em cada etapa e avança sozinha.", Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { WirelessAdb.startSetup(ctx) }, modifier = Modifier.fillMaxWidth(), enabled = wifi && st.phase != WirelessAdb.Phase.PAIRING, shapes = ButtonDefaults.shapes()) {
            Text(if (st.phase == WirelessAdb.Phase.IDLE) "Iniciar configuração" else "Continuar")
        }
        if (st.phase == WirelessAdb.Phase.PAIRING || st.phase == WirelessAdb.Phase.FAILED) Row(Modifier.padding(8.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (st.phase == WirelessAdb.Phase.PAIRING) { LoadingIndicator(Modifier.size(32.dp)); Spacer(Modifier.width(8.dp)) }
            Text(st.msg, color = if (st.phase == WirelessAdb.Phase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
            label = { Text("Ou digite o código aqui (tela dividida)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = { TextButton({ WirelessAdb.pairAsync(ctx, code) }, enabled = code.length == 6 && st.phase != WirelessAdb.Phase.PAIRING) { Text("Parear") } })
    }
    Spacer(Modifier.navigationBarsPadding().height(16.dp))
}

@Composable
private fun SetupStep(n: Int, title: String, desc: String, done: Boolean, action: (@Composable () -> Unit)? = null) = ListItem(
    { Text(title, style = MaterialTheme.typography.titleMedium) },
    supportingContent = { Column { Text(desc); action?.invoke() } },
    leadingContent = {
        if (done) TonalIcon(Sym.Check, Green)
        else Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text("$n", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
)

@Composable
private fun InstallerSheet(vm: MainViewModel, s: Settings, step: String, setStep: (String) -> Unit, list: List<Installer>, loading: Boolean, error: String?, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BackHandler(step == "specific") { setStep("mode") }
        Text("Escolher instalador", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.headlineSmallEmphasized)
        if (step == "specific" && s.hiddenInstallers.isNotEmpty()) IconButton({ vm.set { it.copy(hiddenInstallers = emptyList()) } }) { Icon(Sym.RestartAlt, "Restaurar instaladores ocultos") }
    }
    AnimatedContent(step, label = "step") { st ->
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st == "mode") {
                Selectable(s.installerPreference == "system", "Instalador do sistema") { vm.set { it.withInstaller("system") }; onDismiss() }
                Selectable(s.installerPreference == "chooser", "Perguntar sempre") { vm.set { it.withInstaller("chooser") }; onDismiss() }
                Selectable(s.installerPreference == "package", "Instalador específico", if (s.installerPreference == "package") s.installerLabel.ifEmpty { "Selecionado" } else null) { setStep("specific") }
            } else when {
                loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { LoadingIndicator(); Text("Carregando...", Modifier.padding(start = 12.dp)) }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                list.isEmpty() -> EmptyState(Sym.Android, "Nenhum instalador encontrado.")
                else -> LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shown = list.filter { it.packageName !in s.hiddenInstallers }
                    items(shown, key = { it.packageName }) { ins ->
                        InstallerRow(vm, ins, s.installerPreference == "package" && s.installerPackage == ins.packageName, ins == shown.first(),
                            { vm.set { it.withInstaller("package", ins.packageName, ins.label) }; onDismiss() }) { vm.set { it.hideInstaller(ins.packageName) } }
                    }
                }
            }
        }
    }
    Spacer(Modifier.navigationBarsPadding())
}

@Composable
private fun InstallerRow(vm: MainViewModel, ins: Installer, selected: Boolean, hint: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, ins.packageName) { value = withContext(Dispatchers.IO) { vm.tracker.icon(ins.packageName)?.asImageBitmap() } }
    SwipeRow(RoundedCornerShape(20.dp), right = SwipeAction(Sym.VisibilityOff, "Ocultar instalador", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, removes = true, run = onRemove), hint = hint) {
        ListItem({ Text(ins.label, fontWeight = FontWeight.Bold) }, Modifier.clickable(onClick = onSelect),
            supportingContent = { Text(ins.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { icon?.let { Image(it, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) } ?: TonalIcon(Sym.Android, MaterialTheme.colorScheme.primary) },
            trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                if (ins.isSystem) Text("Sistema", Modifier.clip(Pill).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                if (selected) Icon(Sym.CheckCircle, null, Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.primary)
            } },
            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

@Composable
private fun ScannerSheet(apps: List<AppItem>, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }; var done by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }; var total by remember { mutableIntStateOf(apps.size) }
    var results by remember { mutableStateOf<List<MissionResult>>(emptyList()) }
    fun start() = scope.launch {
        scanning = true; progress = 0; done = false; results = emptyList()
        results = runCatalogDiagnostic(apps) { p, t -> progress = p; total = t }
        scanning = false; done = true
    }
    SheetTitle("Verificador de missão (Admin)", Sym.Radar)
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        when {
            scanning -> {
                Text("Verificando catálogo...", style = MaterialTheme.typography.titleMedium)
                LinearWavyProgressIndicator({ if (total == 0) 0f else progress / total.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
                Text("$progress / $total apps verificados", style = MaterialTheme.typography.bodySmall)
            }
            done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Verificação concluída", style = MaterialTheme.typography.titleMedium)
                        Text("Encontrados ${results.size} apps com problemas de um total de $total.", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(::start, shapes = ButtonDefaults.shapes()) { Text("Verificar de novo") }
                }
                if (results.isEmpty()) Text("Tudo parece perfeito!", Modifier.padding(vertical = 24.dp), color = Safe, fontWeight = FontWeight.Bold)
                else LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.appId }) { r ->
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer.copy(.4f))) {
                            Column(Modifier.padding(14.dp)) {
                                Text(r.appName, fontWeight = FontWeight.Bold); Text(r.appId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                r.issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                            }
                        }
                    }
                }
            }
            else -> {
                Text("Pronto para verificar", style = MaterialTheme.typography.titleMedium)
                Text("Isso executará um diagnóstico em todos os ${apps.size} apps do seu catálogo, verificando texto ausente e enviando requisições HTTP HEAD para validar URLs.",
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(::start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) { Text("Executar diagnóstico agora") }
            }
        }
    }
    Spacer(Modifier.navigationBarsPadding().height(16.dp))
}

private val ICON_ART = mapOf(
    "default" to (R.mipmap.ic_launcher_background to R.mipmap.ic_launcher_foreground),
    "midnight" to (R.drawable.ic_icon_midnight_bg to R.drawable.ic_icon_midnight_fg), "sunset" to (R.drawable.ic_icon_sunset_bg to R.drawable.ic_icon_sunset_fg),
    "ocean" to (R.drawable.ic_icon_ocean_bg to R.drawable.ic_icon_ocean_fg), "mint" to (R.drawable.ic_icon_mint_bg to R.drawable.ic_icon_mint_fg),
    "neon" to (R.drawable.ic_icon_neon_bg to R.drawable.ic_icon_neon_fg), "gold" to (R.drawable.ic_icon_gold_bg to R.drawable.ic_icon_gold_fg),
)

/** A scrolling row of picks; locked ones carry their ad count and route to Fuel the Code. */
@Composable
private fun CosmeticRow(items: List<Cosmetic>, selected: String, width: androidx.compose.ui.unit.Dp, onPick: (Cosmetic) -> Unit, art: @Composable (Cosmetic) -> Unit) {
    val s = LocalS.current; val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).horizontalScroll(rememberScrollState()).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { c ->
            val on = c.id == selected; val open = c.unlocked(s)
            val r by animateDpAsState(if (on) 28.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
            Column(Modifier.width(width).springClick(RoundedCornerShape(r)) { onPick(c) }.background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(64.dp).fillMaxWidth().graphicsLayer { alpha = if (open) 1f else .45f }, contentAlignment = Alignment.Center) { art(c) }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!open) { Icon(Sym.Lock, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(c.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                Text(when { open -> if (on) "Em uso" else "Toque para usar"; s.unlockTarget == c.id -> "${c.progress(s)}/${c.ads} · desbloqueando"; else -> "${c.progress(s)}/${c.ads} anúncios" }, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IconPicker(vm: MainViewModel, s: Settings) = Section("Ícone do app") {
    CosmeticRow(APP_ICONS, s.appIcon, 92.dp, { vm.pickCosmetic(it, true) }) { c ->
        val (bg, fg) = ICON_ART.getValue(c.id)
        Box(Modifier.size(56.dp).clip(CircleShape)) {
            Image(painterResource(bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Image(painterResource(fg), c.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
    InfoCard("Toque em Neon ou Gold e assista anúncios em Abasteça o Código: cada um conta para o desbloqueio. Desbloqueie os dois para o emblema Colecionador de Ícones.", icon = Sym.Palette)
}

/** Each dock drawn small, in its own colors and selected-item style. */
@Composable
private fun DockPicker(vm: MainViewModel, s: Settings) = Section("Estilo do dock") {
    val cs = MaterialTheme.colorScheme
    CosmeticRow(DOCKS, s.navDock, 132.dp, { vm.pickCosmetic(it, false) }) { c ->
        val icons = listOf(Sym.AndroidFilled, Sym.DesktopWindows, Sym.GridView, Sym.Code)
        val (bg, fg) = cs.surfaceContainerHighest to cs.onSurfaceVariant
        val shape = when (c.id) { "bar" -> RoundedCornerShape(4.dp); "labels" -> RoundedCornerShape(12.dp); else -> Pill }
        Row(Modifier.fillMaxWidth().clip(shape).background(bg).padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            icons.forEachIndexed { i, ic ->
                val on = i == 0
                val (ibg, ifg, ishape) = when (c.id) {
                    "vibrant" -> Triple(if (on) cs.primary else Color.Transparent, if (on) cs.onPrimary else fg, if (on) RoundedCornerShape(8.dp) else CircleShape)
                    "shapes" -> Triple(if (on) cs.secondaryContainer else Color.Transparent, if (on) cs.onSecondaryContainer else fg, if (on) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                    else -> Triple(if (on) cs.secondaryContainer else Color.Transparent, if (on) cs.onSecondaryContainer else fg, Pill)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(if (c.id == "bar" || c.id == "labels") 26.dp else 22.dp, if (c.id == "bar" || c.id == "labels") 16.dp else 22.dp).clip(ishape).background(ibg), contentAlignment = Alignment.Center) {
                        Icon(ic, null, Modifier.size(12.dp), tint = ifg)
                    }
                    if (c.id == "bar" || c.id == "labels") Box(Modifier.padding(top = 2.dp).size(14.dp, 3.dp).clip(Pill).background(fg.copy(.5f)))
                }
            }
        }
    }
    InfoCard("Toque em Expressive ou Shape shift e assista anúncios em Abasteça o Código: cada um conta para o desbloqueio. Desbloqueie os dois para o emblema Mestre do Dock.", icon = Sym.Palette)
}
