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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Categoria de uma entrada de log. */
enum class LogType(val label: String) {
    /** Evento gerado pela lógica do app (repositórios, ViewModels, estado Shizuku). */
    APP("APP"),
    /** Comando shell executado via Shizuku e respectiva resposta/erro. */
    COMMAND("CMD"),
}

/**
 * Entrada imutável no log interno do app.
 *
 * @property id        Identificador único gerado com [System.nanoTime] — usado como
 *                     `key` no LazyColumn para evitar recomposições.
 * @property timestamp Epoch millis de quando o log foi criado.
 * @property type      Categoria: [LogType.APP] ou [LogType.COMMAND].
 * @property message   Mensagem em texto livre (pode conter output de shell).
 */
data class LogEntry(
    val id:        Long    = System.nanoTime(),
    val timestamp: Long    = System.currentTimeMillis(),
    val type:      LogType,
    val message:   String,
) {
    private val FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /** Hora formatada como `HH:mm:ss.SSS`, usado na UI. */
    fun formattedTime(): String = FMT.format(Date(timestamp))
}
