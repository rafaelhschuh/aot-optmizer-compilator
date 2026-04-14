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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotmanager.app.domain.model.LogEntry
import com.aotmanager.app.domain.model.LogType

// ─────────────────────────────────────────────────────────────────────────────
// Tela principal
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack:    () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val appLogs     by viewModel.appLogs.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()

    var selectedTab       by remember { mutableIntStateOf(0) }
    var showClearDialog   by remember { mutableStateOf(false) }
    val snackbarHostState  = remember { SnackbarHostState() }
    val clipboardManager   = LocalClipboardManager.current

    val visibleLogs = if (selectedTab == 0) appLogs else commandLogs

    // Auto-scroll para o final quando chegam novos logs
    val listState = rememberLazyListState()
    LaunchedEffect(visibleLogs.size) {
        if (visibleLogs.isNotEmpty()) {
            listState.animateScrollToItem(visibleLogs.lastIndex)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Logs internos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Copia os logs visíveis para a área de transferência
                    IconButton(
                        onClick = {
                            val text = visibleLogs.joinToString("\n") { entry ->
                                "[${entry.formattedTime()}][${entry.type.label}] ${entry.message}"
                            }
                            clipboardManager.setText(AnnotatedString(text))
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar logs")
                    }
                    // Limpar (com confirmação)
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar todos os logs")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Tabs ──────────────────────────────────────────────────────────
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = {
                        Row(
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("App")
                            if (appLogs.isNotEmpty()) {
                                Badge { Text("${appLogs.size}") }
                            }
                        }
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = {
                        Row(
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Comandos")
                            if (commandLogs.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                ) {
                                    Text("${commandLogs.size}")
                                }
                            }
                        }
                    },
                )
            }

            // ── Lista de logs ─────────────────────────────────────────────────
            if (visibleLogs.isEmpty()) {
                Box(
                    modifier        = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = "Nenhum log ainda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state          = listState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        items = visibleLogs,
                        key   = { it.id },
                    ) { entry ->
                        LogEntryItem(entry = entry)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }

    // ── Diálogo de confirmação de limpeza ─────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title            = { Text("Limpar logs") },
            text             = { Text("Todos os logs (App e Comandos) serão removidos. Confirmar?") },
            confirmButton    = {
                TextButton(onClick = {
                    viewModel.clearLogs()
                    showClearDialog = false
                }) {
                    Text("Limpar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Item individual de log
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val (typeBadgeColor, typeOnColor) = when (entry.type) {
        LogType.APP     -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LogType.COMMAND -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Coluna esquerda: timestamp + badge de tipo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.width(60.dp),
        ) {
            Text(
                text  = entry.formattedTime().take(8),   // HH:mm:ss
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
            Text(
                text  = entry.formattedTime().drop(8),   // .SSS
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
            Box(
                modifier          = Modifier
                    .background(typeBadgeColor, shape = MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment  = Alignment.Center,
            ) {
                Text(
                    text  = entry.type.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = typeOnColor,
                    fontSize = 9.sp,
                )
            }
        }

        // Coluna direita: mensagem — scroll horizontal para outputs longos em linha única
        val scrollState = rememberScrollState()
        Text(
            text     = entry.message,
            style    = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize   = 11.sp,
                lineHeight = 16.sp,
            ),
            color    = if (entry.message.startsWith("✗"))
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/** Exibe o par (Color, Color) de container e onContainer para um tipo de log. */
@Composable
private fun logTypeColors(type: LogType): Pair<Color, Color> = when (type) {
    LogType.APP     -> MaterialTheme.colorScheme.primaryContainer  to MaterialTheme.colorScheme.onPrimaryContainer
    LogType.COMMAND -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}
