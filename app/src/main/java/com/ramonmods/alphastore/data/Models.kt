package com.ramonmods.alphastore.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true; encodeDefaults = true }

object Platform { const val ANDROID = "Android"; const val PC = "PC"; const val TV = "TV" }

/** What this device runs, read once at process start before any screen or background job asks. */
/** [lowEnd]: a low-RAM phone, under 4 GB, where AlphaStore starts in Lite performance mode. */
object Device { @Volatile var isTv = false; @Volatile var lowEnd = false }

/** Android apps install everywhere; TV apps install natively too when AlphaStore runs on a TV (beta). */
fun AppItem.installsHere() = platform == Platform.ANDROID || (platform == Platform.TV && Device.isTv)

enum class SortOption(val label: String) {
    HOME("Início"), NEWEST("Mais Recentes"), OLDEST("Mais Antigos"), NAME_ASC("Nome (A-Z)"),
    NAME_DESC("Nome (Z-A)"), SIZE_ASC("Tamanho (Menor)"), SIZE_DESC("Tamanho (Maior)")
}

enum class Tab(val platform: String?) { ANDROID(Platform.ANDROID), PC(Platform.PC), TV(Platform.TV), ORBIT(null), MYAPPS(null), ABOUT(null), UPDATES(null) }

val STREAMS = listOf("Stable", "Beta", "Alpha", "Nightly")

@Serializable data class AppVariant(val arch: String = "", val url: String = "", val size: Long? = null, val name: String = "")

/** [tag]: the release tag it came from, used to fetch its release notes. */
@Serializable data class VersionOption(val type: String = "Stable", val version: String = "", val date: String = "", val variants: List<AppVariant> = emptyList(), val tag: String = "")

@Serializable data class AppItem(
    val id: String = "",
    val name: String = "App Desconhecido",
    val description: String = "",
    val icon: String = "",
    val version: String = "Latest",
    val latestVersion: String = "Latest",
    val downloadUrl: String = "#",
    val variants: List<AppVariant> = emptyList(),
    val availableVersions: List<VersionOption> = emptyList(),
    val repoUrl: String? = null,
    val githubRepo: String? = null,
    val gitlabRepo: String? = null,
    val codebergRepo: String? = null,
    val gitlabDomain: String? = null,
    val releaseKeyword: String? = null,
    val packageName: String? = null,
    val category: String = "Utility",
    val tags: List<String> = emptyList(),
    val platform: String = Platform.ANDROID,
    val size: String = "",
    val author: String = "Unknown",
    val screenshots: List<String> = emptyList(),
    val officialSite: String? = null,
    val patches: List<String> = emptyList(),
    /** GitHub stars from the data branch's stars.json, null when unknown. */
    val stars: Int? = null,
    /** Times the release files were downloaded, from the host, when it reports a count. */
    val downloadCount: Long? = null,
)

@Serializable data class SocialLinks(val github: String = "", val x: String = "", val discord: String = "", val coffee: String = "")
@Serializable data class DonationConfig(val kofiUrl: String? = null, val upiId: String? = null, val paypalUrl: String? = null)
@Serializable data class DevProfile(val name: String = "", val image: String? = null, val bio: String = "")
@Serializable data class FAQItem(val question: String = "", val answer: String = "", val icon: String = "")
@Serializable data class Notice(val id: String = "", val title: String = "", val message: String = "", val show: Boolean = false)
@Serializable data class PillConfig(val id: String? = null, val label: String? = null, val appId: String? = null, val tone: String? = null)
@Serializable data class ModuleBundleConfig(val id: String = "", val title: String = "", val description: String? = null, val appIds: List<String> = emptyList(), val color: String? = null, val badge: String? = null, val icon: String? = null, val monogram: String? = null)
@Serializable data class ModuleConfig(
    val id: String = "", val type: String = "", val title: String = "", val subtitle: String? = null, val platform: String? = null,
    val insertAfterCategory: Double? = null, val animation: String? = null, val appIds: List<String>? = null,
    val pills: List<PillConfig>? = null, val bundles: List<ModuleBundleConfig>? = null,
)

