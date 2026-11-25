package com.ramonmods.alphastore.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Componente de verificação de desenvolvedor do Android (`com.google.android.verifier`), enviado a todo dispositivo, que pode retardar ou bloquear
 *  instalações via sideload. A detecção é somente leitura; desligar roda os comandos que o Google documenta (reversíveis) através de qualquer
 *  rota privilegiada que o usuário configurou. Desabilitar para o usuário atual é a alavanca recomendada; desinstalar-para-usuário é o fallback
 *  que OEMs que bloqueiam o disable ainda permitem. Os globals desligam os hooks de verificação do Play Protect sem remover nada. */
object Verifier {
    const val PKG = "com.google.android.verifier"
    const val OVERLAY = "com.google.android.verifier.overlay"

    enum class State { ACTIVE, OFF, ABSENT }

    /** Onde o pacote do verificador mora. NONE significa que este telefone não tem. DATA é uma cópia instalada via Play. SYSTEM é gravado na ROM, então está congelado, não removido. */
    enum class Home { NONE, DATA, SYSTEM }

    fun home(dumpsys: String): Home {
        if (dumpsys.isBlank() || "Unable to find package" in dumpsys) return Home.NONE
        val path = Regex("""codePath=(\S+)""").find(dumpsys)?.groupValues?.get(1).orEmpty()
        if (path.isEmpty()) return if ("Package [" in dumpsys) Home.SYSTEM else Home.NONE
        return if (path.startsWith("/data/")) Home.DATA else Home.SYSTEM
    }

    /** App ops que impedem o verificador de acordar ou iniciar na inicialização, mesmo que outro app o reative. Escopo apenas para este pacote. */
    val QUIET = listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "BOOT_COMPLETED").map { "cmd appops set $PKG $it ignore" }

    /** Comandos para um ciclo, em ordem. Um pacote ausente pula direto para as três configurações do verificador. Nada aqui nomeia outro pacote, e toda mudança pm fica no usuário atual para o Revert desfazê-la. */
    fun secureSteps(pkgDump: String, overlayDump: String): List<String> {
        val steps = ArrayList<String>()
        when (home(pkgDump)) {
            Home.NONE -> {}
            Home.DATA -> { steps += QUIET; steps += DISABLE_CMD; steps += UNINSTALL_CMD }
            Home.SYSTEM -> { steps += QUIET; steps += DISABLE_CMD }
        }
        if (home(overlayDump) != Home.NONE) steps += "pm disable-user --user 0 $OVERLAY"
        steps += GLOBALS.map { (k, v) -> "settings put global $k $v" }
        return steps
    }

    /** Hooks globais de verificação e o valor que liga cada um. Seus valores antigos são salvos primeiro para o Revert restaurá-los. */
    val GLOBALS = listOf("package_verifier_user_consent" to "-1", "package_verifier_enable" to "0", "verifier_verify_adb_installs" to "0")
    const val DISABLE_CMD = "pm disable-user --user 0 $PKG"
    /** Fallback quando um OEM bloqueia disable-user (o componente fica habilitado depois). Mantém o APK, então o Revert pode trazê-lo de volta. */
    const val UNINSTALL_CMD = "pm uninstall -k --user 0 $PKG"
    val RESTORE_CMDS = listOf("cmd package install-existing --user 0 $PKG", "pm enable --user 0 $PKG")

    /** ACTIVE enquanto o componente está instalado e habilitado para este usuário, OFF após desabilitado ou removido para este usuário, ABSENT em telefones que nunca o receberam. */
    fun state(ctx: Context): State = runCatching {
        val pm = ctx.packageManager
        val info = pm.getApplicationInfo(PKG, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        val on = info.flags and ApplicationInfo.FLAG_INSTALLED != 0 && pm.getApplicationEnabledSetting(PKG).let {
            it == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || it == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        if (on) State.ACTIVE else State.OFF
    }.getOrDefault(State.ABSENT)

    fun active(ctx: Context) = state(ctx) == State.ACTIVE

    /** pm and settings print their refusals instead of failing; this pulls out the line that says why, if there is one. */
    fun refusal(out: String): String? {
        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // A stack trace's "SomeException: reason" line beats its "Exception occurred while executing" header
        val l = lines.firstOrNull { "Exception: " in it } ?: lines.firstOrNull { l -> listOf("Error", "Failure", "not allowed", "Cannot", "denied").any { it in l } } ?: return null
        return l.substringAfterLast("Exception: ").removePrefix("Error: ").trimEnd('.').ifBlank { null }
    }
}
