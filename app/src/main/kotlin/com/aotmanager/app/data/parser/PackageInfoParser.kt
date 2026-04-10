/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.data.parser

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser para a saída de `cmd package dump <packageName>`.
 *
 * O formato varia entre versões do Android (9–14), por isso implementamos
 * 4 estratégias em cascata para máxima compatibilidade.
 *
 * ## Exemplo de saída relevante (Android 12+)
 * ```
 * Dexopt state:
 *   [com.example.app]
 *     path: /data/app/~~Xxxx/com.example.app-Yyyy/base.apk
 *       arm64: [status=speed-profile] [reason=install]
 *       arm: [status=verify] [reason=boot-after-ota]
 * ```
 *
 * ## Exemplo Android 9/10
 * ```
 * Dexopt state:
 *   [com.example.app]
 *     Instruction Set: arm64
 *       0: [status=quicken]
 * ```
 */
@Singleton
class PackageInfoParser @Inject constructor() {

    /**
     * Extrai o filtro de compilação do arm64 (arquitetura primária) a partir
     * da saída completa de `cmd package dump`.
     *
     * @return String como "speed-profile", "verify", "quicken", ou "unknown".
     */
    fun parseCompilationFilter(dumpOutput: String): String {
        return tryParseStatusBracket(dumpOutput)
            ?: tryParseStatusEquals(dumpOutput)
            ?: tryParseOdexStatus(dumpOutput)
            ?: tryParseDexoptState(dumpOutput)
            ?: "unknown"
    }

    // ── Estratégia 1: Android 12+ ─────────────────────────────────────────
    // Linha: "      arm64: [status=speed-profile] [reason=install]"
    private fun tryParseStatusBracket(output: String): String? {
        val regex = Regex("""arm64:\s*\[status=([a-z\-]+)\]""")
        return regex.find(output)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 1 (arm64 bracket): $it")
        }
    }

    // ── Estratégia 2: Android 10/11 ───────────────────────────────────────
    // Linha: "    [status=speed-profile]"  ou  "0: [status=quicken]"
    private fun tryParseStatusEquals(output: String): String? {
        // Pega o primeiro status encontrado na seção Dexopt state
        val dexoptSection = output.substringAfter("Dexopt state:", "")
        if (dexoptSection.isEmpty()) return null
        val regex = Regex("""\[status=([a-z\-]+)\]""")
        return regex.find(dexoptSection)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 2 (status equals): $it")
        }
    }

    // ── Estratégia 3: campos odex ─────────────────────────────────────────
    // Linha: "    odexStatus=speed-profile"
    private fun tryParseOdexStatus(output: String): String? {
        val regex = Regex("""odexStatus=([a-z\-]+)""")
        return regex.find(output)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 3 (odexStatus): $it")
        }
    }

    // ── Estratégia 4: Fallback — linha "compilationFilter" direta ────────
    // Linha: "    compilationFilter=speed"
    private fun tryParseDexoptState(output: String): String? {
        val regex = Regex("""compilationFilter=([a-z\-]+)""")
        return regex.find(output)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 4 (compilationFilter): $it")
        }
    }
}