@Serializable data class StoreConfig(
    val appsJsonUrl: String = "",
    val mirrorJsonUrl: String? = null,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String? = null,
    val announcement: String? = null,
    val notice: Notice? = null,
    val minStoreVersion: String? = null,
    val latestStoreVersion: String? = null,
    val storeDownloadUrl: String? = null,
    val socials: SocialLinks? = null,
    val donation: DonationConfig? = null,
    val devProfile: DevProfile? = null,
    val faqs: List<FAQItem>? = null,
    val supportEmail: String? = null,
    val easterEggUrl: String? = null,
    val leaderboardUrl: String? = null,
    val storefrontModules: List<ModuleConfig>? = null,
)

@Serializable data class LeaderboardEntry(val username: String = "", val xp: Long = 0, val level: Int = 0, val title: String = "", val `class`: String = "Hybrid", val avatar_url: String = "", val rank: Int? = null)

data class BundleItem(val id: String, val title: String, val description: String, val appIds: List<String>, val apps: List<AppItem> = emptyList(), val color: String? = null, val badge: String? = null, val icon: String? = null, val monogram: String? = null)
data class CategoryCard(val id: String, val label: String, val icon: String, val count: Int, val accent: String, val gradient: List<String>)

enum class CollectionType { HERO, SWIMLANE, BUNDLE, RECOMMENDATION_BUNDLES, CATEGORY_CARDS, SORTED_GRID, RANKED }

data class StoreCollection(
    val id: String, val title: String, val type: CollectionType, val subtitle: String? = null, val filter: String? = null,
    val animation: String? = null, val apps: List<AppItem> = emptyList(), val bundles: List<BundleItem> = emptyList(),
    val categoryCards: List<CategoryCard> = emptyList(), val totalAppCount: Int? = null,
)

