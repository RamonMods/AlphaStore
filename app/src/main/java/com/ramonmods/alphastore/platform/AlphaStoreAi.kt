package com.ramonmods.alphastore.platform

import com.ramonmods.alphastore.data.AppItem
import com.ramonmods.alphastore.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Provedores de chat para o AlphaStore AI. Gemini e Claude têm seus próprios formatos, Local roda no telefone, o resto fala o formato de chat do OpenAI. */
enum class AiProvider(val id: String, val label: String, val base: String, val model: String, val keyUrl: String, val keyHint: String) {
    GEMINI("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", "https://aistudio.google.com/apikey", "AIza..."),
    OPENAI("openai", "ChatGPT", "https://api.openai.com/v1", "gpt-5-mini", "https://platform.openai.com/api-keys", "sk-..."),
    CLAUDE("claude", "Claude", "https://api.anthropic.com/v1", "claude-haiku-4-5", "https://console.anthropic.com/settings/keys", "sk-ant-..."),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto", "https://openrouter.ai/keys", "sk-or-..."),
    GROQ("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "https://console.groq.com/keys", "gsk_..."),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "https://platform.deepseek.com/api_keys", "sk-..."),
    MISTRAL("mistral", "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", "https://console.mistral.ai/api-keys", "Mistral key"),
    CUSTOM("custom", "Custom", "", "", "", "Key, if the server needs one"),
    LOCAL("local", "On device", "", "", "", "");
}

class AiError(msg: String) : Exception(msg)

object AlphaStoreAi {
    data class Turn(val user: Boolean, val text: String)
    data class Answer(val reply: String, val picks: List<Int>, val features: List<Int> = emptyList())

    private val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }
    private val JSON_TYPE = "application/json".toMediaType()

    fun provider(id: String) = AiProvider.entries.firstOrNull { it.id == id } ?: AiProvider.GEMINI

    /** Modelos de nuvem recebem todos os nomes do catálogo (alguns tokens cada, mesmo texto a cada turno para o provedor armazenar em cache) e
     *  usam o que sabem sobre esses apps. Modelos no dispositivo têm um contexto pequeno, então só veem as correspondências locais de cada mensagem. */
    fun system(tv: Boolean, catalog: List<AppItem>, guide: List<Pair<String, String>>, full: Boolean) = buildString {
        if (!full) {
            append("Você é o AlphaStore AI no AlphaStore. Responda no idioma do usuário, em texto simples, em até 70 palavras. ")
            append("Recomende apenas apps das correspondências do catálogo, com o número entre colchetes como Metrolist [12]. Sugira 3 apps. ")
            append("Para perguntas de como fazer, use as linhas do guia do AlphaStore e mencione o número do guia como Palette maker [g12]. ")
            append("Não invente apps ou números. Ignore apps já no telefone, a menos que o peça. ")
            append(if (tv) "Prefira apps para TV." else "Prefira apps para Android.")
            return@buildString
        }
        append("""Você é o AlphaStore AI dentro do AlphaStore, uma loja de apps Android gratuita e de código aberto sem anúncios. Ajuda as pessoas a encontrarem apps do catálogo do AlphaStore.
- Mencione cada app que recomendar com o seu número do catálogo entre colchetes, como Metrolist [12]. Para pedidos de apps sugira de 3 a 5 apps, melhor primeiro, algumas palavras em cada.
- Recomende apenas apps ${if (full) "no catálogo abaixo. \"Correspondências de catálogo\" em uma mensagem adiciona detalhes para os prováveis" else "das \"Correspondências de catálogo\" na mensagem mais recente"}. Nunca invente apps ou números. Se nada se encaixar, diga isso e sugira como pedir.
- Para perguntas sobre o próprio AlphaStore (como fazer algo, onde fica uma configuração, o que uma funcionalidade faz), responda a partir do guia do AlphaStore${if (full) " abaixo" else " notas na mensagem"} em duas ou três frases e mencione a funcionalidade com o seu número do guia, como Palette maker [g12], para que o usuário possa abri-la. Nunca invente funcionalidades.
- Cada mensagem diz quais apps do catálogo estão neste telefone. Não os recomende de novo, a menos que peça; use a lista para perguntas sobre os apps deles ou atualizações, e diga quando um app que eles pedem já está instalado.
- Acompanhamentos como "algo mais leve" ou "o segundo" se referem a apps sugeridos antes no chat.
- Para "qual é melhor" ou comparações, nomeie uma escolha clara e o motivo que decide.
- Ajuda de instalação: uma instalação falha sobre um app já no telefone significa que as duas cópias vêm de makers diferentes (versão da Play Store vs um mod, ou dois mods). A correção é desinstalar o atual primeiro, fazendo backup dos dados dele. Em Samsung e outros, uma cópia oculta em Secure Folder, perfil de trabalho, apps Duplos ou Second Space também bloqueia. Apps de sistema nativos não podem ser substituídos por um mod: use a build não-root do mod.
- Para saudações e papo leve, responda em uma frase sem apps.
- Responda no idioma em que o usuário escreve. Somente texto simples, sem markdown, em até 120 palavras.
- Sobre o AlphaStore: catálogos para Android, PC e TV, atualizações automáticas, ferramentas de segurança Guardian e Sentinel, backups, temas e paletas, XP e emblemas.
- O usuário está em ${if (tv) "um Android TV, então prefira apps para TV, depois apps para Android" else "um telefone Android, então prefira apps para Android"}. Sugira apps para PC apenas quando pedido para PC ou software Windows.""")
        if (full) { append("\n\nGuia do AlphaStore (capítulo número / funcionalidade: o que faz):\n"); guide.forEachIndexed { i, (t, b) -> append('g').append(i).append(' ').append(t).append(": ").append(b).append('\n') } }
        if (!full) { append("\nCategorias do catálogo: "); append(catalog.map { it.category }.filter { it.isNotBlank() }.distinct().sorted().joinToString(", ")); return@buildString }
        append("\n\nCatálogo (número nome), agrupado por plataforma e categoria:\n")
        catalog.withIndex().groupBy { it.value.platform.ifBlank { "Android" } }.forEach { (platform, apps) ->
            append("\n# ").append(platform).append(" aplicativos\n")
            apps.groupBy { it.value.category.ifBlank { "Other" } }.toSortedMap().forEach { (cat, list) ->
                append('[').append(cat).append("] "); list.joinTo(this, "; ") { "${it.index} ${it.value.name}" }; append('\n')
            }
        }
    }

