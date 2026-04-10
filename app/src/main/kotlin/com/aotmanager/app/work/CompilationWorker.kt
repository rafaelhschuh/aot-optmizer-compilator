/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aotmanager.app.domain.model.CompilationProfile
import com.aotmanager.app.domain.model.CompilationResult
import com.aotmanager.app.domain.repository.PackageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Worker do WorkManager que executa a compilação AOT em background.
 *
 * Usa `@HiltWorker` + `@AssistedInject` para receber dependências via Hilt,
 * enquanto [Context] e [WorkerParameters] são injetados pelo WorkManager.
 *
 * ## Fluxo
 * 1. Recebe `packageName` e `profile` via [inputData].
 * 2. Exibe ForegroundService com notificação de progresso.
 * 3. Chama [PackageRepository.compilePackage].
 * 4. Retorna [Result.success] ou [Result.failure] com dados de output.
 */
@HiltWorker
class CompilationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val repository: PackageRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_PACKAGE_NAME = "package_name"
        const val KEY_PROFILE      = "profile"
        const val KEY_FORCE        = "force"
        const val KEY_OUTPUT       = "output"
        const val KEY_ERROR        = "error"
        const val KEY_DURATION_MS  = "duration_ms"

        private const val NOTIFICATION_ID      = 1001
        const val NOTIFICATION_CHANNEL_ID      = "compilation_channel"
    }

    override suspend fun doWork(): Result {
        val packageName = params.inputData.getString(KEY_PACKAGE_NAME)
            ?: return Result.failure(workDataOf(KEY_ERROR to "packageName ausente"))

        val profileValue = params.inputData.getString(KEY_PROFILE)
            ?: CompilationProfile.SPEED_PROFILE.cmdValue
        val profile = CompilationProfile.fromCmdValue(profileValue)
        val force   = params.inputData.getBoolean(KEY_FORCE, true)

        setForeground(buildForegroundInfo(packageName))
        Timber.d("CompilationWorker iniciado: $packageName ($profile)")

        return when (val result = repository.compilePackage(packageName, profile, force)) {
            is CompilationResult.Success -> {
                Timber.i("Compilação OK: $packageName em ${result.durationMs}ms")
                Result.success(
                    workDataOf(
                        KEY_OUTPUT      to result.output,
                        KEY_DURATION_MS to result.durationMs,
                    )
                )
            }
            is CompilationResult.Failure -> {
                Timber.e("Compilação falhou: $packageName — ${result.errorMessage}")
                // Retorna failure mas não requeued — compilação é operação única
                Result.failure(workDataOf(KEY_ERROR to result.errorMessage))
            }
        }
    }

    private fun buildForegroundInfo(packageName: String): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(com.aotmanager.app.R.string.notification_compiling_title))
            .setContentText(packageName)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(com.aotmanager.app.R.string.notification_channel_compilation),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(
                    com.aotmanager.app.R.string.notification_channel_compilation_description
                )
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
