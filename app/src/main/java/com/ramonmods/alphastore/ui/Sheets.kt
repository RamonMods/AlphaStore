package com.ramonmods.alphastore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramonmods.alphastore.data.*
import com.ramonmods.alphastore.platform.ScanTarget
import com.ramonmods.alphastore.platform.ScanTargetType
import com.ramonmods.alphastore.platform.VtResult
import com.ramonmods.alphastore.platform.VtScan
import com.ramonmods.alphastore.platform.VtView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

val ty: Typography @Composable @ReadOnlyComposable get() = MaterialTheme.typography
val cs: ColorScheme @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

/** Vertical segmented list corners: 20dp at group ends, 4dp between rows. */
fun colShape(i: Int, n: Int) = RoundedCornerShape(if (i == 0) 20.dp else 4.dp, if (i == 0) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp)

@Composable
fun Eyebrow(text: String, c: Color = cs.primary, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = ty.labelMedium, fontWeight = FontWeight.Bold, color = c, letterSpacing = 1.2.sp)

/** Sheet lead block: optional visual, eyebrow, emphasized headline, supporting body. */
@Composable
fun Lead(title: String, body: String? = null, eyebrow: String? = null, eyebrowColor: Color = cs.primary, center: Boolean = false, top: (@Composable () -> Unit)? = null) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        top?.let { it(); Spacer(Modifier.height(16.dp)) }
        eyebrow?.let { Eyebrow(it, eyebrowColor) }
        Text(title, style = ty.headlineSmallEmphasized, textAlign = align)
        body?.let { Text(it, Modifier.padding(top = 6.dp), style = ty.bodyMedium, color = cs.onSurfaceVariant, textAlign = align) }
    }
}

