/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Tela de configurações — implementação completa no Prompt 05.
 * Exibe apenas informações da versão do app para o MVP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            ListItem(
                headlineContent   = { Text("Versão do app") },
                supportingContent = {
                    Text(
                        text  = "${com.aotmanager.app.BuildConfig.VERSION_NAME} (${com.aotmanager.app.BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            ListItem(
                headlineContent   = { Text("Build time") },
                supportingContent = {
                    Text(
                        text  = com.aotmanager.app.BuildConfig.BUILD_TIME,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Configurações completas") },
                supportingContent = { Text("Disponível no Prompt 05") },
            )
        }
    }
}
