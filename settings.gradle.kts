/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

pluginManagement {
    repositories {
        // Google Maven — AGP, Compose Compiler, AndroidX, Hilt
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // KSP, Kotlin compiler plugins, Turbine, MockK, Coil, Shizuku
        mavenCentral()
        // Gradle plugin portal — fallback para plugins não encontrados acima
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Falha imediatamente se algum módulo declarar seus próprios repositórios,
    // forçando repositórios centralizados aqui.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AotCompilerManager"
include(":app")