@Composable
fun PickerHeader(title: String, subtitle: String?, onBack: () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BackHandler(onBack = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = ty.headlineSmallEmphasized)
            subtitle?.let { Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }

/** Pulsing placeholder that keeps the final card geometry. */
@Composable
fun Skeleton(modifier: Modifier, shape: Shape = RoundedCornerShape(20.dp)) {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(.45f, .9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(shape).graphicsLayer { alpha = a }.background(cs.surfaceContainerHighest))
}

/** Searchable, category-filtered, paged app list shared by the bundle editor and creator. */
@Composable
fun AppPickerList(pool: List<AppItem>, platform: String, modifier: Modifier = Modifier, selected: Set<String> = emptySet(), status: ((Int, Int) -> String)? = null, onPick: (AppItem) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }; var cat by rememberSaveable { mutableStateOf("All") }; var limit by rememberSaveable { mutableIntStateOf(20) }
    val cats = remember(pool) { pool.groupingBy { it.category }.eachCount().toSortedMap() }
    val filtered = remember(pool, q, cat) {
        val t = q.trim()
        pool.filter { (cat == "All" || it.category == cat) && (t.isEmpty() || it.name.contains(t, true) || it.author.contains(t, true) || it.packageName?.contains(t, true) == true) }
    }
    val shown = filtered.take(limit); val left = filtered.size - shown.size
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            OutlinedTextField(q, { q = it; limit = 20 }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar apps de $platform...") }, singleLine = true, shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Sym.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = ""; limit = 20 }) { Icon(Sym.Close, "Limpar busca") } })
            Row(Modifier.fillMaxWidth().bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All" to pool.size) + cats.toList()).forEach { (k, n) ->
                    FilterChip(cat == k, { cat = k; limit = 20 }, { Text("${if (k == "All") "Todos" else k} ($n)") }, leadingIcon = if (cat == k) ({ Icon(Sym.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
            Text(status?.invoke(shown.size, filtered.size) ?: "Mostrando ${shown.size}/${filtered.size}", Modifier.padding(start = 4.dp, bottom = 8.dp), style = ty.labelMedium, color = cs.onSurfaceVariant)
        }
        if (filtered.isEmpty()) item { EmptyState(Sym.SearchOff, "Nenhum app corresponde.") }
        itemsIndexed(shown, key = { _, a -> a.id }) { i, a ->
            val on = a.id in selected
            GroupItem(i, shown.size, selected = on, onClick = { onPick(a) }) {
                ListItem({ Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = if (on) ty.titleMediumEmphasized else ty.titleMedium) },
                    supportingContent = { Text("${a.category} · ${a.author}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AppIcon(a, 40.dp) },
                    trailingContent = { Icon(if (on) Sym.CheckCircle else Sym.AddCircleOutline, null, tint = if (on) cs.primary else cs.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
        if (left > 0) item {
            FilledTonalButton({ limit += 20 }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shapes = ButtonDefaults.shapes()) { Text("Carregar mais ${min(20, left)} ($left restantes)") }
        }
    }
}

// ---------------- Notice ----------------

@Composable
fun NoticeSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val n = vm.config?.notice
    if (n == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    AlphaStoreSheet(onDismiss) {
        val close = LocalSheetClose.current
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Lead(n.title, n.message, "Aviso", center = true) { Blob(Sym.Campaign, cs.primary, 80, MaterialShapes.Sunny) }
            Spacer(Modifier.height(16.dp))
            CtaButton("Entendi", close, icon = Sym.Check)
        }
    }
}

// ---------------- FAQ ----------------

@Composable
fun FaqSheet(vm: MainViewModel, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val faqs = vm.config?.faqs?.takeIf { it.isNotEmpty() } ?: Defaults.FAQS
    var open by rememberSaveable { mutableIntStateOf(-1) }
    SheetTitle("Perguntas frequentes", Sym.HelpOutline, "Dúvidas comuns e segredos")
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
        itemsIndexed(faqs) { i, f ->
            val on = open == i
            GroupItem(i, faqs.size, selected = on, onClick = { open = if (on) -1 else i; vm.haptic(H.SELECTION) }) {
                Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(if (on) MaterialShapes.Cookie6Sided.toShape() else CircleShape).background(if (on) cs.primary else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Icon(faIcon(f.icon, Sym.QuestionMark), null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(f.question, Modifier.weight(1f), style = if (on) ty.titleSmallEmphasized else ty.titleSmall)
                        Icon(if (on) Sym.ExpandLess else Sym.ExpandMore, null, tint = cs.onSurfaceVariant)
                    }
                    if (on) Text(f.answer, Modifier.padding(start = 54.dp, top = 10.dp), style = ty.bodyMedium)
                }
            }
        }
    }
}

// ---------------- What's new (GitHub Releases) ----------------

/** What's new reads GitHub Releases directly: publishing a release is the only step, no JSON to maintain. Cached for offline and rate limits. */
@Composable
fun ReleaseNotesSheet(vm: MainViewModel, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val ctx = LocalContext.current
    var releases by remember { mutableStateOf<List<Release>?>(null) }; var failed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val f = File(ctx.filesDir, "releases.json")
        withContext(Dispatchers.IO) { runCatching { parseReleases(f.readText()) }.getOrNull() }?.let { releases = it }
        // GitHub allows 60 unauthenticated calls an hour per network, so a fresh cache is used as-is
        if (releases == null || System.currentTimeMillis() - f.lastModified() > 30 * 60_000L) runCatching {
            val raw = Net.text(Defaults.RELEASES_API, mapOf("Accept" to "application/vnd.github+json"))
            releases = parseReleases(raw); withContext(Dispatchers.IO) { f.writeText(raw) }
        }.onFailure { if (releases == null) failed = true }
    }
    SheetTitle("Novidades", Sym.NewReleases, "Direto dos lançamentos da AlphaStore")
    val list = releases
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            list == null && !failed -> items(2) { Skeleton(Modifier.fillMaxWidth().height(140.dp)) }
            list.isNullOrEmpty() -> item {
                EmptyState(Sym.CloudOff, "Não foi possível acessar o GitHub", "As notas de versão carregam quando você estiver online.")
                TextButton({ vm.tracker.open("https://github.com/RamonMods/AlphaStore/releases") }, Modifier.fillMaxWidth()) { Text("Abrir lançamentos no GitHub") }
            }
            else -> itemsIndexed(list, key = { _, r -> r.version }) { i, r ->
                ReleaseCard(vm, r, latest = i == 0, expanded = (open ?: list[0].version) == r.version) { open = if ((open ?: list[0].version) == r.version) "" else r.version }
            }
        }
    }
}

/** Collapsed: version, title, when. Open: section titles with one-line headlines, and a link to the full notes. */
@Composable
private fun ReleaseCard(vm: MainViewModel, r: Release, latest: Boolean, expanded: Boolean, toggle: () -> Unit) {
    val installed = isSameVersion(r.version, STORE_VERSION)
    val fg = if (latest) cs.onPrimaryContainer else cs.onSurface
    Surface(toggle, shape = RoundedCornerShape(if (expanded) 28.dp else 20.dp), color = if (latest) cs.primaryContainer else cs.surfaceContainerHigh, contentColor = fg) {
        Column(Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${r.version}", Modifier.weight(1f), style = ty.titleLargeEmphasized)
                if (installed) Tag("Instalado", if (latest) cs.primary else Safe) else if (latest) Tag("Mais recente", cs.primary)
            }
            if (r.name.isNotEmpty()) Text(r.name, style = ty.titleMedium, fontWeight = FontWeight.SemiBold)
            val count = r.sections.sumOf { it.items.size }
            val whenText = if (r.publishedAt > 0) android.text.format.DateUtils.getRelativeTimeSpanString(r.publishedAt).toString() else ""
            Text(listOf(whenText, if (count > 0) "$count alterações" else "").filter { it.isNotEmpty() }.joinToString(" · "), style = ty.labelMedium, color = fg.copy(.7f))
            if (expanded) {
                r.sections.forEach { sec ->
                    if (sec.title.isNotEmpty()) Text(sec.title, Modifier.padding(top = 16.dp, bottom = 4.dp), style = ty.titleSmallEmphasized, color = cs.primary)
                    sec.items.forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(fg.copy(.5f)))
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = ty.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (r.url.isNotEmpty()) TextButton({ vm.tracker.open(r.url) }, Modifier.padding(top = 8.dp)) { Text("Notas completas no GitHub"); Spacer(Modifier.width(6.dp)); Icon(Sym.OpenInNew, null, Modifier.size(16.dp)) }
            }
        }
    }
}

// ---------------- Tutorial (Modern UI intro + feature tour) ----------------

private data class Step(val icon: ImageVector, val title: String, val body: String)

private val HERO_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided)
private val UI_STEPS = listOf(
    Step(Sym.AutoAwesome, "Experimente a nova interface", "A AlphaStore tem uma nova interface moderna com coleções selecionadas, carrosséis em destaque e um layout lindo."),
    Step(Sym.Settings, "Abra as Configurações", "Toque no ícone de engrenagem no canto superior direito"),
    Step(Sym.Palette, "Vá para Interface", "Encontre a seção Interface nas configurações"),
    Step(Sym.ToggleOn, "Ative a interface moderna", "Ative a opção \"Interface moderna\" para liberar o novo layout"),
)
private val TOUR_STEPS = listOf(
    Step(Sym.Terminal, "Menu de desenvolvedor", "Toque na aba Dev para ver as novidades por dentro."),
    Step(Sym.VolunteerActivism, "Apoie a AlphaStore", "Adicionamos novas formas de doar. Confira!"),
    Step(Sym.Favorite, "Obrigado!", "Agora você pode apoiar a AlphaStore via Ko-fi, UPI ou PayPal."),
)

/** Hero moment: the container morphs to a new MaterialShape and turns a quarter on every step. */
@Composable
private fun StepHero(steps: List<Step>, i: Int, dots: IntRange?) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(); val sf = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val sp = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MorphBlob(HERO_SHAPES, i, 136.dp, cs.primaryContainer) {
            AnimatedContent(i, transitionSpec = { (fadeIn(fx) + scaleIn(sf, .6f)) togetherWith (fadeOut(fx) + scaleOut(sf, .6f)) }, label = "icon") {
                Icon(steps[it].icon, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(i, transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (fadeIn(fx) + slideInHorizontally(sp) { it / 4 * dir }) togetherWith (fadeOut(fx) + slideOutHorizontally(sp) { -it / 4 * dir })
        }, label = "text") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(steps[it].title, style = ty.headlineMediumEmphasized, textAlign = TextAlign.Center)
                Text(steps[it].body, Modifier.padding(top = 8.dp), style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        dots?.let { r ->
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { d ->
                    val w by animateDpAsState(if (d == i) 24.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dw")
                    val col by animateColorAsState(if (d == i) cs.primary else cs.outlineVariant, MaterialTheme.motionScheme.fastEffectsSpec(), label = "dc")
                    Box(Modifier.height(8.dp).width(w).clip(Pill).background(col))
                }
            }
        }
    }
}

@Composable
fun TutorialSheet(vm: MainViewModel, onDismiss: () -> Unit) = if (vm.tour) TourSheet(vm, onDismiss) else ModernUiSheet(vm, onDismiss)

@Composable
private fun ModernUiSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var enable by remember { mutableStateOf(false) }; var settings by remember { mutableStateOf(false) }
    AlphaStoreSheet({
        vm.set { it.copy(hasSeenModernUITutorial = true, storeLayout = if (enable) "modern" else it.storeLayout) }
        onDismiss(); if (settings) vm.openSettings()
    }) {
        val close = LocalSheetClose.current
        val now = { enable = true; vm.haptic(H.SUCCESS); close() }
        StepHero(UI_STEPS, step, if (step > 0) 1..3 else null)
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step) {
                0 -> {
                    CtaButton("Ativar agora", now, icon = Sym.Bolt)
                    CtaButton("Como ativar", { step = 1; vm.haptic(H.SELECTION) }, icon = Sym.Lightbulb, tonal = true)
                    TextButton(close, Modifier.fillMaxWidth()) { Text("Pular") }
                }
                UI_STEPS.lastIndex -> {
                    CtaButton("Ativar agora", now, icon = Sym.Bolt)
                    CtaButton("Abrir Configurações", { settings = true; close() }, icon = Sym.Settings, tonal = true)
                }
                else -> {
                    CtaButton("Próximo", { step++; vm.haptic(H.SELECTION) }, icon = Sym.ArrowForward)
                    TextButton(close, Modifier.fillMaxWidth()) { Text("Pular") }
                }
            }
        }
    }
}

@Composable
private fun TourSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    AlphaStoreSheet({ vm.setFlag("tour", "1"); onDismiss() }) {
        val close = LocalSheetClose.current
        StepHero(TOUR_STEPS, step, TOUR_STEPS.indices)
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            CtaButton(if (step == TOUR_STEPS.lastIndex) "Entendi!" else "Próximo", {
                vm.haptic(H.SELECTION)
                when (step) {
                    0 -> { vm.tab = Tab.ABOUT; step = 1 }
                    1 -> { vm.tab = Tab.ABOUT; vm.donateOpen = true; step = 2 }
                    else -> close()
                }
            }, icon = if (step == TOUR_STEPS.lastIndex) Sym.Check else Sym.ArrowForward)
        }
    }
}

