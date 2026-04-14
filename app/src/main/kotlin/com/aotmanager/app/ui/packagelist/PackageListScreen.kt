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

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotmanager.app.domain.model.AppPackage
import com.aotmanager.app.domain.model.CompilationProfile
import com.aotmanager.app.ui.theme.ProfileInterpretOnly
import com.aotmanager.app.ui.theme.ProfileQuicken
import com.aotmanager.app.ui.theme.ProfileSpeed
import com.aotmanager.app.ui.theme.ProfileSpeedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageListScreen(
    onCompileSelected: (List<String>, CompilationProfile) -> Unit,
    onSettingsClick:   () -> Unit,
    viewModel: PackageListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Coleta eventos de Snackbar do ViewModel ───────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var showProfilePicker by remember { mutableStateOf(false) }

    // Estado local do TextField para preservar cursor/seleção sem depender do debounce do ViewModel.
    // O ViewModel recebe apenas o texto (String) para filtragem; o TextFieldValue fica na UI.
    var searchFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("AOT Compiler") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.onRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar lista")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.hasSelection,
                enter   = fadeIn(),
                exit    = fadeOut(),
            ) {
                BottomAppBar(
                    actions = {
                        TextButton(onClick = { viewModel.onDeselectAll() }) {
                            Text("Limpar (${uiState.selectionCount})")
                        }
                        TextButton(onClick = { viewModel.onSelectAll() }) {
                            Text("Todos")
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showProfilePicker = true }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                Icon(Icons.Outlined.BuildCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Compilar ${uiState.selectionCount}")
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Barra de busca ────────────────────────────────────────────────
            // Usa TextFieldValue local para preservar posição do cursor e seleção.
            // Envia apenas o texto (String) ao ViewModel para filtragem debounced.
            OutlinedTextField(
                value = searchFieldValue,
                onValueChange = { newValue ->
                    searchFieldValue = newValue
                    viewModel.onSearchQueryChanged(newValue.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar pacote…") },
                leadingIcon  = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchFieldValue.text.isNotEmpty()) {
                        IconButton(onClick = {
                            searchFieldValue = TextFieldValue("")
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
            )

            // ── Filtros: apps de sistema + estado de compilação ───────────────
            LazyRow(
                modifier              = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding        = PaddingValues(end = 8.dp),
            ) {
                // Chip: apps de sistema
                item {
                    FilterChip(
                        selected = uiState.includeSystemApps,
                        onClick  = { viewModel.onIncludeSystemAppsToggled(!uiState.includeSystemApps) },
                        label    = { Text("Sistema") },
                        leadingIcon = if (uiState.includeSystemApps) ({
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }) else null,
                    )
                }

                // Chips de profile só aparecem após ao menos um filter ter sido buscado.
                // `availableFilters` exclui "unknown" — apenas valores reais do ART.
                if (uiState.availableFilters.isNotEmpty()) {
                    // Chip "Todos" — limpa o filtro de compilação ativo
                    item {
                        FilterChip(
                            selected    = uiState.activeCompilationFilter == null,
                            onClick     = { viewModel.onCompilationFilterChanged(null) },
                            label       = { Text("Todos") },
                            leadingIcon = if (uiState.activeCompilationFilter == null) ({
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }) else null,
                        )
                    }

                    // Um FilterChip por valor único de compilationFilter encontrado
                    items(uiState.availableFilters) { filter ->
                        val isActive = uiState.activeCompilationFilter == filter
                        val chipColor = compilationFilterBadgeColor(filter)
                        FilterChip(
                            selected    = isActive,
                            onClick     = { viewModel.onCompilationFilterChanged(if (isActive) null else filter) },
                            label       = {
                                Text(
                                    text  = filter,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (isActive) chipColor else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = if (isActive) ({
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = chipColor)
                            }) else null,
                        )
                    }
                }
            }

            // ── Indicador de fetch de filtros de compilação ───────────────────
            // Barra linear sutil exibida enquanto o ViewModel consulta cmd package dump
            // para cada pacote via Shizuku. Desaparece ao concluir.
            AnimatedVisibility(
                visible = uiState.isFetchingFilters,
                enter   = fadeIn(),
                exit    = fadeOut(),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 16.dp),
                    color            = MaterialTheme.colorScheme.primary,
                    trackColor       = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // ── Banner Shizuku ────────────────────────────────────────────────
            if (!uiState.shizukuReady) {
                ShizukuBanner()
            }

            // ── Conteúdo principal ────────────────────────────────────────────
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.filteredPackages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text  = if (uiState.searchQuery.isBlank() && uiState.activeCompilationFilter == null)
                                        "Nenhum pacote encontrado"
                                    else
                                        "Sem resultados para os filtros aplicados",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(
                            items = uiState.filteredPackages,
                            key   = { it.packageName },
                        ) { pkg ->
                            // Lambdas remembered por packageName para evitar recomposições desnecessárias
                            val onToggle = remember(pkg.packageName) {
                                { viewModel.onPackageSelectionToggled(pkg.packageName) }
                            }
                            val onReset = remember(pkg.packageName) {
                                { viewModel.onResetPackage(pkg.packageName) }
                            }
                            PackageListItem(
                                pkg          = pkg,
                                isSelected   = pkg.packageName in uiState.selectedPackages,
                                isResetting  = pkg.packageName in uiState.packagesResetting,
                                onToggle     = onToggle,
                                onReset      = onReset,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Profile Picker Dialog ─────────────────────────────────────────────────────
    if (showProfilePicker) {
        ProfilePickerDialog(
            onDismiss = { showProfilePicker = false },
            onProfileSelected = { profile ->
                showProfilePicker = false
                onCompileSelected(uiState.selectedPackages.toList(), profile)
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Item da lista de pacotes com:
 * - Ícone real do app carregado assincronamente via Coil + PackageManager
 * - Badge colorido com o estado de compilação atual
 * - Botão de reset de compilação com indicador de loading
 * - Seleção via Checkbox
 */
@Composable
private fun PackageListItem(
    pkg:         AppPackage,
    isSelected:  Boolean,
    isResetting: Boolean,
    onToggle:    () -> Unit,
    onReset:     () -> Unit,
) {
    val context = LocalContext.current

    // Carrega o ícone do app assincronamente no Dispatchers.IO para não bloquear a Main Thread.
    // `produceState` é restartado apenas quando `packageName` muda (key1).
    val icon by produceState<Drawable?>(initialValue = null, key1 = pkg.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(pkg.packageName)
            }.getOrNull()
        }
    }

    val fallbackPainter = rememberVectorPainter(Icons.Default.Android)

    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = {
            Text(
                text     = pkg.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text  = pkg.packageName,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            // AsyncImage lida com todos os tipos de Drawable (incluindo AdaptiveIconDrawable).
            // Enquanto o ícone não carrega, exibe o ícone Android como fallback.
            AsyncImage(
                model              = icon,
                contentDescription = pkg.label,
                fallback           = fallbackPainter,
                error              = fallbackPainter,
                modifier           = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        },
        trailingContent = {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Badge colorido com o filtro de compilação atual
                if (pkg.compilationFilter != "unknown") {
                    val badgeContainerColor = compilationFilterBadgeColor(pkg.compilationFilter)
                    Badge(containerColor = badgeContainerColor) {
                        Text(
                            text  = pkg.compilationFilter,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }

                // Botão de reset — mostra CircularProgressIndicator enquanto o reset ocorre
                if (isResetting) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick  = onReset,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.RestartAlt,
                            contentDescription = "Resetar compilação de ${pkg.label}",
                            modifier           = Modifier.size(18.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Checkbox(
                    checked         = isSelected,
                    onCheckedChange = { onToggle() },
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * Mapeia o valor do filtro de compilação para uma cor semântica de badge.
 * Cores definidas em [com.aotmanager.app.ui.theme.Color].
 */
@Composable
private fun compilationFilterBadgeColor(filter: String): Color = when (filter) {
    "speed"          -> ProfileSpeed
    "speed-profile"  -> ProfileSpeedProfile
    "quicken"        -> ProfileQuicken
    "everything"     -> ProfileSpeed
    "interpret-only" -> ProfileInterpretOnly
    "verify"         -> MaterialTheme.colorScheme.tertiary
    "verify-none"    -> MaterialTheme.colorScheme.error
    else             -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun ShizukuBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier           = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment  = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Outlined.Warning,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    text  = "Shizuku não disponível",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text  = "Inicie o Shizuku para habilitar compilação AOT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ProfilePickerDialog(
    onDismiss:         () -> Unit,
    onProfileSelected: (CompilationProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(CompilationProfile.SPEED_PROFILE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Perfil de compilação") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text  = "Perfil selecionado: ${selected.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text  = selected.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    onClick  = { expanded = true },
                    shape    = MaterialTheme.shapes.small,
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = "▼ ${selected.cmdValue}",
                        modifier = Modifier.padding(12.dp),
                        style    = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        CompilationProfile.entries.forEach { profile ->
                            DropdownMenuItem(
                                text    = { Text("${profile.displayName} (${profile.cmdValue})") },
                                onClick = {
                                    selected  = profile
                                    expanded  = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onProfileSelected(selected) }) {
                Text("Compilar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