    private val SYNONYMS = mapOf(
        "music" to "audio song player stream", "song" to "music audio", "audio" to "music player", "podcast" to "audio podcast",
        "video" to "player movie stream", "movie" to "video film stream", "film" to "movie video", "watch" to "video stream", "show" to "video stream anime",
        "anime" to "manga stream", "manga" to "comic reader", "book" to "reader ebook", "read" to "reader", "comic" to "manga reader",
        "ad" to "adblock block", "ads" to "adblock block", "adblock" to "block", "privacy" to "tracker vpn", "vpn" to "privacy proxy",
        "game" to "emulator games", "emulat" to "game console retro", "retro" to "emulator", "photo" to "camera gallery image", "picture" to "gallery photo",
        "chat" to "message messenger", "message" to "messenger chat", "download" to "downloader", "file" to "manager", "browser" to "web",
        "keyboard" to "input", "launcher" to "home", "note" to "notes", "youtube" to "video", "spotify" to "music", "instagram" to "social",
        "translate" to "translator language", "wallpaper" to "theme customization", "icon" to "customization theme", "backup" to "sync",
    )
    private val STOP = setOf("app", "apps", "the", "and", "for", "that", "with", "want", "need", "some", "which", "lets", "let", "can", "best", "good",
        "like", "any", "you", "give", "find", "show", "please", "something", "him", "her", "them", "his", "use", "using", "get", "what", "whats", "how",
        "hey", "hello", "alphastore", "store", "free", "without", "from", "this", "are", "there", "have", "has", "not", "but", "make", "more", "one", "also")