// ---------------- Bundle preview / editor ----------------

@Composable
fun BundlePreviewSheet(vm: MainViewModel, b: BundleItem, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val removed = s.removedBundleApps[b.id].orEmpty(); val extras = s.extraBundleApps[b.id].orEmpty()
    val all = remember(d.apps, d.importedApps) { (d.apps + d.importedApps).distinctBy { it.id } }
    val byId = remember(all) { all.associateBy { it.id } }
    val apps = (b.apps.filter { it.id !in removed } + extras.mapNotNull { byId[it] }).distinctBy { it.id }
    val present = apps.map { it.id }.toSet()
    val local = b.badge == "Local"; val customized = removed.isNotEmpty() || extras.isNotEmpty()
    val first = remember { apps.firstOrNull()?.id }
    var picking by rememberSaveable { mutableStateOf(false) }; var ask by remember { mutableStateOf<String?>(null) }
    ConfirmDialog(ask, "Excluir", onConfirm = {
        vm.set { st -> st.resetBundle(b.id).copy(customBundles = st.customBundles.filterNot { it.id == b.id }) }; vm.haptic(H.WARNING); close()
    }, onDismiss = { ask = null })
    AnimatedContent(picking, label = "bundle") { pick ->
        if (pick) Column {
            val platform = vm.tab.targetPlatform()
            PickerHeader("Adicionar ao pacote", b.title) { picking = false }
            AppPickerList(all.filter { it.platform == platform && it.id !in present }, platform) { a ->
                vm.set { it.addAppToBundle(b.id, a.id) }; vm.haptic(H.SUCCESS); picking = false
            }
        } else LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(cs.tertiaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
                        if (b.icon != null) Icon(faIcon(b.icon), null, Modifier.size(30.dp), tint = cs.onTertiaryContainer)
                        else Text((b.monogram ?: b.title).take(2).uppercase(), style = ty.titleLarge, fontWeight = FontWeight.Black, color = cs.onTertiaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Pacote recomendado")
                        Text(b.title, style = ty.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${apps.size} app${if (apps.size == 1) "" else "s"}${b.badge?.let { " · $it" } ?: ""}", style = ty.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                if (b.description.isNotBlank()) Text(b.description, Modifier.padding(horizontal = 8.dp), style = ty.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                CtaButton("Baixar", { vm.bundleDownload(b) }, icon = Sym.Download, enabled = apps.isNotEmpty())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ picking = true }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                        Icon(Sym.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Adicionar app")
                    }
                    if (customized) FilledTonalButton({ vm.set { it.resetBundle(b.id) }; vm.haptic(H.MEDIUM) }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                        Icon(Sym.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Redefinir")
                    }
                    if (local) OutlinedButton({ ask = "Excluir este pacote?" }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                        Icon(Sym.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Excluir")
                    }
                }
            }
            if (apps.isEmpty()) item { EmptyState(Sym.Inventory2, "O pacote está vazio", "Adicione apps com o botão acima.") }
            itemsIndexed(apps, key = { _, a -> a.id }) { i, a ->
                BundleRow(vm, a, colShape(i, apps.size), a.id in extras, !s.installedVersions[a.id].isNullOrEmpty(), a.id == first, onOpen = { vm.selectApp(a) }) {
                    vm.set { st ->
                        var x = st
                        if (a.id in extras) x = x.removeAppFromBundleExtras(b.id, a.id)
                        if (b.apps.any { it.id == a.id }) x = x.toggleBundleAppRemoval(b.id, a.id)
                        x
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleRow(vm: MainViewModel, a: AppItem, shape: Shape, extra: Boolean, installed: Boolean, hint: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    SwipeRow(shape, Modifier.padding(bottom = 2.dp), right = SwipeAction(Sym.Delete, "Remover do pacote", cs.error, cs.onError, removes = true, run = onRemove), hint = hint) {
        ListItem({ Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) }, Modifier.clickable(onClick = onOpen),
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.category, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (installed) Tag("Instalado", Safe)
                    if (extra) Tag("Adicionado", cs.tertiary)
                }
            },
            leadingContent = { AppIcon(a, 44.dp) }, trailingContent = { Icon(Sym.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh))
    }
}

// ---------------- VirusTotal ----------------

private val MOD_PAGES = listOf(
    Step(Sym.Extension, "Apps com patch precisam de contexto", "Builds do Morphe modificam o código original do app. Mecanismos antivírus podem sinalizar essas mudanças mesmo quando a build está limpa."),
    Step(Sym.Warning, "Falsos positivos podem acontecer", "Detecções heurísticas podem reagir a patches, mudanças de assinatura ou código de compatibilidade incluído. Leia o resultado; não entre em pânico por uma sinalização."),
    Step(Sym.Build, "Melhor caminho de verificação", "Se as sinalizações parecerem suspeitas, faça o patch você mesmo com o Morphe Manager, mantenha os patches atualizados e depois refaça a verificação manualmente no VirusTotal."),
)
private val MOD_SHAPES = listOf(MaterialShapes.Cookie4Sided, MaterialShapes.SoftBurst, MaterialShapes.Gem)
private val VT_TIPS = listOf("Dica: Verifique apenas apps que você pretende instalar.", "Truque da AlphaStore: Verifique a tag de patches antes de instalar builds modificadas.",
    "Curiosidade: 1 a 4 detecções em apps modificados podem ser falsos positivos.", "Dica pro: Abra o relatório completo do VirusTotal e leia os nomes dos mecanismos.")
private const val VT_KEY_TUTORIAL = "https://www.youtube.com/shorts/Ynqw4dgH5ko"

private data class VtRisk(val label: String, val color: Color, val icon: ImageVector, val note: String)
private fun risk(flagged: Int) = when {
    flagged == 0 -> VtRisk("Limpo", Safe, Sym.VerifiedUser, "0 detecções. Este é o melhor resultado no VirusTotal.")
    flagged == 1 -> VtRisk("Sinal baixo", Blue, Sym.Info, "1 detecção. Muitas vezes é um falso positivo, especialmente em apps modificados.")
    flagged < 5 -> VtRisk("Revisar", Caution, Sym.QueryStats, "2-4 detecções. Verifique os nomes dos mecanismos e a origem do app.")
    else -> VtRisk("Alto cuidado", Red, Sym.GppBad, "5+ detecções. Trate como arriscado até verificar manualmente.")
}

/** Downloaded APK first, then the installed APK, then a direct remote APK link. Android only. */
fun scanTargetFor(app: AppItem, s: Settings, d: DataState): ScanTarget? {
    if (!app.installsHere()) return null
    (d.readyToInstall[app.id] ?: d.pendingCleanup[app.id]?.fileName)?.let { return ScanTarget(ScanTargetType.DOWNLOADED_APK, "APK baixado", it) }
    if (!app.packageName.isNullOrEmpty() && !s.installedVersions[app.id].isNullOrEmpty()) return ScanTarget(ScanTargetType.APK, "APK instalado", app.packageName)
    val variant = (app.availableVersions.firstOrNull()?.variants?.firstOrNull()?.url ?: app.variants.firstOrNull()?.url)?.takeIf { it.isNotEmpty() }
    val url = variant ?: app.downloadUrl
    val direct = variant != null || Regex("\\.(apk|apks|xapk|zip)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)
    return if (url.startsWith("http") && url != "#" && direct) ScanTarget(ScanTargetType.REMOTE_APK, "Arquivo APK", url) else null
}

@Composable
fun VirusTotalSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val scope = rememberCoroutineScope(); val t = rememberSheetToast()
    val vt = remember { VtScan(vm.tracker, app.id, app.name, app.packageName, s.virusTotalApiKey) }
    val target = scanTargetFor(app, s, d)
    val modded = remember { listOf(app.name, app.author, app.id).any { it.contains("revanced", true) || it.contains("morphe", true) } }
    var page by rememberSaveable { mutableIntStateOf(if (modded) 0 else MOD_PAGES.size) }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    fun revoke() { vm.set { it.copy(virusTotalApiKey = "") }; vt.reset(); vt.view = VtView.KEY; vm.haptic(H.SUCCESS) }
    fun scan() { scope.launch { vt.start(target, s.virusTotalApiKey, vm::hapticStr) } }
    SheetTitle("VirusTotal", Sym.Security, app.name)
    SheetToastHost(t)
    AnimatedContent(page.coerceAtMost(MOD_PAGES.size) to vt.view, Modifier.fillMaxWidth(), transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "vt") { (pg, view) ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pg < MOD_PAGES.size) ModWarning(pg) { page++; vm.haptic(H.SELECTION) }
            else when (view) {
                VtView.KEY -> VtKey(vt, s.virusTotalApiKey, ::revoke, { k -> vm.set { it.copy(virusTotalApiKey = k) } }, vm::hapticStr, vm.tracker::open)
                VtView.READY -> VtReady(target, s.virusTotalApiKey, ::scan, ::revoke)
                VtView.SCANNING -> VtScanning(vt)
                VtView.RESULTS -> vt.result?.let { r ->
                    VtResults(r, onCopy = { t.show("SHA-256 copiado") }, onReport = { r.permalink?.let(vm.tracker::open) }, onAgain = { vt.reset(); vt.view = VtView.READY }, onClose = close)
                }
                VtView.ERROR -> {
                    Lead("Atenção necessária", vt.error.ifEmpty { "Algo deu errado." }, "Verificação interrompida", cs.error) { Blob(Sym.ErrorOutline, cs.error, 72, MaterialShapes.Cookie7Sided) }
                    CtaButton("Verificar novamente", { vt.reset(); vt.view = if (s.virusTotalApiKey.isNotEmpty()) VtView.READY else VtView.KEY }, icon = Sym.Refresh, tonal = true)
                    TextButton(::revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text("Revogar chave") }
                }
            }
        }
    }
}

/** The VirusTotal flow without a sheet around it, for the APK installer: key, ready, scanning, results and errors. */
@Composable
fun VtPanel(vt: VtScan, target: ScanTarget, key: String, save: (String) -> Unit, revoke: () -> Unit, open: (String) -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    fun scan() { scope.launch { vt.start(target, key, {}) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (vt.view) {
            VtView.KEY -> VtKey(vt, key, revoke, save, {}, open)
            VtView.READY -> VtReady(target, key, ::scan, revoke)
            VtView.SCANNING -> VtScanning(vt)
            VtView.RESULTS -> vt.result?.let { r -> VtResults(r, onCopy = {}, onReport = { r.permalink?.let(open) }, onAgain = { vt.reset(); vt.view = VtView.READY }, onClose = onDone) }
            VtView.ERROR -> {
                Lead("Atenção necessária", vt.error.ifEmpty { "Algo deu errado." }, "Verificação interrompida", cs.error) { Blob(Sym.ErrorOutline, cs.error, 72, MaterialShapes.Cookie7Sided) }
                CtaButton("Verificar novamente", { vt.reset(); vt.view = if (key.isNotEmpty()) VtView.READY else VtView.KEY }, icon = Sym.Refresh, tonal = true)
            }
        }
    }
}

@Composable
private fun ModWarning(page: Int, onNext: () -> Unit) {
    var left by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val p = MOD_PAGES[page]
    Lead(p.title, p.body, "Aviso ${page + 1} de ${MOD_PAGES.size}", Caution, center = true) { Blob(p.icon, Caution, 88, MOD_SHAPES[page]) }
    LinearWavyProgressIndicator({ (10 - left) / 10f }, Modifier.fillMaxWidth(), color = Caution, trackColor = Caution.copy(.2f))
    CtaButton(if (left > 0) "Continuar em ${left}s" else if (page < MOD_PAGES.lastIndex) "Continuar" else "Entendi", onNext, enabled = left == 0)
}

@Composable
private fun VtKey(vt: VtScan, saved: String, revoke: () -> Unit, save: (String) -> Unit, haptic: (String) -> Unit, open: (String) -> Unit) {
    var key by rememberSaveable { mutableStateOf(saved) }; var show by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    Lead("Conectar ao VirusTotal", "Sua chave fica neste dispositivo. A AlphaStore só a usa quando você inicia uma verificação.", "Configuração BYOK")
    OutlinedTextField(key, { key = it; err = "" }, Modifier.fillMaxWidth(), label = { Text("Chave de API") }, placeholder = { Text("Cole sua chave de API do VirusTotal") },
        singleLine = true, isError = err.isNotEmpty(), supportingText = if (err.isNotEmpty()) ({ Text(err) }) else null, shape = RoundedCornerShape(16.dp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        leadingIcon = { Icon(Sym.Key, null) },
        trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) "Ocultar chave" else "Mostrar chave") } })
    CtaButton("Salvar chave", {
        if (key.isBlank()) { err = "Cole sua chave de API do VirusTotal primeiro."; haptic("error") }
        else { save(key.trim()); vt.view = VtView.READY; haptic("success") }
    }, icon = Sym.Save)
    if (saved.isNotEmpty()) TextButton(revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text("Remover chave salva") }
    Column {
        GroupItem(0, 2, onClick = { open(VT_KEY_TUTORIAL) }) {
            ListItem({ Text("Assistir tutorial da chave", fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("Ajuda rápida para encontrar sua chave do VirusTotal.") },
                leadingContent = { Blob(Sym.PlayCircle, Red, 40) }, trailingContent = { Icon(Sym.OpenInNew, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        GroupItem(1, 2) {
            ListItem({ Text("Dica de cota", fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("Chaves gratuitas do VirusTotal geralmente são limitadas a 4 solicitações por minuto.") },
                leadingContent = { Blob(Sym.Speed, Caution, 40) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
}

@Composable
private fun VtReady(target: ScanTarget?, key: String, scan: () -> Unit, revoke: () -> Unit) {
    val masked = if (key.length > 8) key.take(4) + "••••" + key.takeLast(4) else "••••"
    Lead("Verificar com o VirusTotal", "Primeiro consulta por hash. O envio só acontece se o VirusTotal ainda não tiver um relatório do APK.", "Pronto")
    Column {
        GroupItem(0, 2) {
            ListItem({ Text(target?.label ?: "Nenhum alvo de verificação encontrado", fontWeight = FontWeight.SemiBold) },
                supportingContent = target?.let { { Text(it.detail, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { Blob(if (target == null) Sym.Block else Sym.Android, if (target == null) cs.error else Safe, 40) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        GroupItem(1, 2) {
            ListItem({ Text(masked, fontFamily = FontFamily.Monospace) }, supportingContent = { Text("Dica de limite") }, leadingContent = { Blob(Sym.Key, cs.primary, 40) },
                trailingContent = { Tag("Use com moderação", Caution) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
    CtaButton("Iniciar verificação no VirusTotal", scan, icon = Sym.Radar, enabled = target != null)
    TextButton(revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text("Revogar chave") }
    Text("Dica: uma verificação pode precisar de chamadas de relatório, envio e consulta. Guarde sua cota gratuita para apps que você realmente pretende instalar.", style = ty.bodySmall, color = cs.onSurfaceVariant)
}

@Composable
private fun VtScanning(vt: VtScan) {
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2600); tip = (tip + 1) % VT_TIPS.size } }
    val p by animateFloatAsState(vt.progress, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "vtp")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadingIndicator(Modifier.size(112.dp))
        Text("Verificando...", style = ty.titleLargeEmphasized)
        Text(vt.note, style = ty.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(16.dp), color = cs.secondaryContainer) {
            AnimatedContent(tip, Modifier.fillMaxWidth().padding(14.dp), label = "tip") { Text(VT_TIPS[it], style = ty.bodyMedium, textAlign = TextAlign.Center) }
        }
        Text("Mantenha isso aberto. O VirusTotal pode levar um tempinho quando um APK novo precisa de análise.", style = ty.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VtResults(r: VtResult, onCopy: () -> Unit, onReport: () -> Unit, onAgain: () -> Unit, onClose: () -> Unit) {
    val clip = LocalClipboardManager.current
    val st = r.stats; val total = st.total; val rate = if (total > 0) (st.flagged * 100f / total).roundToInt() else 0
    val k = risk(st.flagged)
    val detections = r.engines.filter { it.category == "malicious" || it.category == "suspicious" }.take(8)
    Lead(k.label, k.note, "Resultado do VirusTotal", k.color, center = true) { Blob(k.icon, k.color, 88, MaterialShapes.Cookie9Sided) }
    Surface(shape = RoundedCornerShape(28.dp), color = k.color.copy(.12f)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Taxa de risco", style = ty.labelLarge, color = cs.onSurfaceVariant)
            Text("$rate%", style = ty.displayMediumEmphasized, color = k.color)
            LinearProgressIndicator({ rate / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp), color = k.color, trackColor = k.color.copy(.2f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Triple("Sinalizados", st.flagged, Red), Triple("Limpos", st.harmless, Safe), Triple("Mecanismos", total, cs.onSurface)).forEachIndexed { i, (l, v, col) ->
            Surface(Modifier.weight(1f), shape = rowShape(i, 3), color = cs.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$v", style = ty.titleLargeEmphasized, color = col)
                    Text(l.uppercase(), style = ty.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow("Como ler", cs.onSurfaceVariant)
            Text("0 detecções: geralmente seguro.", style = ty.bodyMedium)
            Text("1-4 detecções: geralmente falsos positivos.", style = ty.bodyMedium)
            Text("5+ detecções: alto cuidado.", style = ty.bodyMedium)
        }
    }
    if (detections.isNotEmpty()) Column {
        Eyebrow("Mecanismos que sinalizaram", cs.onSurfaceVariant, Modifier.padding(start = 4.dp, bottom = 8.dp))
        detections.forEachIndexed { i, e ->
            GroupItem(i, detections.size) {
                ListItem({ Text(e.engine, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(e.result ?: e.category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Tag(e.category, if (e.category == "malicious") Red else Caution) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
    if (r.hash != null) CtaButton("Copiar SHA-256", { clip.setText(AnnotatedString(r.hash)); onCopy() }, icon = Sym.ContentCopy, tonal = true)
    if (r.permalink != null) CtaButton("Abrir relatório do VirusTotal", onReport, icon = Sym.OpenInNew)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onAgain, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text("Verificar novamente") }
        TextButton(onClose, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text("Fechar") }
    }
}

// ---------------- App change request ----------------

@Composable
fun ChangeRequestSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val close = LocalSheetClose.current
    val fields = remember(app.platform) { changeFields(app.platform) }
    var key by rememberSaveable { mutableStateOf("name") }
    val f = fields.firstOrNull { it.key == key } ?: fields.first()
    var value by rememberSaveable { mutableStateOf("") }; var reason by rememberSaveable { mutableStateOf("") }; var ref by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf("form") }; var issueUrl by rememberSaveable { mutableStateOf("") }
    var err by remember { mutableStateOf("") }; var showCurrent by remember { mutableStateOf(false) }
    val current = remember(key) { currentFieldValue(app, key) }
    fun submit() {
        vm.refreshCooldown()
        vm.submissionCooldown?.let { err = "Próxima solicitação disponível em $it."; vm.haptic(H.WARNING); return }
        validateChange(f, value).takeIf { it.isNotEmpty() }?.let { err = it; vm.haptic(H.ERROR); return }
        if (ref.isNotBlank()) urlError(ref).takeIf { it.isNotEmpty() }?.let { err = "Link de evidência: $it"; vm.haptic(H.ERROR); return }
        val v: Any = if (f.kind == "urls") splitUrls(value) else value.trim()
        issueUrl = appUpdateIssueUrl(app, f.label, current, value, reason, ref, appUpdatePayloadJson(app, f.key, v))
        err = ""; view = "ready"; vm.haptic(H.SUCCESS)
    }
    if (view != "pick") SheetTitle("Atualização de app ${app.platform}", Sym.EditNote, app.name)
    AnimatedContent(view, label = "cr") { v ->
        when (v) {
            "pick" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                PickerHeader("Escolha um campo", "Corrigir um detalhe do app") { view = "form" }
                fields.groupBy { it.group }.forEach { (g, list) ->
                    Eyebrow(g, modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 8.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        list.forEachIndexed { i, x ->
                            GroupItem(i, list.size, selected = x.key == key, onClick = { if (x.key != key) { key = x.key; value = "" }; err = ""; view = "form"; vm.haptic(H.SELECTION) }) {
                                ListItem({ Text(x.label, style = if (x.key == key) ty.titleMediumEmphasized else ty.titleMedium) }, supportingContent = { Text(x.hint) },
                                    trailingContent = { if (x.key == key) Icon(Sym.CheckCircle, null, tint = cs.primary) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                            }
                        }
                    }
                }
            }
            "ready" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lead("Issue de atualização pronta", "A issue contém o JSON exato que o fluxo de atualização de apps mescla no apps.json após a revisão.", f.label, center = true) {
                    Blob(Sym.TaskAlt, Safe, 88, MaterialShapes.Cookie9Sided)
                }
                CtaButton("Abrir issue no GitHub", {
                    vm.tracker.open(issueUrl); vm.set { it.registerChangeRequest(System.currentTimeMillis()) }; vm.haptic(H.SUCCESS); close()
                }, icon = Sym.OpenInNew)
                CtaButton("Editar solicitação", { view = "form" }, icon = Sym.Edit, tonal = true)
            }
            else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Eyebrow("O que corrigir", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    GroupItem(0, 2, onClick = { view = "pick" }) {
                        ListItem({ Text(f.label, style = ty.titleMediumEmphasized) }, supportingContent = { Text(f.hint) }, trailingContent = { Icon(Sym.UnfoldMore, "Alterar campo") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    GroupItem(1, 2, onClick = { showCurrent = !showCurrent }) {
                        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Valor atual", Modifier.weight(1f), style = ty.labelLarge, color = cs.onSurfaceVariant)
                                Icon(if (showCurrent) Sym.ExpandLess else Sym.ExpandMore, null, tint = cs.onSurfaceVariant)
                            }
                            if (showCurrent) Text(current.take(1000), Modifier.padding(top = 6.dp), style = ty.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                val multi = f.kind == "textarea" || f.kind == "urls"; val urlKind = f.kind == "url" || f.kind == "urls"
                OutlinedTextField(value, { value = it.take(4000); err = "" }, Modifier.fillMaxWidth(), label = { Text("Valor correto") }, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(if (f.kind == "urls") "https://image.example/one.png\nhttps://image.example/two.png" else f.hint) },
                    singleLine = !multi, minLines = if (multi) 4 else 1, keyboardOptions = KeyboardOptions(keyboardType = if (urlKind) KeyboardType.Uri else KeyboardType.Text))
                OutlinedTextField(reason, { reason = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Motivo") }, placeholder = { Text("Por que essa correção é necessária?") },
                    minLines = 3, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(ref, { ref = it; err = "" }, Modifier.fillMaxWidth(), label = { Text("Link de evidência (opcional)") }, placeholder = { Text("Página de lançamento ou fonte oficial") },
                    singleLine = true, shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Sym.Link, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                AnimatedVisibility(err.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.errorContainer, contentColor = cs.onErrorContainer) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Sym.ErrorOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(err, style = ty.bodyMedium)
                        }
                    }
                }
                CtaButton("Preparar issue de atualização", ::submit, icon = Sym.Description)
                Text("+50 XP para o ranking", Modifier.fillMaxWidth(), style = ty.labelLarge, color = cs.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------------- 2.0 welcome ----------------

private val WELCOME_POINTS = listOf(
    Triple(Sym.Speed, "Velocidade nativa", "Feita com Jetpack Compose, sem web view à vista"),
    Triple(Sym.Palette, "Material 3 Expressive", "Movimento elástico, morphing de formas e Material You"),
    Triple(Sym.Bolt, "Sem espera de 24 horas", "Instalação sem fio integrada, configure uma vez"),
    Triple(Sym.MilitaryTech, "Níveis e cards de piloto", "Ganhe XP e suba de Bronze, Prata e Ouro"),
)

/** One-time hero for the native switch, shown after updating over the web app and on a fresh install alike. */
@Composable
fun WelcomeSheet(vm: MainViewModel) = AlphaStoreSheet(vm::finishWelcome) {
    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography; val close = LocalSheetClose.current
    val still = LocalS.current.calm
    var k by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) { delay(250); k = 1; while (!still) { delay(2200); k++ } }
    val pulse by rememberInfiniteTransition(label = "re").animateFloat(1f, if (still) 1f else 1.08f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter) {
            MorphBlob(listOf(MaterialShapes.Circle, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst, MaterialShapes.Flower), k, 168.dp, cs.primaryContainer) {
                Icon(androidx.compose.ui.res.painterResource(com.ramonmods.alphastore.R.drawable.ic_splash_logo), null, Modifier.size(84.dp), tint = cs.onPrimaryContainer)
            }
            Surface(Modifier.offset(y = 10.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, shape = Pill, color = cs.tertiary, contentColor = cs.onTertiary) {
                Text("RE", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = ty.labelLargeEmphasized, letterSpacing = 3.sp)
            }
        }
        Text("AlphaStore 2.0 · Nativa", Modifier.padding(top = 28.dp).reveal(0), style = ty.labelLarge, color = cs.primary)
        Text("Finalmente nativa", Modifier.reveal(1), style = ty.displaySmallEmphasized, textAlign = TextAlign.Center)
        Text("O app web que você conhecia foi reconstruído do zero como um app Android de verdade. Mesma AlphaStore, motor totalmente novo.",
            Modifier.padding(top = 8.dp).reveal(2), style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WELCOME_POINTS.forEachIndexed { i, (icon, title, desc) ->
                ListItem({ Text(title, style = ty.titleMedium) }, Modifier.reveal(3 + i).clip(colShape(i, WELCOME_POINTS.size)),
                    supportingContent = { Text(desc) }, leadingContent = { TonalIcon(icon, cs.primary) },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer))
            }
        }
        AnimatedVisibility(vm.migratedFromWeb) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.tertiaryContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.CheckCircle, null, tint = cs.onTertiaryContainer); Spacer(Modifier.width(12.dp))
                Text("Seu perfil, emblemas e configurações vieram junto do app web.", style = ty.bodyMedium, color = cs.onTertiaryContainer)
            }
        }
        CtaButton("Vamos lá", close, Modifier.padding(top = 20.dp).reveal(8), icon = Sym.RocketLaunch)
    }
}

/** Every catalog app by one developer: a shape avatar, the name, the count, then the apps. */
@Composable
fun DevSheet(vm: MainViewModel, dev: String, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val d = LocalD.current; val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography
    val name = dev.trim()
    val apps = remember(d.apps, d.importedApps, name) {
        (d.apps + d.importedApps).filter { it.author.trim().equals(name, true) }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }
    val letter = name.takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))) }.orEmpty().uppercase().ifBlank { "?" }
    val cap = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = cap), contentPadding = PaddingValues(bottom = 32.dp)) {
        item("dev") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(letter, style = ty.displaySmallEmphasized, color = cs.onPrimaryContainer)
                }
                Text(dev, Modifier.padding(top = 14.dp, start = 24.dp, end = 24.dp), style = ty.headlineSmallEmphasized, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${apps.size} app${if (apps.size == 1) "" else "s"}", style = ty.labelLarge, color = cs.onSurfaceVariant)
            }
        }
        itemsIndexed(apps, key = { _, a -> a.id }) { i, a -> AppRow(vm, a, i, apps.size, Modifier.animateItem()) }
    }
}

/** The pm install switches this Android version supports, shared by the store's options sheet and the APK installer sheet.
 *  [suggested] ones carry a "Needed" tag: the APK was read and won't install without them. */
@Composable
fun InstallOptionsList(on: Set<String>, installer: String, onChange: (Set<String>) -> Unit, onInstaller: (String) -> Unit, suggested: Set<String> = emptySet(), haptic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shown = remember { INSTALL_OPTIONS.filter { android.os.Build.VERSION.SDK_INT >= it.minSdk } }
    shown.forEachIndexed { i, o ->
        val c = o.id in on
        fun flip() { haptic(); onChange(if (c) on - o.id else on + o.id - setOfNotNull(o.excludes)) }
        SegmentedListItem(::flip, ListItemDefaults.segmentedShapes(i, shown.size),
            colors = ListItemDefaults.segmentedColors(containerColor = if (c) cs.secondaryContainer else cs.surfaceContainerHigh),
            supportingContent = { Text(o.desc) },
            trailingContent = { Switch(c, { flip() }, thumbContent = { Icon(if (c) Sym.Check else Sym.Close, null, Modifier.size(SwitchDefaults.IconSize)) }) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.label, Modifier.weight(1f, false), style = MaterialTheme.typography.titleMedium)
                if (o.id in suggested) Text("Necessário", Modifier.padding(start = 8.dp).clip(Pill).background(cs.tertiaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = cs.onTertiaryContainer)
            }
        }
        if (o.id == "installer" && c) OutlinedTextField(installer, { onInstaller(it.trim()) }, Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true,
            label = { Text("Pacote do instalador") }, placeholder = { Text("com.sec.android.app.samsungapps") }, shape = RoundedCornerShape(16.dp),
            isError = installer.isNotEmpty() && installArgs(listOf("installer", "installer=$installer"), android.os.Build.VERSION.SDK_INT, "").contains(" -i ").not(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    }
}

/** The picks as saved and passed to pm: switch ids, plus "installer=<package>" when a custom installer is on. */
fun pickedOptions(on: Set<String>, installer: String) =
    on.filter { id -> INSTALL_OPTIONS.any { it.id == id && android.os.Build.VERSION.SDK_INT >= it.minSdk } } + listOfNotNull(installer.takeIf { "installer" in on && it.isNotEmpty() }?.let { "installer=$it" })

/** Beyond install: pm install switches for one APK, last picks preselected. Installs through root or Shizuku. */
@Composable
fun InstallOptionsSheet(vm: MainViewModel, app: AppItem, file: String, onDismiss: () -> Unit) = AlphaStoreSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current; val cs = MaterialTheme.colorScheme
    var on by remember { mutableStateOf(s.installFlags.filterNot { it.startsWith("installer=") }.toSet()) }
    var installer by remember { mutableStateOf(s.installFlags.firstOrNull { it.startsWith("installer=") }?.substringAfter('=').orEmpty()) }
    SheetTitle("Além da instalação", Sym.Tune, app.name)
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        InstallOptionsList(on, installer, { on = it }, { installer = it }) { vm.haptic(H.SELECTION) }
        Text(if (s.rootMode) "Instala com root." else "Instala pelo Shizuku, que precisa estar em execução.", Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Button({
            val picked = pickedOptions(on, installer)
            vm.set { it.copy(installFlags = picked) }
            vm.installFile(app, file, opts = picked); close()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight)) {
            Icon(Sym.InstallMobile, null); Spacer(Modifier.width(10.dp)); Text("Instalar", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}
