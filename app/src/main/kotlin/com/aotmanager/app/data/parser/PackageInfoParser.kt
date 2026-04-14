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
 * Parser para saídas dos comandos `cmd package dump <pkg>` e `dumpsys package dexopt`.
 *
 * ## Formatos suportados (Android 9–14)
 *
 * **Android 12+ (estratégia 1)**
 * ```
 * arm64: [status=speed-profile] [reason=install]
 * ```
 *
 * **Android 10/11 (estratégia 2)**
 * ```
 * [status=speed-profile]
 * 0: [status=quicken]
 * ```
 *
 * **Campos odex (estratégia 3)**
 * ```
 * odexStatus=speed-profile
 * ```
 *
 * **Fallback compilationFilter (estratégia 4)**
 * ```
 * compilationFilter=speed
 * ```
 */
@Singleton
class PackageInfoParser @Inject constructor() {

    // ── Regex compartilhadas ──────────────────────────────────────────────────

    /**
     * Cabeçalho de seção de pacote no dump bulk:  "  [com.example.app]"
     *
     * Requisito para não colidir com linhas de status como "[status=speed-profile]":
     * - Conteúdo dentro dos colchetes deve começar com letra e conter apenas
     *   letras, dígitos, ponto e underscore (package name canônico).
     * - O sinal `=` que aparece em entradas de status NÃO está no charset → sem falso positivo.
     */
    private val PACKAGE_HEADER_RE = Regex("""^\s*\[([a-zA-Z][a-zA-Z0-9_.]*)\]\s*$""")

    /** Estratégia 1 – Android 12+: `arm64: [status=speed-profile]` */
    private val ARM64_STATUS_RE = Regex("""arm64:\s*\[status=([a-z][a-z\-]*)\]""")

    /** Estratégia 2 – Android 10/11: `[status=quicken]` ou `0: [status=quicken]` */
    private val ANY_STATUS_BRACKET_RE = Regex("""\[status=([a-z][a-z\-]*)\]""")

    /** Estratégia 3: `odexStatus=speed-profile` */
    private val ODEX_STATUS_RE = Regex("""odexStatus=([a-z][a-z\-]*)""")

    /** Estratégia 4 (fallback): `compilationFilter=speed` */
    private val COMPILE_FILTER_RE = Regex("""compilationFilter=([a-z][a-z\-]*)""")

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Extrai o `compilationFilter` de um único pacote a partir da saída completa
     * de `cmd package dump <packageName>`. Usa as 4 estratégias em cascata.
     *
     * @return String como "speed-profile", "verify", ou "unknown".
     */
    fun parseCompilationFilter(dumpOutput: String): String {
        return tryParseArm64StatusBracket(dumpOutput)
            ?: tryParseAnyStatusBracket(dumpOutput)
            ?: tryParseOdexStatus(dumpOutput)
            ?: tryParseCompileFilter(dumpOutput)
            ?: "unknown"
    }

    /**
     * Faz o parse do output de `dumpsys package dexopt` (ou `cmd package dump dexopt`)
     * e extrai o `compilationFilter` de **todos** os pacotes em uma única varredura O(linhas).
     *
     * ## Algoritmo
     * 1. Itera linha a linha com [lineSequence] (sem alocar String intermediária).
     * 2. Linha que bate com [PACKAGE_HEADER_RE] → novo `currentPackage`.
     * 3. Para o pacote atual, tenta extrair status por linha:
     *    - Linha com `arm64:` → **finaliza** o pacote (status preferencial — arch primária).
     *    - Linha com outro indicador → armazena como candidato, mas não finaliza
     *      (permite que `arm64:` numa linha subsequente sobrescreva).
     * 4. Retorna mapa `packageName → compilationFilter` (apenas valores não-"unknown").
     *
     * @param output Saída bruta do comando `dumpsys package dexopt`.
     * @return Mapa de packageName para compilationFilter (ex: `"com.foo" to "speed-profile"`).
     *         Pacotes sem status detectado NÃO aparecem no mapa.
     */
    fun parseBulkDexoptStates(output: String): Map<String, String> {
        val result      = mutableMapOf<String, String>()
        val finalized   = mutableSetOf<String>()   // pacotes com status arm64 confirmado
        var currentPkg: String? = null

        for (line in output.lineSequence()) {

            // ── Detecta nova seção de pacote ──────────────────────────────
            val pkgMatch = PACKAGE_HEADER_RE.find(line)
            if (pkgMatch != null) {
                currentPkg = pkgMatch.groupValues[1]
                continue
            }

            val pkg = currentPkg ?: continue
            if (pkg in finalized) continue       // arm64 já confirmado — pula linhas restantes

            // ── Estratégia 1: arm64 (status definitivo) ───────────────────
            val arm64Status = ARM64_STATUS_RE.find(line)?.groupValues?.getOrNull(1)
            if (arm64Status != null) {
                result[pkg] = arm64Status
                finalized.add(pkg)               // não sobrescrever com arch inferior
                continue
            }

            // ── Estratégias 2-4: candidato (pode ser sobrescrito por arm64) ─
            if (pkg !in result) {
                val candidate = ANY_STATUS_BRACKET_RE.find(line)?.groupValues?.getOrNull(1)
                    ?: ODEX_STATUS_RE.find(line)?.groupValues?.getOrNull(1)
                    ?: COMPILE_FILTER_RE.find(line)?.groupValues?.getOrNull(1)
                if (candidate != null) result[pkg] = candidate
            }
        }

        Timber.d("parseBulkDexoptStates: ${result.size} pacotes com status detectado")
        return result
    }

    // ── Helpers privados (per-dump, usados por parseCompilationFilter) ────────

    private fun tryParseArm64StatusBracket(output: String): String? {
        return ARM64_STATUS_RE.find(output)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 1 (arm64 bracket): $it")
        }
    }

    private fun tryParseAnyStatusBracket(output: String): String? {
        val dexoptSection = output.substringAfter("Dexopt state:", "")
        if (dexoptSection.isEmpty()) return null
        return ANY_STATUS_BRACKET_RE.find(dexoptSection)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 2 (status equals): $it")
        }
    }

    private fun tryParseOdexStatus(output: String): String? {
        return ODEX_STATUS_RE.find(output)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 3 (odexStatus): $it")
        }
    }

    private fun tryParseCompileFilter(output: String): String? {
        return COMPILE_FILTER_RE.find(output)?.groupValues?.getOrNull(1).also {
            if (it != null) Timber.d("Parser estratégia 4 (compilationFilter): $it")
        }
    }
}
