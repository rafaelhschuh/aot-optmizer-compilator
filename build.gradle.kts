/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

// Plugins declarados com apply false — cada módulo os aplica individualmente
plugins {
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.kotlin.android)        apply false
    alias(libs.plugins.kotlin.compose)        apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.ksp)                   apply false
    alias(libs.plugins.hilt.android)          apply false
}