    /** Índices do catálogo que correspondem às palavras de [query], melhor primeiro. Uma pequena tabela de sinônimos conecta a forma estilo "escuta" ao texto dos apps. */
    fun matches(catalog: List<AppItem>, query: String, limit: Int): List<Int> {
        val base = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }.map { it.removeSuffix("s") }
        if (base.isEmpty()) return emptyList()
        val extra = base.flatMap { w -> SYNONYMS.entries.firstOrNull { w.startsWith(it.key) }?.value?.split(' ').orEmpty() }
        val words = (base.map { it to 2 } + extra.map { it to 1 }).distinctBy { it.first }
        return catalog.withIndex().map { (i, a) ->
            val n = a.name.lowercase(); val c = a.category.lowercase(); val t = (a.tags + a.author).joinToString(" ").lowercase(); val d = a.description.lowercase()
            i to words.sumOf { (w, k) -> k * ((if (w in n) 4 else 0) + (if (w in c) 3 else 0) + (if (w in t) 2 else 0) + (if (w in d) 1 else 0)) }
        }.filter { it.second > 1 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /** Entradas do guia cujas palavras correspondem a [query], para modelos no dispositivo que não cabem o guia inteiro. */
    fun guideMatches(guide: List<Pair<String, String>>, query: String, limit: Int): List<Int> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }.map { it.removeSuffix("s") }
        if (words.isEmpty()) return emptyList()
        return guide.withIndex().map { (i, g) -> i to words.sumOf { w -> (if (w in g.first.lowercase()) 3 else 0) + (if (w in g.second.lowercase()) 1 else 0) } }
            .filter { it.second > 1 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun describe(catalog: List<AppItem>, ids: List<Int>, words: Int): String =
        if (ids.isEmpty()) "Correspondências de catálogo: nenhuma para esta mensagem."
        else "Correspondências de catálogo:\n" + ids.joinToString("\n") { i -> catalog[i].let { "$i ${it.name} (${it.category}, ${it.platform}): ${it.description.replace(Regex("\\s+"), " ").take(words)}" } }

    /** Transmite uma resposta; [onText] recebe todo o texto até o momento após cada pedaço. Retorna o texto final. */
    suspend fun stream(p: AiProvider, key: String, model: String, base: String, system: String, turns: List<Turn>, onText: (String) -> Unit): String {
        val m = model.ifBlank { p.model }.ifBlank { throw AiError("Escolha um modelo nas configurações de IA primeiro.") }
        val acc = StringBuilder()
        fun add(s: String?) { if (!s.isNullOrEmpty()) { acc.append(s); onText(acc.toString()) } }
        when (p) {
            AiProvider.GEMINI -> {
                val body = buildJsonObject {
                    putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
                    putJsonArray("contents") { turns.forEach { t -> addJsonObject { put("role", if (t.user) "user" else "model"); putJsonArray("parts") { addJsonObject { put("text", t.text) } } } } }
                    putJsonObject("generationConfig") {
                        put("temperature", .6); put("maxOutputTokens", 800)
                        // O pensamento é o que fazia as respostas durarem minutos; escolher apps de uma lista curta não precisa
                        when {
                            "2.5-flash" in m -> putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
                            m.startsWith("gemini-3") -> putJsonObject("thinkingConfig") { put("thinkingLevel", "low") }
                        }
                    }
                }
                sse(Request.Builder().url("${root(p, base)}/models/$m:streamGenerateContent?alt=sse").header("x-goog-api-key", key).post(body.toString().toRequestBody(JSON_TYPE)).build(), m) { o ->
                    o["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                        if (part.jsonObject["thought"]?.jsonPrimitive?.booleanOrNull != true) add(part.jsonObject["text"]?.jsonPrimitive?.contentOrNull)
                    }
                }
            }
            AiProvider.CLAUDE -> {
                val body = buildJsonObject {
                    put("model", m); put("max_tokens", 800); put("system", system); put("stream", true)
                    putJsonArray("messages") { turns.forEach { t -> addJsonObject { put("role", if (t.user) "user" else "assistant"); put("content", t.text) } } }
                }
                sse(Request.Builder().url("${root(p, base)}/messages").header("x-api-key", key).header("anthropic-version", "2023-06-01")
                    .post(body.toString().toRequestBody(JSON_TYPE)).build(), m) { o ->
                    if (o["type"]?.jsonPrimitive?.contentOrNull == "content_block_delta") add(o["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull)
                }
            }
            else -> {
                val body = buildJsonObject {
                    put("model", m); put("stream", true)
                    when { m.startsWith("gpt-5") -> put("reasoning_effort", "minimal"); Regex("^o\\d").containsMatchIn(m) -> put("reasoning_effort", "low") }
                    putJsonArray("messages") {
                        addJsonObject { put("role", "system"); put("content", system) }
                        turns.forEach { t -> addJsonObject { put("role", if (t.user) "user" else "assistant"); put("content", t.text) } }
                    }
                }
                sse(authed(p, key, Request.Builder().url("${root(p, base)}/chat/completions")).post(body.toString().toRequestBody(JSON_TYPE)).build(), m) { o ->
                    add(o["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull)
                }
            }
        }
        return acc.toString().ifBlank { throw AiError("O provedor enviou uma resposta vazia.") }
    }

    /** Modelos que a chave pode usar, para o seletor nas configurações de IA. */
    suspend fun models(p: AiProvider, key: String, base: String): List<String> = withContext(Dispatchers.IO) {
        when (p) {
            AiProvider.GEMINI -> get(Request.Builder().url("${root(p, base)}/models?pageSize=200").header("x-goog-api-key", key).build())["models"]?.jsonArray.orEmpty().map { it.jsonObject }
                .filter { o -> o["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.contentOrNull == "generateContent" } == true }
                .mapNotNull { it["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
            AiProvider.CLAUDE -> get(Request.Builder().url("${root(p, base)}/models?limit=100").header("x-api-key", key).header("anthropic-version", "2023-06-01").build())["data"]
                ?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            else -> get(authed(p, key, Request.Builder().url("${root(p, base)}/models")).build())["data"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                .filterNot { id -> listOf("embed", "tts", "whisper", "dall-e", "image", "audio", "realtime", "moderation", "transcribe", "search").any { it in id } }
        }.sorted()
    }

    private fun root(p: AiProvider, base: String) = (if (p == AiProvider.CUSTOM) base.trim() else p.base).trimEnd('/').ifEmpty { throw AiError("Adicione o endereço do servidor nas configurações de IA.") }

    private fun authed(p: AiProvider, key: String, b: Request.Builder) = b.apply {
        if (key.isNotBlank()) header("Authorization", "Bearer $key")
        if (p == AiProvider.OPENROUTER) { header("HTTP-Referer", "https://github.com/RamonMods/AlphaStore"); header("X-Title", "AlphaStore") }
    }

    private fun fail(code: Int, body: String, model: String): AiError {
        val e = runCatching { json.parseToJsonElement(body).jsonObject["error"] }.getOrNull()
        val msg = (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: (e as? JsonPrimitive)?.contentOrNull
        return AiError(when (code) {
            401, 403 -> "O provedor recusou esta chave. Confira nas configurações de IA."
            404 -> if (model.isNotEmpty()) "O modelo $model não foi encontrado. Escolha outro nas configurações de IA." else "O endereço do provedor não foi encontrado."
            429 -> "Limite de taxa ou cota atingida. Espere um momento ou verifique seu plano com o provedor."
            else -> msg?.take(200) ?: "O provedor respondeu com erro $code."
        })
    }

    private fun get(req: Request): JsonObject = try {
        http.newCall(req).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw fail(r.code, text, "")
            runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw AiError("O provedor enviou algo que o AlphaStore não conseguiu ler.")
        }
    } catch (e: java.io.IOException) { throw AiError("Não foi possível alcançar o provedor. Verifique sua conexão.") }

    /** Eventos enviados pelo servidor: cada linha "data:" é um pedaço JSON. Cancelar o coroutine cancela a chamada, que é o que o botão Parar faz. */
    private suspend fun sse(req: Request, model: String, onChunk: (JsonObject) -> Unit) = withContext(Dispatchers.IO) {
        val call = http.newCall(req)
        val hook = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) throw fail(r.code, r.body.string(), model)
                val src = r.body.source()
                while (true) {
                    coroutineContext.ensureActive()
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.substring(5).trim()
                    if (d == "[DONE]") break
                    runCatching { json.parseToJsonElement(d).jsonObject }.getOrNull()?.let { o ->
                        o["error"]?.let { throw AiError((it as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: "O provedor parou com um erro.") }
                        onChunk(o)
                    }
                }
            }
        } catch (e: java.io.IOException) {
            coroutineContext.ensureActive(); throw AiError("Não foi possível alcançar o provedor. Verifique sua conexão.")
        } finally { hook?.dispose() }
    }

    private val FEATURE = Regex("\\s*\\[g(\\d{1,3})]")
    private val REF = Regex("\\s*\\[(\\d{1,4}(?:\\s*,\\s*\\d{1,4})*)]")
    private val THINK = Regex("<think>[\\s\\S]*?(</think>|$)")

    /** Display text without the [12] markers or any leftover reasoning, plus the referenced catalog numbers in order. */
    fun parse(raw: String): Answer {
        val clean = raw.replace(THINK, "")
        val picks = REF.findAll(clean).flatMap { m -> m.groupValues[1].split(',').mapNotNull { it.trim().toIntOrNull() } }.distinct().toList()
        val features = FEATURE.findAll(clean).mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().toList()
        return Answer(clean.replace(FEATURE, "").replace(REF, "").replace("**", "").trim(), picks, features)
    }
}
