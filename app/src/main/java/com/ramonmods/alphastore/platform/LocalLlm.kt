package com.ramonmods.alphastore.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Modelos no dispositivo para o AlphaStore AI: builds LiteRT-LM do litert-community do Hugging Face, baixados pelo downloader próprio do AlphaStore
 *  (retomáveis, continuam em segundo plano) e rodando na CPU. */
object LocalLlm {
    class Model(val id: String, val name: String, val repo: String, val file: String, val mb: Int, val ramGb: Int, val blurb: String) {
        val url get() = "https://huggingface.co/$repo/resolve/main/$file?download=true"
        val fileName get() = "alphastore-llm-$file"
    }

    val MODELS = listOf(
        Model("smollm2-360m", "SmolLM2 360M", "litert-community/SmolLM2-360M-Instruct", "SmolLM2_360M_instruct.litertlm", 374, 3, "A mais leve. Rápida em qualquer telefone, serve para pedidos simples."),
        Model("qwen3-0.6b", "Qwen3 0.6B", "litert-community/Qwen3-0.6B", "Qwen3-0.6B.litertlm", 614, 4, "Rápida e capaz. A melhor escolha para a maioria dos telefones."),
        Model("qwen2.5-1.5b", "Qwen2.5 1.5B", "litert-community/Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", 1598, 6, "Respostas mais inteligentes para telefones recentes."),
        Model("phi4-mini", "Phi-4 mini", "litert-community/Phi-4-mini-instruct", "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm", 3910, 12, "A mais capaz. Apenas telefones flagships."),
    )

    /** A biblioteca nativa do engine, baixada do Maven do Google no primeiro uso e verificada contra este hash antes de ser carregada. */
    private const val ENGINE_URL = "https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.18.1/litertlm-android-0.18.1.aar"
    private const val ENGINE_SHA = "cf4c55139c6fc86425ecfe75f8a907e6193ca665ecc19fbabcaba7ab361bdc43"
    const val ENGINE_FILE = "alphastore-llm-engine.aar"
    const val ENGINE_MB = 20
    private fun engineLib(ctx: Context) = File(ctx.filesDir, "llm/liblitertlm_jni.so")
    @Volatile private var libLoaded = false

    fun engineReady(ctx: Context) = engineLib(ctx).length() > 0 || installEngine(ctx)
    fun engineDownloading() = ENGINE_FILE in Downloads.active()
    fun downloadEngine() { if (!engineDownloading()) Downloads.start(ENGINE_URL, ENGINE_FILE) }

    /** Extrai a biblioteca arm64 de um download de engine concluído, a mantém só se o hash bater, e descarta o arquivo. */
    @Synchronized private fun installEngine(ctx: Context): Boolean {
        val aar = Downloads.file(ENGINE_FILE)?.takeIf { it.length() > 0 && !engineDownloading() } ?: return false
        val out = engineLib(ctx); val tmp = File(out.path + ".tmp")
        val ok = runCatching {
            out.parentFile?.mkdirs()
            java.util.zip.ZipFile(aar).use { z -> z.getInputStream(z.getEntry("jni/arm64-v8a/liblitertlm_jni.so")).use { i -> tmp.outputStream().use { i.copyTo(it) } } }
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
            sha == ENGINE_SHA && tmp.renameTo(out) && out.setReadOnly()
        }.getOrDefault(false)
        tmp.delete(); aar.delete(); Downloads.forget(ENGINE_FILE)
        return ok
    }

    fun model(id: String) = MODELS.firstOrNull { it.id == id } ?: added().firstOrNull { it.id == id }
    /** LiteRT-LM só entrega para ARM de 64 bits. */
    val supported get() = "arm64-v8a" in Build.SUPPORTED_ABIS
    fun ramGb(ctx: Context) = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem.let { (it / 1e9).roundToInt() }
    fun freeMb() = Downloads.dir().usableSpace / 1_000_000
    /** O maior modelo que este telefone tem memória para. */
    fun recommended(ctx: Context) = ramGb(ctx).let { r -> MODELS.lastOrNull { it.ramGb <= r } ?: MODELS.first() }

    fun file(m: Model): File? = Downloads.file(m.fileName)
    fun ready(m: Model) = (file(m)?.length() ?: 0) > 0 && m.fileName !in Downloads.active()
    fun download(ctx: Context, m: Model) { if (!engineReady(ctx)) downloadEngine(); Downloads.start(m.url, m.fileName) }
    fun cancel(m: Model) { runCatching { Downloads.cancel(m.fileName) } }
    fun delete(m: Model) { if (loaded == m.id) close(); file(m)?.delete(); Downloads.forget(m.fileName) }

