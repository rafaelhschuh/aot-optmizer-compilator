/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.di

import android.content.Context
import android.content.pm.PackageManager
import com.aotmanager.app.data.log.LogRepositoryImpl
import com.aotmanager.app.data.repository.PackageRepositoryImpl
import com.aotmanager.app.domain.repository.LogRepository
import com.aotmanager.app.domain.repository.PackageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /** Liga [PackageRepository] à implementação concreta [PackageRepositoryImpl]. */
    @Binds
    @Singleton
    abstract fun bindPackageRepository(impl: PackageRepositoryImpl): PackageRepository

    /** Liga [LogRepository] à implementação em memória [LogRepositoryImpl]. */
    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: LogRepositoryImpl): LogRepository

    companion object {

        /**
         * Fornece o [PackageManager] do sistema via contexto da Application.
         * Injetado em [PackageRepositoryImpl] para listar pacotes instalados.
         */
        @Provides
        @Singleton
        fun providePackageManager(
            @ApplicationContext context: Context,
        ): PackageManager = context.packageManager
    }
}
