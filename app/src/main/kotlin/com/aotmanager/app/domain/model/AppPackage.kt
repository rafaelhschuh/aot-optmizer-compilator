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

import androidx.compose.runtime.Immutable

/**
 * Representa um pacote Android instalado no dispositivo.
 *
 * @property packageName  Nome canônico do pacote (ex: "com.example.app").
 * @property label        Nome legível para o usuário (ex: "Example App").
 * @property versionName  Versão legível (ex: "1.2.3").
 * @property versionCode  Versão numérica interna.
 * @property isSystemApp  `true` se o pacote está na partição system/priv-app.
 * @property compilationFilter Filtro de compilação atual reportado pelo ART
 *                             (ex: "speed-profile", "verify", "unknown").
 *                             Obtido via `cmd package dump` — pode ser "unknown"
 *                             se não foi possível consultar via Shizuku.
 */
@Immutable
data class AppPackage(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val compilationFilter: String = "unknown",
)
