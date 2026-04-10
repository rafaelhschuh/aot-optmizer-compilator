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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilationScreen(
    onBack:    () -> Unit,
    viewModel: CompilationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Inicia compilação automaticamente ao entrar na tela
    LaunchedEffect(Unit) {
        viewModel.startCompilation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Compilando AOT")
                        Text(
                            text  = "${uiState.completed}/${uiState.total} • ${uiState.selectedProfile.displayName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isRunning) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {

            // ── Barra de progresso ────────────────────────────────────────
            LinearProgressIndicator(
                progress    = { uiState.progress },
                modifier    = Modifier.fillMaxWidth(),
            )

            // ── Resumo final ──────────────────────────────────────────────
            if (uiState.isDone) {
                CompletionSummary(uiState)
            }

            // ── Lista de resultados ───────────────────────────────────────
            LazyColumn(
                contentPadding    = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.items, key = { it.packageName }) { item ->
                    CompilationItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun CompletionSummary(uiState: CompilationUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.failureCount == 0)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text  = if (uiState.failureCount == 0) "Compilação concluída ✓"
                            else "Concluído com erros",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text  = "${uiState.successCount} sucesso · ${uiState.failureCount} erro(s)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CompilationItemCard(item: CompilationItemState) {
    val containerColor = when (item.status) {
        CompilationStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        CompilationStatus.FAILURE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        CompilationStatus.RUNNING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        CompilationStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status icon
            when (item.status) {
                CompilationStatus.PENDING -> Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = "Aguardando",
                    tint   = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                CompilationStatus.RUNNING -> CircularProgressIndicator(
                    modifier      = Modifier.size(24.dp),
                    strokeWidth   = 2.dp,
                )
                CompilationStatus.SUCCESS -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Sucesso",
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                CompilationStatus.FAILURE -> Icon(
                    Icons.Default.Error,
                    contentDescription = "Erro",
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = item.packageName,
                    style    = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.output.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = item.output,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.status == CompilationStatus.SUCCESS && item.duration > 0) {
                    Text(
                        text  = "${item.duration}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
