/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.data.log

import com.aotmanager.app.domain.model.LogEntry
import com.aotmanager.app.domain.model.LogType
import com.aotmanager.app.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação em memória do [LogRepository].
 *
 * - Thread-safe: [MutableStateFlow.update] é atômico.
 * - Buffer limitado a [LogRepository.MAX_ENTRIES] entradas — as mais antigas são descartadas
 *   via [List.takeLast] para evitar vazamento de memória.
 * - Não persiste entre sessões (design intencional — logs são para diagnóstico em runtime).
 */
@Singleton
class LogRepositoryImpl @Inject constructor() : LogRepository {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    override fun log(type: LogType, message: String) {
        val entry = LogEntry(type = type, message = message)
        _logs.update { current ->
            (current + entry).takeLast(LogRepository.MAX_ENTRIES)
        }
    }

    override fun clear() {
        _logs.value = emptyList()
    }
}
