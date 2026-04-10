/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.ui.compilation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aotmanager.app.domain.model.CompilationProfile
import com.aotmanager.app.domain.model.CompilationResult
import com.aotmanager.app.domain.repository.PackageRepository
import com.aotmanager.app.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel da tela de compilação.
 *
 * Recebe a lista de pacotes selecionados e o perfil desejado via
 * [SavedStateHandle] (passados pela navegação type-safe), executa as
 * compilações sequencialmente e atualiza o [CompilationUiState] em tempo real.
 */
@HiltViewModel
class CompilationViewModel @Inject constructor(
    private val repository: PackageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: AppRoute.Compilation = savedStateHandle.toRoute()

    private val _uiState = MutableStateFlow(
        CompilationUiState(
            items = route.packageNames.map { name ->
                CompilationItemState(packageName = name)
            },
            selectedProfile = CompilationProfile.fromCmdValue(route.profile),
            forceCompile    = route.force,
            total           = route.packageNames.size,
        )
    )
    val uiState = _uiState.asStateFlow()

    fun startCompilation() {
        if (_uiState.value.isRunning) return

        _uiState.update { it.copy(isRunning = true, completed = 0) }

        viewModelScope.launch {
            val profile = _uiState.value.selectedProfile
            val force   = _uiState.value.forceCompile

            route.packageNames.forEachIndexed { index, packageName ->
                // Marca item como RUNNING
                _uiState.update { state ->
                    state.copy(
                        items = state.items.mapIndexed { i, item ->
                            if (i == index) item.copy(status = CompilationStatus.RUNNING) else item
                        }
                    )
                }

                Timber.d("Compilando [$index/${route.packageNames.size}] $packageName")

                val result = repository.compilePackage(packageName, profile, force)

                // Atualiza item com resultado
                _uiState.update { state ->
                    state.copy(
                        completed = state.completed + 1,
                        items = state.items.mapIndexed { i, item ->
                            if (i == index) {
                                when (result) {
                                    is CompilationResult.Success -> item.copy(
                                        status   = CompilationStatus.SUCCESS,
                                        output   = result.output.trim().takeLast(200),
                                        duration = result.durationMs,
                                    )
                                    is CompilationResult.Failure -> item.copy(
                                        status = CompilationStatus.FAILURE,
                                        output = result.errorMessage,
                                    )
                                }
                            } else item
                        }
                    )
                }
            }

            _uiState.update { it.copy(isRunning = false) }
            Timber.i("Compilação batch concluída: ${route.packageNames.size} pacotes")
        }
    }

    fun onProfileSelected(profile: CompilationProfile) {
        if (!_uiState.value.isRunning) {
            _uiState.update { it.copy(selectedProfile = profile) }
        }
    }

    fun onForceToggled(force: Boolean) {
        if (!_uiState.value.isRunning) {
            _uiState.update { it.copy(forceCompile = force) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UiState
// ═══════════════════════════════════════════════════════════════════════════════

data class CompilationUiState(
    val items:           List<CompilationItemState> = emptyList(),
    val isRunning:       Boolean                    = false,
    val completed:       Int                        = 0,
    val total:           Int                        = 0,
    val selectedProfile: CompilationProfile         = CompilationProfile.SPEED_PROFILE,
    val forceCompile:    Boolean                    = true,
) {
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
    val isDone: Boolean get() = !isRunning && completed == total && total > 0
    val successCount: Int get() = items.count { it.status == CompilationStatus.SUCCESS }
    val failureCount: Int get() = items.count { it.status == CompilationStatus.FAILURE }
}

data class CompilationItemState(
    val packageName: String,
    val status:      CompilationStatus = CompilationStatus.PENDING,
    val output:      String            = "",
    val duration:    Long              = 0L,
)

enum class CompilationStatus { PENDING, RUNNING, SUCCESS, FAILURE }
