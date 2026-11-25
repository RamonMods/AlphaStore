package com.ramonmods.alphastore.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ramonmods.alphastore.data.HttpError
import com.ramonmods.alphastore.data.Net
import com.ramonmods.alphastore.data.VtStats
import com.ramonmods.alphastore.data.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder

enum class VtView { KEY, READY, SCANNING, RESULTS, ERROR }
enum class ScanTargetType { APK, DOWNLOADED_APK, REMOTE_APK }
data class ScanTarget(val type: ScanTargetType, val label: String, val detail: String)
data class VtEngine(val engine: String, val category: String, val result: String?)
data class VtResult(val type: ScanTargetType, val hash: String?, val stats: VtStats, val engines: List<VtEngine>, val fresh: Boolean, val permalink: String?)

private const val VT = "https://www.virustotal.com/api/v3"

private fun parseBody(b: String): JsonObject {
    if (b.isEmpty()) return JsonObject(emptyMap())
    if (b.trimStart().startsWith("<")) throw Exception("O VirusTotal retornou uma página HTML em vez de JSON. Isso geralmente significa que a requisição foi bloqueada pelo Cloudflare ou a API está temporariamente indisponível. Tente novamente em um minuto.")
    return runCatching { json.parseToJsonElement(b).jsonObject }.getOrElse { throw Exception("O VirusTotal retornou uma resposta inesperada. A API pode estar temporariamente indisponível. Tente novamente mais tarde.") }
}

private fun apiError(status: Int, fallback: String = "A requisição ao VirusTotal falhou.") = when {
    status == 401 || status == 403 -> "A chave da API foi rejeitada. Verifique a chave e tente novamente."
    status == 404 -> "Não existe nenhum relatório do VirusTotal para este alvo ainda."
    status == 429 -> "Cota do VirusTotal atingida. Chaves gratuitas costumam ter limite de 4 requisições por minuto."
    status >= 500 -> "O VirusTotal está tendo problemas agora. Tente novamente mais tarde."
    else -> fallback
}

