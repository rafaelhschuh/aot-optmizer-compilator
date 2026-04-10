/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.data.shizuku

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holder singleton do estado de conexão/permissão Shizuku.
 *
 * Injetado tanto na [com.aotmanager.app.MainActivity] (que atualiza o estado
 * conforme os callbacks Shizuku chegam) quanto nos ViewModels e no
 * [ShizukuCommandExecutor] (que verificam se podem executar comandos).
 *
 * Usar um singleton compartilhado evita acoplamento direto entre Activity e
 * ViewModel, respeitando a separação de responsabilidades.
 */
@Singleton
class ShizukuStateHolder @Inject constructor() {

    private val _isReady = MutableStateFlow(false)

    /**
     * `true` quando o Binder Shizuku está vivo E a permissão foi concedida.
     * Observado pela UI para exibir conteúdo ou tela de onboarding.
     */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Atualiza o estado de prontidão. Chamado pela MainActivity. */
    fun setReady(ready: Boolean) {
        _isReady.value = ready
    }

    /** Retorna o valor atual sem coletar o Flow. */
    val isReadyNow: Boolean get() = _isReady.value
}
