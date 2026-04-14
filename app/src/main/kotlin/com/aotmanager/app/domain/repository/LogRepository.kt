/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.domain.repository

import com.aotmanager.app.domain.model.LogEntry
import com.aotmanager.app.domain.model.LogType
import kotlinx.coroutines.flow.StateFlow

/**
 * Repositório de logs internos do app.
 *
 * Implementação mantida como Singleton via Hilt — o mesmo estado é compartilhado entre
 * [com.aotmanager.app.data.shizuku.ShizukuCommandExecutor],
 * [com.aotmanager.app.data.repository.PackageRepositoryImpl] e
 * a tela de Logs na UI.
 */
interface LogRepository {

    /** Estado observável da lista de entradas de log (mais recente ao final). */
    val logs: StateFlow<List<LogEntry>>

    /**
     * Adiciona uma entrada ao log.
     *
     * Thread-safe: pode ser chamado de qualquer dispatcher.
     * O buffer interno é limitado a [MAX_ENTRIES] entradas para evitar crescimento ilimitado.
     */
    fun log(type: LogType, message: String)

    /** Remove todas as entradas do log. */
    fun clear()

    companion object {
        /** Número máximo de entradas mantidas em memória. */
        const val MAX_ENTRIES = 500
    }
}
