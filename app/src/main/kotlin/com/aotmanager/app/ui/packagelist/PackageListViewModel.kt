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

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotmanager.app.data.shizuku.ShizukuStateHolder
import com.aotmanager.app.domain.model.AppPackage
import com.aotmanager.app.domain.model.CompilationResult
import com.aotmanager.app.domain.model.LogType
import com.aotmanager.app.domain.repository.LogRepository
import com.aotmanager.app.domain.repository.PackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel da tela de listagem de pacotes.
 *
 * ## Fluxo de dados
 *
 * ```
 * PackageManager ──► rawPackagesFlow (compilationFilter = "unknown")
 *                          │
 *                          ▼
 *         quando Shizuku ready: launchFilterFetch()
 *                          │ executa cmd package dump em paralelo (max 4 concurrent)
 *                          ▼
 *               _compilationFilters: Map<pkg, filter>
 *                          │
 *                          ▼  combine
 *               enrichedPackagesFlow  ◄── sobrepõe filtros buscados
 *                          │
 *                          ▼  combine + debounce + compilationFilter chip
 *                      uiState: StateFlow<PackageListUiState>
 * ```
 */
@HiltViewModel
class PackageListViewModel @Inject constructor(
    private val repository: PackageRepository,
    private val shizukuStateHolder: ShizukuStateHolder,
    private val logRepository: LogRepository,
) : ViewModel() {

    // ── Controles internos ────────────────────────────────────────────────────
    private val _searchQuery             = MutableStateFlow("")
    private val _selectedPackages        = MutableStateFlow<Set<String>>(emptySet())
    private val _includeSystemApps       = MutableStateFlow(false)
    private val _isLoadingPackages       = MutableStateFlow(true)
    private val _activeCompilationFilter = MutableStateFlow<String?>(null)
    private val _packagesResetting       = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Mapa packageName → compilationFilter buscado via `cmd package dump`.
     * Populado progressivamente após Shizuku estar pronto.
     * Vazio enquanto Shizuku não conectou ou após desconexão.
     */
    private val _compilationFilters  = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * `true` enquanto [launchFilterFetch] está em execução.
     * Usado para exibir [LinearProgressIndicator] na UI.
     */
    private val _isFetchingFilters   = MutableStateFlow(false)

    /** Job do fetch em andamento — cancelado quando packages ou estado do Shizuku mudam. */
    private var fetchFiltersJob: Job? = null

    // ── Eventos de UI one-shot ────────────────────────────────────────────────
    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    // ── Flows base ────────────────────────────────────────────────────────────

    /**
     * Packages brutos do repositório (compilationFilter sempre "unknown" aqui).
     * Compartilhado via [shareIn] para evitar múltiplas assinaturas ao repositório.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawPackagesFlow: Flow<List<AppPackage>> = _includeSystemApps
        .flatMapLatest { includeSystem ->
            _isLoadingPackages.value = true
            repository.getInstalledPackages(includeSystem)
        }
        .onEach { _isLoadingPackages.value = false }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    /**
     * Packages enriquecidos: para cada pacote cujo packageName esteja em
     * [_compilationFilters], substitui o `compilationFilter = "unknown"` pelo
     * valor real buscado via Shizuku. O restante permanece "unknown" até ser buscado.
     */
    private val enrichedPackagesFlow: Flow<List<AppPackage>> = combine(
        rawPackagesFlow,
        _compilationFilters,
    ) { packages, filters ->
        if (filters.isEmpty()) return@combine packages
        packages.map { pkg ->
            val fetched = filters[pkg.packageName]
            if (fetched != null && fetched != "unknown") pkg.copy(compilationFilter = fetched)
            else pkg
        }
    }

    // ── UiState ───────────────────────────────────────────────────────────────

    @OptIn(FlowPreview::class)
    val uiState = combine(
        enrichedPackagesFlow,
        _searchQuery.debounce(200),
        _selectedPackages,
        _includeSystemApps,
        shizukuStateHolder.isReady,
    ) { packages, query, selected, includeSystem, shizukuReady ->

        val textFiltered = if (query.isBlank()) packages else {
            val q = query.lowercase()
            packages.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        // Apenas valores reais (não "unknown") alimentam os chips de filtro.
        // Os chips só aparecem quando ao menos um fetch terminou com sucesso.
        val availableFilters = packages
            .map { it.compilationFilter }
            .filter { it != "unknown" }
            .distinct()
            .sorted()

        PackageListUiState(
            allPackages          = packages,
            textFilteredPackages = textFiltered,
            filteredPackages     = textFiltered,   // sobrescrito no segundo combine
            isLoading            = false,
            searchQuery          = query,
            selectedPackages     = selected,
            includeSystemApps    = includeSystem,
            shizukuReady         = shizukuReady,
            availableFilters     = availableFilters,
        )
    }.combine(
        // Estado auxiliar: filtro ativo + resetting + indicadores de loading
        combine(
            _activeCompilationFilter,
            _packagesResetting,
            _isFetchingFilters,
            _isLoadingPackages,
        ) { activeFilter, resetting, fetchingFilters, loadingPkgs ->
            AuxState(activeFilter, resetting, fetchingFilters, loadingPkgs)
        }
    ) { partial, aux ->
        val compilationFiltered = if (aux.activeFilter == null) {
            partial.textFilteredPackages
        } else {
            partial.textFilteredPackages.filter { it.compilationFilter == aux.activeFilter }
        }
        partial.copy(
            filteredPackages        = compilationFiltered,
            activeCompilationFilter = aux.activeFilter,
            packagesResetting       = aux.resetting,
            isFetchingFilters       = aux.fetchingFilters,
            isLoading               = aux.loadingPkgs,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = PackageListUiState(isLoading = true),
    )

    // ── Init: dispara fetchBulkFilters quando Shizuku conecta / reconecta ─────

    init {
        viewModelScope.launch {
            shizukuStateHolder.isReady.collect { shizukuReady ->
                fetchFiltersJob?.cancel()
                if (shizukuReady) {
                    logRepository.log(LogType.APP, "Shizuku conectado — iniciando fetchBulkFilters")
                    fetchBulkFilters()
                } else {
                    logRepository.log(LogType.APP, "Shizuku desconectado — cache de profiles limpo")
                    _isFetchingFilters.value = false
                    _compilationFilters.value = emptyMap()
                }
            }
        }
    }

    /**
     * Busca o estado de compilação de **todos** os pacotes com **uma única chamada Shizuku**:
     * `dumpsys package dexopt` → [PackageRepository.getBulkCompilationProfiles].
     *
     * Custo: 1 IPC round-trip + varredura linear do texto (~1-3 s no total).
     * Ao receber o Map completo, [_compilationFilters] é atualizado de uma só vez,
     * disparando uma única reemissão do [enrichedPackagesFlow] e do [uiState].
     */
    private fun fetchBulkFilters() {
        fetchFiltersJob = viewModelScope.launch {
            _isFetchingFilters.value = true
            _compilationFilters.value = emptyMap()
            try {
                val profiles = repository.getBulkCompilationProfiles()
                _compilationFilters.value = profiles
                val known = profiles.values.filter { it != "unknown" }.size
                logRepository.log(LogType.APP, "fetchBulkFilters concluído: ${profiles.size} profiles ($known com status conhecido)")
                Timber.d("fetchBulkFilters concluído: ${profiles.size} profiles")
            } catch (e: CancellationException) {
                Timber.d("fetchBulkFilters cancelado")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Erro em fetchBulkFilters")
            } finally {
                _isFetchingFilters.value = false
            }
        }
    }

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
            // Re-executa o bulk fetch para que os profiles reflitam o estado pós-refresh
            if (shizukuStateHolder.isReadyNow) {
                fetchBulkFilters()
            }
        }
    }

    /**
     * Define o filtro ativo de compilação. `null` = mostrar todos.
     * Limpa a seleção para evitar pacotes selecionados invisíveis.
     */
    fun onCompilationFilterChanged(filter: String?) {
        _activeCompilationFilter.value = filter
        _selectedPackages.value = emptySet()
    }

    /**
     * Executa `cmd package compile --reset <packageName>` via Shizuku.
     * Marca o pacote como "em reset" durante a operação e emite Snackbar ao concluir.
     */
    fun onResetPackage(packageName: String) {
        viewModelScope.launch {
            _packagesResetting.update { it + packageName }
            try {
                when (val result = repository.resetCompilation(packageName)) {
                    is CompilationResult.Success -> {
                        // Remove o filtro cacheado do pacote para que seja rebuscado na próxima vez
                        _compilationFilters.update { it - packageName }
                        _snackbarMessages.emit("Reset de $packageName concluído")
                    }
                    is CompilationResult.Failure ->
                        _snackbarMessages.emit("Falha no reset: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exceção ao resetar $packageName")
                _snackbarMessages.emit("Erro inesperado ao resetar $packageName")
            } finally {
                _packagesResetting.update { it - packageName }
            }
        }
    }

}

// ─────────────────────────────────────────────────────────────────────────────

/** Dados auxiliares agrupados para o segundo `combine` do uiState. */
private data class AuxState(
    val activeFilter:    String?,
    val resetting:       Set<String>,
    val fetchingFilters: Boolean,
    val loadingPkgs:     Boolean,
)

// ═══════════════════════════════════════════════════════════════════════════════
// UiState
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Estado imutável da tela de listagem de pacotes.
 *
 * @property allPackages              Lista completa sem nenhum filtro aplicado, com filtros
 *                                    de compilação enriquecidos progressivamente via Shizuku.
 * @property textFilteredPackages     Lista após aplicar [searchQuery] (sem filtro de compilação).
 * @property filteredPackages         Lista final após aplicar texto E [activeCompilationFilter].
 * @property isLoading                `true` enquanto o carregamento inicial não concluiu.
 * @property isFetchingFilters        `true` enquanto o fetch de compilationFilters via Shizuku
 *                                    está em andamento. Exibir [LinearProgressIndicator] na UI.
 * @property searchQuery              Texto atual de busca (debounced — para exibição de estado vazio).
 * @property selectedPackages         Conjunto de `packageName` selecionados para compilação em lote.
 * @property includeSystemApps        Se apps de sistema estão visíveis.
 * @property shizukuReady             Se Shizuku está conectado e com permissão.
 * @property availableFilters         Valores únicos de compilationFilter (excluindo "unknown")
 *                                    presentes na lista — alimenta os chips de filtro.
 *                                    Vazio enquanto nenhum filtro foi ainda buscado.
 * @property activeCompilationFilter  Filtro de compilação ativo, ou `null` para "todos".
 * @property packagesResetting        Conjunto de pacotes com reset em andamento.
 */
@Stable
data class PackageListUiState(
    val allPackages:             List<AppPackage> = emptyList(),
    val textFilteredPackages:    List<AppPackage> = emptyList(),
    val filteredPackages:        List<AppPackage> = emptyList(),
    val isLoading:               Boolean          = false,
    val isFetchingFilters:       Boolean          = false,
    val searchQuery:             String           = "",
    val selectedPackages:        Set<String>      = emptySet(),
    val includeSystemApps:       Boolean          = false,
    val shizukuReady:            Boolean          = false,
    val availableFilters:        List<String>     = emptyList(),
    val activeCompilationFilter: String?          = null,
    val packagesResetting:       Set<String>      = emptySet(),
) {
    val hasSelection:   Boolean get() = selectedPackages.isNotEmpty()
    val selectionCount: Int     get() = selectedPackages.size
}
