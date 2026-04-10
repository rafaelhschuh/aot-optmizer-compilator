/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.ui.packagelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotmanager.app.data.shizuku.ShizukuStateHolder
import com.aotmanager.app.domain.model.AppPackage
import com.aotmanager.app.domain.repository.PackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel da tela de listagem de pacotes.
 *
 * Gerencia:
 * - Carregamento da lista via [PackageRepository]
 * - Filtragem por texto (com debounce para não fazer re-fetch a cada tecla)
 * - Seleção múltipla para compilação em lote
 * - Flag de inclusão de apps do sistema
 */
@HiltViewModel
class PackageListViewModel @Inject constructor(
    private val repository: PackageRepository,
    private val shizukuStateHolder: ShizukuStateHolder,
) : ViewModel() {

    // ── Controles internos ────────────────────────────────────────────────────
    private val _searchQuery        = MutableStateFlow("")
    private val _selectedPackages   = MutableStateFlow<Set<String>>(emptySet())
    private val _includeSystemApps  = MutableStateFlow(false)
    private val _isLoadingPackages  = MutableStateFlow(true)

    // ── UiState exposto ───────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState = combine(
        _includeSystemApps.flatMapLatest { includeSystem ->
            _isLoadingPackages.value = true
            repository.getInstalledPackages(includeSystem)
        },
        _searchQuery.debounce(200),
        _selectedPackages,
        _includeSystemApps,
        shizukuStateHolder.isReady,
    ) { packages, query, selected, includeSystem, shizukuReady ->
        _isLoadingPackages.value = false

        val filtered = if (query.isBlank()) {
            packages
        } else {
            val q = query.lowercase()
            packages.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        PackageListUiState(
            allPackages      = packages,
            filteredPackages = filtered,
            isLoading        = false,
            searchQuery      = query,
            selectedPackages = selected,
            includeSystemApps = includeSystem,
            shizukuReady     = shizukuReady,
        )
    }.stateIn(
        scope            = viewModelScope,
        started          = SharingStarted.WhileSubscribed(5_000),
        initialValue     = PackageListUiState(isLoading = true),
    )

    // ── Actions ───────────────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onPackageSelectionToggled(packageName: String) {
        _selectedPackages.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun onSelectAll() {
        _selectedPackages.value = uiState.value.filteredPackages
            .map { it.packageName }.toSet()
    }

    fun onDeselectAll() {
        _selectedPackages.value = emptySet()
    }

    fun onIncludeSystemAppsToggled(include: Boolean) {
        _includeSystemApps.value = include
        _selectedPackages.value  = emptySet()
    }

    fun onRefresh() {
        viewModelScope.launch {
            _isLoadingPackages.value = true
            try {
                repository.refresh()
            } catch (e: Exception) {
                Timber.e(e, "Falha ao atualizar lista de pacotes")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UiState
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Estado imutável da tela de listagem de pacotes.
 *
 * @property allPackages      Lista completa sem filtro de texto aplicado.
 * @property filteredPackages Lista após aplicar [searchQuery].
 * @property isLoading        `true` enquanto o carregamento inicial não concluiu.
 * @property searchQuery      Texto atual de busca.
 * @property selectedPackages Conjunto de `packageName` selecionados para compilação.
 * @property includeSystemApps Se apps de sistema estão visíveis.
 * @property shizukuReady     Se Shizuku está conectado e com permissão.
 */
data class PackageListUiState(
    val allPackages:       List<AppPackage> = emptyList(),
    val filteredPackages:  List<AppPackage> = emptyList(),
    val isLoading:         Boolean          = false,
    val searchQuery:       String           = "",
    val selectedPackages:  Set<String>      = emptySet(),
    val includeSystemApps: Boolean          = false,
    val shizukuReady:      Boolean          = false,
) {
    val hasSelection: Boolean get() = selectedPackages.isNotEmpty()
    val selectionCount: Int   get() = selectedPackages.size
}