private fun JsonObject.obj(k: String) = this[k] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.int(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
private fun stats(o: JsonObject) = VtStats(o.int("harmless"), o.int("malicious"), o.int("suspicious"), o.int("undetected"), o.int("timeout"))
private fun engines(o: JsonObject) = o.map { (k, v) -> v.jsonObject.let { VtEngine(it.str("engine_name") ?: k, it.str("category") ?: "", it.str("result")) } }

/** Port de hooks/useVirusTotalScan.ts. Estado do Compose para que os sheets o observem diretamente. */
class VtScan(private val tracker: AppTracker, private val appId: String, private val appName: String, private val packageName: String?, apiKey: String) {
    var view by mutableStateOf(if (apiKey.isNotBlank()) VtView.READY else VtView.KEY)
    var note by mutableStateOf("Preparando verificação...")
    var progress by mutableFloatStateOf(0f)
    var result by mutableStateOf<VtResult?>(null)
    var error by mutableStateOf("")

    private suspend fun get(path: String, key: String): Pair<Int, JsonObject> {
        val c = Net.client.newBuilder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        return try { 200 to parseBody(Net.text("$VT$path", mapOf("x-apikey" to key), c = c)) } catch (e: HttpError) { e.code to runCatching { parseBody(e.body) }.getOrDefault(JsonObject(emptyMap())) }
    }

    private fun step(n: String, p: Int) { note = n; progress = p / 100f }

    private suspend fun report(hash: String, key: String): VtResult? {
        val (s, b) = get("/files/$hash", key)
        if (s == 404) return null
        if (s !in 200..299) throw Exception(apiError(s))
        val a = b.obj("data").obj("attributes")
        return VtResult(ScanTargetType.APK, hash, stats(a.obj("last_analysis_stats")), engines(a.obj("last_analysis_results")), false, "https://www.virustotal.com/gui/file/$hash")
    }

    private suspend fun poll(id: String, key: String, hash: String): VtResult {
        repeat(18) { i ->
            step(if (i == 0) "Aguardando motores..." else "Coletando vereditos dos motores...", minOf(94, 58 + i * 2))
            delay(if (i < 3) 5000 else 10000)
            val (s, b) = get("/analyses/$id", key)
            if (s !in 200..299) throw Exception(apiError(s, "Analysis polling failed."))
            val a = b.obj("data").obj("attributes")
            if (a.str("status") == "completed") return VtResult(ScanTargetType.APK, hash, stats(a.obj("stats")), engines(a.obj("results")), true, "https://www.virustotal.com/gui/file/$hash")
        }
        throw Exception("O VirusTotal ainda está analisando este app. Tente abrir o relatório do VirusTotal em um minuto.")
    }

    private suspend fun scanPath(path: String, key: String): VtResult {
        step("Calculando SHA-256...", 18)
        val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tracker.hash(path) }
        step("Verificando relatório existente do VirusTotal...", 34)
        report(hash, key)?.let { return it }
        step("Enviando APK apenas porque o VirusTotal não tem relatório...", 48)
        val (s, body) = tracker.vtUpload(path, key)
        val b = parseBody(body)
        if (s !in 200..299) throw Exception(apiError(s, "Upload failed."))
        val id = b.obj("data").str("id") ?: throw Exception("VirusTotal did not return an analysis id.")
        return poll(id, key, hash)
    }

    private fun tempName(url: String): String {
        var base = "${appId.ifEmpty { appName }}-vt-scan.apk"
        runCatching { URLDecoder.decode(URI(url).path.substringAfterLast('/'), "UTF-8") }.getOrNull()?.takeIf { Regex("\\.(apk|apks|xapk|zip)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }?.let { base = it }
        val safe = base.replace(Regex("[^a-zA-Z0-9._-]"), "_"); val sfx = System.currentTimeMillis().toString(36); val dot = safe.lastIndexOf('.')
        return if (dot > 0) "${safe.take(dot)}_$sfx${safe.substring(dot)}" else "${safe}_$sfx.apk"
    }

    private suspend fun downloadAndScan(url: String, key: String): VtResult {
        val name = tempName(url)
        step("Baixando APK para verificação de arquivo...", 8)
        try {
            Downloads.start(url, name)
            run wait@{
                repeat(180) {
                    val d = Downloads.status(name)
                    progress = d.progress.coerceIn(8, 40) / 100f
                    if (d.status == DlStatus.SUCCESSFUL) return@wait
                    if (d.status == DlStatus.FAILED) throw Exception("APK download failed before scanning.")
                    delay(1000)
                }
                throw Exception("O download do APK demorou demais. Tente novamente com uma conexão mais forte.")
            }
            return scanPath(tracker.resolve(name).absolutePath, key)
        } finally { Downloads.forget(name); runCatching { tracker.deleteFile(name) } }
    }

    fun reset() { result = null; error = ""; progress = 0f; note = "Preparando verificação..." }

    /** Returns the result, or null on error. [haptic] receives "success" | "warning" | "error". */
    suspend fun start(target: ScanTarget?, key: String, haptic: (String) -> Unit): VtResult? {
        val k = key.trim()
        if (k.isEmpty()) { view = VtView.KEY; return null }
        if (target == null) { error = "Nenhum APK instalado, APK baixado ou arquivo APK direto foi encontrado para este app."; view = VtView.ERROR; return null }
        reset(); view = VtView.SCANNING; step("Iniciando verificação do VirusTotal...", 8)
        return try {
            val r = when (target.type) {
                ScanTargetType.APK -> scanPath(tracker.extractApk(packageName ?: throw Exception("No Android package is available for this scan target.")), k)
                ScanTargetType.DOWNLOADED_APK -> scanPath(tracker.resolve(target.detail).absolutePath, k)
                ScanTargetType.REMOTE_APK -> downloadAndScan(target.detail, k).copy(type = ScanTargetType.REMOTE_APK)
            }.let { if (target.type == ScanTargetType.DOWNLOADED_APK) it.copy(type = target.type) else it }
            step("Relatório pronto.", 100); delay(350)
            result = r; view = VtView.RESULTS
            haptic(if (r.stats.malicious + r.stats.suspicious >= 5) "aviso" else "sucesso"); r
        } catch (e: Exception) {
            error = e.message ?: "Falha na verificação do VirusTotal."; view = VtView.ERROR; haptic("error"); null
        }
    }
}