object Defaults {
    const val CACHE_VERSION = "v1_7"
    const val NETWORK_TIMEOUT_MS = 8000L
    const val RELEASES_API = "https://api.github.com/repos/RamonMods/AlphaStore/releases?per_page=8"
    const val MIRROR_JSON = "https://raw.githubusercontent.com/RamonMods/AlphaStore/data/mirror.json"
    /** owner/repo (lowercase) to GitHub stars, built by the mirror workflow so the app never spends API calls on it. */
    const val STARS_JSON = "https://raw.githubusercontent.com/RamonMods/AlphaStore/data/stars.json"
    /** GitHub first, then two CDNs that serve the same files for networks blocking raw.githubusercontent.com. */
    val CONFIG_URLS = listOf(
        "https://raw.githubusercontent.com/RamonMods/AlphaStore/main/config.json",
        "https://cdn.jsdelivr.net/gh/RamonMods/AlphaStore@main/config.json",
        "https://raw.githack.com/RamonMods/AlphaStore/main/config.json",
    )
    val APPS_URLS = CONFIG_URLS.map { it.replace("config.json", "apps.json") }
    val MIRROR_LABELS = listOf("GitHub", "JSDelivr", "GitHack")
    val MICROG_DEPENDENT_APPS = setOf("youtube-revanced", "yt-music-revanced", "google-photos-revanced")
    const val MICROG_INFO_URL = "https://github.com/microg/GmsCore/wiki"
    val DEV_SOCIALS = SocialLinks("https://github.com/RamonMods", "https://github.com/RamonMods", "https://github.com/RamonMods/AlphaStore/discussions", "https://pixgg.com/ramon")
    val DONATION = DonationConfig("https://pixgg.com/ramon", "contato@ramonmods.com", "paypal.me/RamonMods")
    val DEV_PROFILE = DevProfile("RamonMods", "https://i.pinimg.com/originals/12/79/48/127948a3253396796874286570740594.jpg", "A AlphaStore é o futuro, um commit de cada vez. Sem anúncios, sem rastreamento, só código.")
    const val SUPPORT_EMAIL = "contato@ramonmods.com"
    const val EASTER_EGG = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    const val SELF_PACKAGE = "com.ramonmods.alphastore"
    const val UNITY_GAME_ID = "5996387"
    val PACKAGE_SUFFIXES = listOf(".preview", ".debug", ".test", ".beta", ".canary", ".dev", ".alpha", ".nightly", ".staging", ".release")
    val FONTS = listOf(
        "spaceGrotesk" to "Space Grotesk", "inter" to "Inter", "poppins" to "Poppins", "manrope" to "Manrope", "outfit" to "Outfit",
        "dmSans" to "DM Sans", "plusJakartaSans" to "Plus Jakarta Sans", "rubik" to "Rubik", "nunitoSans" to "Nunito Sans",
        "publicSans" to "Public Sans", "systemDefault" to "System Default",
    )
    val PTR_CHARACTERS = listOf("cat", "dog", "pokeball", "shield", "owl", "robot", "ghost", "kitty", "bunny", "batman")
    val FAQS = listOf(
        FAQItem("Por que apps de verificação dizem que a AlphaStore tem rastreadores?", "Apps de verificação como o AppManager sinalizam o SDK do Unity Ads dentro da AlphaStore. Esse SDK existe por exatamente um motivo: os anúncios opcionais da aba Sobre (Missions), onde assistir aos anúncios apoia o projeto e rende XP e insígnias. Não há análise de dados, mineração ou venda de dados. O app é open source, então qualquer pessoa pode verificar, e os anúncios são 100% opcionais, sem nada rodando em segundo plano.", "fa-magnifying-glass"),
        FAQItem("A AlphaStore é segura?", "Com certeza. A AlphaStore é totalmente open source. O código é público no GitHub para qualquer pessoa auditar. Acreditamos em transparência: sem rastreadores escondidos, sem mineração de dados, só uma porta de entrada limpa para os apps.", "fa-shield-cat"),
        FAQItem("Os apps da AlphaStore são seguros?", "Sim. Eu reviso e modifico pessoalmente cada app usando ferramentas disponíveis nos repositórios oficiais, para garantir que sejam seguros, funcionais e respeitem a privacidade antes de entrar na loja.", "fa-check-double"),
        FAQItem("Os builds do ReVanced são seguros?", "Sim. Esses builds são automatizados por bots do GitHub Actions, não são patchados manualmente. Eles usam o CLI e os patches oficiais do ReVanced em APKs obtidos do APKMirror, Uptodown e APKPure. O processo roda todos os dias às 6h. Toda a pipeline de build é transparente e open source: https://github.com/RamonMods/Revanced-AutoBuilds", "fa-robot"),
        FAQItem("A instalação falhou?", "Se aparecer \"App não instalado\" ou \"Conflito de pacote\", provavelmente é incompatibilidade de assinatura. Não dá para instalar um app modificado por cima da versão original da Play Store ou de outro mod. Desinstale o app existente primeiro e depois instale o da AlphaStore.", "fa-file-signature"),
        FAQItem("O download não funciona?", "Não entre em pânico! Vá à página do app e toque no ícone de relatar (⚠️) no canto superior direito. Vai abrir um e-mail já preenchido para eu corrigir o mais rápido possível.", "fa-bug"),
        FAQItem("Vão ter mais apps?", "Sim, sempre que houver apps interessantes para adicionar. Enquanto eu encontrar ferramentas open source ou úteis que mereçam destaque, a biblioteca vai continuar crescendo.", "fa-layer-group"),
        FAQItem("Como posso apoiar?", "Com uma doação via Pix. Código move a loja, mas café move o desenvolvedor! O link está na seção de redes sociais.", "fa-heart"),
        FAQItem("Tem algum easter egg escondido?", "Onde o Arquiteto olha, o segredo dorme.\n\nConte as pernas de uma aranha. Conte os vértices de um cubo.\n\nToque na Visagem essa quantidade de vezes.\n\nA Verdade Dourada espera por quem conhece as regras... e eu também.", "fa-user-secret"),
    )
}