    /** Modelos que o usuário importou ou buscou por um link do Hugging Face. Os nativos mantêm os próprios ids. */
    fun added(): List<Model> {
        val known = MODELS.map { it.fileName }.toSet()
        return runCatching { Downloads.dir().listFiles()?.toList() }.getOrNull().orEmpty()
            .filter { it.isFile && it.name.startsWith("alphastore-llm-") && it.name.endsWith(".litertlm") && it.name !in known && it.length() > 0 }
            .map { f ->
                val base = f.name.removePrefix("alphastore-llm-")
                Model("x:$base", base.removeSuffix(".litertlm"), "", base, (f.length() / 1_000_000L).toInt().coerceAtLeast(1), 0, "Adicionado por você")
            }
    }

    private fun litertName(raw: String) = raw.substringBefore('?').substringAfterLast('/').substringAfterLast("%2F")
        .takeIf { it.endsWith(".litertlm") && it.none { c -> c == '\\' } }

    /** Copies a .litertlm the user picked. Returns its id. */
    fun import(ctx: Context, displayName: String, input: java.io.InputStream): String {
        val base = litertName(displayName) ?: throw AiError("Escolha um arquivo .litertlm. Modelos GGUF como MiniCPM usam outro engine e não rodam aqui.")
        if (!engineReady(ctx)) downloadEngine()
        val out = File(Downloads.dir(), "alphastore-llm-$base")
        input.use { i -> out.outputStream().use { i.copyTo(it) } }
        return "x:$base"
    }

    /** Um link de arquivo do Hugging Face. Links blob são convertidos no link de download. */
    fun downloadLink(ctx: Context, url: String) {
        val clean = url.trim().replace("/blob/", "/resolve/")
        val base = litertName(clean) ?: throw AiError("Cole um link do Hugging Face que termine em .litertlm.")
        if (!engineReady(ctx)) downloadEngine()
        Downloads.start(if ("download=" in clean) clean else "$clean?download=true", "alphastore-llm-$base")
    }

    private val lock = Mutex()
    private var engine: Engine? = null; private var loaded: String? = null
    fun close() { runCatching { engine?.close() }; engine = null; loaded = null }

    /** Uma resposta do modelo no dispositivo, transmitida para [onText] enquanto cresce. O engine permanece carregado entre as mensagens. */
    suspend fun chat(ctx: Context, m: Model, system: String, turns: List<AlphaStoreAi.Turn>, onText: (String) -> Unit): String = withContext(Dispatchers.Default) {
        if (!supported) throw AiError("IA no dispositivo precisa de um telefone ARM de 64 bits.")
        lock.withLock {
            val f = file(m)?.takeIf { ready(m) } ?: throw AiError("Baixe ${m.name} nas configurações de IA primeiro.")
            if (!libLoaded) {
                if (!engineReady(ctx)) { downloadEngine(); throw AiError("O engine no dispositivo ainda está baixando. Tente novamente em um momento.") }
                runCatching { System.load(engineLib(ctx).path) }.getOrElse { engineLib(ctx).let { f2 -> f2.setWritable(true); f2.delete() }; throw AiError("O engine no dispositivo não pôde iniciar neste telefone.") }
                libLoaded = true
            }
            val e = engine?.takeIf { loaded == m.id } ?: run {
                close()
                runCatching { Engine(EngineConfig(modelPath = f.path, backend = Backend.CPU(), maxNumTokens = 768, cacheDir = ctx.cacheDir.path)).apply { initialize() } }
                    .getOrElse { throw AiError("Este telefone não conseguiu carregar ${m.name}. Tente um modelo menor.") }
                    .also { engine = it; loaded = m.id }
            }
            val history = turns.dropLast(1).map { if (it.user) Message.user(it.text) else Message.model(it.text) }
            e.createConversation(ConversationConfig(systemInstruction = Contents.of(system), initialMessages = history,
                samplerConfig = SamplerConfig(topK = 40, topP = .95, temperature = .6, seed = 0), thinkingConfig = ThinkingConfig(false))).use { conv ->
                val acc = StringBuilder()
                try {
                    conv.sendMessageAsync(turns.last().text).collect { msg ->
                        val t = msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        if (t.isNotEmpty()) { acc.append(t); onText(acc.toString()) }
                    }
                } catch (c: CancellationException) { runCatching { conv.cancelProcess() }; throw c }
                acc.toString().ifBlank { throw AiError("${m.name} enviou uma resposta vazia.") }
            }
        }
    }
}
