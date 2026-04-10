/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.domain.model

/**
 * Perfis de compilação disponíveis via `cmd package compile -m <profile>`.
 *
 * A ordem dos valores reflete o espectro de AOT: do mais completo ao mais leve.
 *
 * @property cmdValue  Valor passado ao argumento `-m` do comando `cmd package compile`.
 * @property displayName Nome legível exibido na UI.
 * @property description Descrição técnica do comportamento do perfil.
 */
enum class CompilationProfile(
    val cmdValue: String,
    val displayName: String,
    val description: String,
) {
    /** Compila todos os métodos para código nativo (ART). Máxima performance, maior tamanho OAT. */
    SPEED(
        cmdValue     = "speed",
        displayName  = "Speed",
        description  = "AOT máximo — todos os métodos compilados para nativo",
    ),

    /**
     * Compila apenas os métodos presentes no baseline/startup profile.
     * Equilíbrio ideal entre tamanho e performance de cold start.
     */
    SPEED_PROFILE(
        cmdValue     = "speed-profile",
        displayName  = "Speed Profile",
        description  = "AOT guiado por baseline profile — ideal para produção",
    ),

    /** Aplica otimizações 'quicken' (instruções DEX especializadas). Mais leve que speed. */
    QUICKEN(
        cmdValue     = "quicken",
        displayName  = "Quicken",
        description  = "Otimizações quicken — rápido de compilar, bom para dev/staging",
    ),

    /** Compila absolutamente tudo, incluindo código raramente executado. */
    EVERYTHING(
        cmdValue     = "everything",
        displayName  = "Everything",
        description  = "Compila 100% do bytecode DEX para nativo",
    ),

    /** Apenas verifica o bytecode DEX sem compilação AOT. */
    VERIFY(
        cmdValue     = "verify",
        displayName  = "Verify",
        description  = "Apenas verificação DEX — sem código nativo gerado",
    ),

    /** Remove a verificação DEX. Útil para sistemas com restrições de espaço. */
    VERIFY_NONE(
        cmdValue     = "verify-none",
        displayName  = "Verify None",
        description  = "Sem verificação — menor overhead, maior risco",
    ),

    /** Desativa compilação e executa em modo de interpretação pura. */
    INTERPRET_ONLY(
        cmdValue     = "interpret-only",
        displayName  = "Interpret Only",
        description  = "Interpretação pura — útil para debug de comportamento JIT",
    );

    companion object {
        /** Converte o valor CMD de volta para o enum. Retorna [SPEED_PROFILE] se não encontrado. */
        fun fromCmdValue(value: String): CompilationProfile =
            entries.firstOrNull { it.cmdValue == value } ?: SPEED_PROFILE
    }
}
