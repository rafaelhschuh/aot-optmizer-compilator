/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Ponto de entrada da aplicação AOT Compiler Manager.
 *
 * Responsabilidades:
 * - Inicializar o grafo de injeção de dependências via Hilt ([HiltAndroidApp]).
 * - Configurar o logger [Timber] com árvore adequada ao tipo de build.
 * - Inicializar o [WorkManager] com factory do Hilt para suporte a [HiltWorker].
 *
 * A anotação [HiltAndroidApp] dispara a geração de código KSP que cria o
 * componente Hilt raiz, do qual todos os outros componentes de injeção dependem.
 * Sem ela, qualquer `@AndroidEntryPoint` ou `@Inject` falhará em runtime.
 */
@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {

    /**
     * Factory do WorkManager injetada pelo Hilt.
     * Necessária para que [androidx.hilt.work.HiltWorker] receba suas
     * dependências injetadas via [Inject] no construtor do Worker.
     */
    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        initLogging()
    }

    /**
     * Provê a [Configuration] customizada do WorkManager com a [HiltWorkerFactory].
     *
     * Ao implementar [Configuration.Provider] na Application, o WorkManager é
     * inicializado de forma lazy na primeira chamada, usando esta configuração
     * em vez da padrão — o que é obrigatório quando se usa [HiltWorker].
     *
     * IMPORTANTE: ao usar este mecanismo, o initializer padrão do WorkManager
     * deve ser removido do manifesto (já feito via merge de manifesto do AGP
     * quando o WorkManager é importado com `work-runtime-ktx`). Se houver
     * conflito, adicione em AndroidManifest.xml:
     * ```xml
     * <provider
     *     android:name="androidx.startup.InitializationProvider"
     *     android:authorities="${applicationId}.androidx-startup"
     *     tools:node="remove" />
     * ```
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO
            )
            .build()

    /**
     * Configura o [Timber] com a árvore correta para o tipo de build.
     *
     * - Debug: [Timber.DebugTree] com tag automática baseada no nome da classe.
     * - Release: sem árvore plantada — logs são removidos pelo R8 via ProGuard
     *   (`-assumenosideeffects` no proguard-rules.pro) e não emitidos em runtime.
     */
    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Em release: nenhuma árvore — R8 remove chamadas Timber.d() e Timber.v()
        // em compile time. Timber.i/w/e podem ser plantados em CrashReporter futuro.
    }
}
