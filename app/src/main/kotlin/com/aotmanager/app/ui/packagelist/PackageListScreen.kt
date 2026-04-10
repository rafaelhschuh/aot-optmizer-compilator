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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotmanager.app.domain.model.AppPackage
import com.aotmanager.app.domain.model.CompilationProfile

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

    var showProfilePicker by remember { mutableStateOf(false) }

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
            // ── Barra de busca ────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar pacote…") },
                leadingIcon  = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
            )

            // ── Filtro: apps de sistema ───────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.includeSystemApps,
                    onClick  = { viewModel.onIncludeSystemAppsToggled(!uiState.includeSystemApps) },
                    label    = { Text("Apps de sistema") },
                    leadingIcon = if (uiState.includeSystemApps) ({
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }) else null,
                )
            }

            // ── Banner Shizuku ────────────────────────────────────────────
            if (!uiState.shizukuReady) {
                ShizukuBanner()
            }

            // ── Conteúdo principal ────────────────────────────────────────
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.filteredPackages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text  = if (uiState.searchQuery.isBlank()) "Nenhum pacote encontrado"
                                    else "Sem resultados para \"${uiState.searchQuery}\"",
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
                            PackageListItem(
                                pkg        = pkg,
                                isSelected = pkg.packageName in uiState.selectedPackages,
                                onToggle   = { viewModel.onPackageSelectionToggled(pkg.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Profile Picker Dialog ─────────────────────────────────────────────────
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

@Composable
private fun PackageListItem(
    pkg:       AppPackage,
    isSelected: Boolean,
    onToggle:   () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onToggle() },
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
            Icon(
                imageVector         = Icons.Default.Android,
                contentDescription  = null,
                tint                = MaterialTheme.colorScheme.primary,
                modifier            = Modifier.size(40.dp),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pkg.compilationFilter != "unknown") {
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text  = pkg.compilationFilter,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
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

@Composable
private fun ShizukuBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
