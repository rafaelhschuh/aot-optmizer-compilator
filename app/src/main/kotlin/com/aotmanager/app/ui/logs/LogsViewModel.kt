/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotmanager.app.domain.model.LogEntry
import com.aotmanager.app.domain.model.LogType
import com.aotmanager.app.domain.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logRepository: LogRepository,
) : ViewModel() {

    /** Todos os logs em ordem cronológica. */
    val allLogs: StateFlow<List<LogEntry>> = logRepository.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Apenas logs do tipo [LogType.APP]. */
    val appLogs: StateFlow<List<LogEntry>> = logRepository.logs
        .map { list -> list.filter { it.type == LogType.APP } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Apenas logs do tipo [LogType.COMMAND]. */
    val commandLogs: StateFlow<List<LogEntry>> = logRepository.logs
        .map { list -> list.filter { it.type == LogType.COMMAND } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Remove todas as entradas do log. */
    fun clearLogs() = logRepository.clear()
}
