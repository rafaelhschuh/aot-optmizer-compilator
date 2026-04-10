/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.0+: compilador Compose como plugin independente
    alias(libs.plugins.kotlin.compose)
    // Serialization para type-safe Navigation routes e export JSON
    alias(libs.plugins.kotlin.serialization)
    // KSP para geração de código Hilt em compile time (substitui kapt)
    alias(libs.plugins.ksp)
    // Plugin Hilt deve vir APÓS ksp
    alias(libs.plugins.hilt.android)
}

android {
    namespace   = "com.aotmanager.app"
    compileSdk  = 35

    defaultConfig {
        applicationId   = "com.aotmanager.app"
        minSdk          = 28   // Android 9 — requisito mínimo para cmd package compile -m
        targetSdk       = 35
        versionCode     = 1
        versionName     = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expõe buildTime para logs/UI sem precisar de hardcoded string
        buildConfigField("String", "BUILD_TIME", "\"${System.currentTimeMillis()}\"")
    }

    buildTypes {
        debug {
            isDebuggable      = true
            isMinifyEnabled   = false
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }

        release {
            isDebuggable      = false
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Assinatura configurada via keystore.properties (veja README)
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }

    buildFeatures {
        compose     = true
        buildConfig = true
        // AIDL necessário para IRemoteService (UserService do Shizuku)
        // Desabilitado por padrão no AGP 8.x
        aidl        = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
            )
        }
    }

    // KSP gera código para Hilt dentro do diretório de build do módulo
    // Não é necessário configurar sourceSets manualmente — o plugin KSP faz isso.
}

dependencies {
    // ── Core AndroidX ─────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // ── Lifecycle ─────────────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // ── Compose ───────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // ── Navigation ────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── DataStore ─────────────────────────────────────────────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── WorkManager ───────────────────────────────────────────────────────
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    // Compilador para @HiltWorker (androidx.hilt, não o compilador Dagger principal)
    ksp(libs.androidx.hilt.compiler)

    // ── Hilt ──────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    // Compilador principal do Hilt/Dagger — processa @HiltAndroidApp, @Inject, etc.
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Shizuku ───────────────────────────────────────────────────────────
    // api: interfaces Binder, ShizukuRemoteProcess, permission helpers
    implementation(libs.shizuku.api)
    // provider: ShizukuProvider (ContentProvider para IPC com o serviço Shizuku)
    implementation(libs.shizuku.provider)

    // ── Image Loading ─────────────────────────────────────────────────────
    // Carrega ícones de apps instalados de forma eficiente com cache em disco
    implementation(libs.coil.compose)

    // ── Serialization ─────────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── Coroutines ────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Logging ───────────────────────────────────────────────────────────
    implementation(libs.timber)

    // ── Debug ─────────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ── Unit Tests ────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    // ── Instrumented Tests ────────────────────────────────────────────────
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
}
