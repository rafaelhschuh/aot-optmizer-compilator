/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aotmanager.app.domain.model.CompilationProfile
import com.aotmanager.app.ui.compilation.CompilationScreen
import com.aotmanager.app.ui.logs.LogsScreen
import com.aotmanager.app.ui.packagelist.PackageListScreen
import com.aotmanager.app.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════════════════
// Routes
// ═══════════════════════════════════════════════════════════════════════════════

sealed interface AppRoute {

    @Serializable
    data object PackageList : AppRoute

    @Serializable
    data class Compilation(
        val packageNames: List<String>,
        val profile:      String  = CompilationProfile.SPEED_PROFILE.cmdValue,
        val force:        Boolean = true,
    ) : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object Logs : AppRoute
}

// ═══════════════════════════════════════════════════════════════════════════════
// NavHost
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AppNavHost(
    modifier:      Modifier           = Modifier,
    navController: NavHostController  = rememberNavController(),
) {
    NavHost(
        navController    = navController,
        startDestination = AppRoute.PackageList,
        modifier         = modifier,
    ) {

        composable<AppRoute.PackageList> {
            PackageListScreen(
                onCompileSelected = { packageNames, profile ->
                    navController.navigate(
                        AppRoute.Compilation(
                            packageNames = packageNames,
                            profile      = profile.cmdValue,
                        )
                    )
                },
                onSettingsClick = { navController.navigate(AppRoute.Settings) },
            )
        }

        composable<AppRoute.Compilation> {
            CompilationScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<AppRoute.Settings> {
            SettingsScreen(
                onBack     = { navController.popBackStack() },
                onLogsClick = { navController.navigate(AppRoute.Logs) },
            )
        }

        composable<AppRoute.Logs> {
            LogsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
