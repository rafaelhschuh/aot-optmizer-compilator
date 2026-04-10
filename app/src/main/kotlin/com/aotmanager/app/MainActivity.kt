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

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aotmanager.app.data.shizuku.ShizukuCommandExecutor
import com.aotmanager.app.data.shizuku.ShizukuStateHolder
import com.aotmanager.app.navigation.AppNavHost
import com.aotmanager.app.ui.theme.AotCompilerManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import rikka.shizuku.Shizuku
import timber.log.Timber
import javax.inject.Inject

/**
 * Activity principal do AOT Compiler Manager.
 *
 * Gerencia o ciclo de vida dos listeners Shizuku e propaga o estado de
 * prontidão para o resto do app via [ShizukuStateHolder].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1001
    }

    @Inject
    lateinit var shizukuStateHolder: ShizukuStateHolder

    @Inject
    lateinit var shizukuCommandExecutor: ShizukuCommandExecutor

    // ── Listeners Shizuku ─────────────────────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Timber.d("Shizuku binder recebido")
        updateShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Timber.w("Shizuku binder morreu")
        shizukuStateHolder.setReady(false)
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Timber.i("Permissão Shizuku: ${if (granted) "concedida" else "negada"}")
                shizukuStateHolder.setReady(granted)
                if (granted) shizukuCommandExecutor.bindService()
            }
        }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        setContent {
            AotCompilerManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shizukuCommandExecutor.unbindService()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    // ── Shizuku ───────────────────────────────────────────────────────────────

    private fun updateShizukuState() {
        if (!Shizuku.pingBinder()) {
            shizukuStateHolder.setReady(false)
            return
        }
        if (Shizuku.isPreV11()) {
            shizukuStateHolder.setReady(true)
            return
        }
        when (Shizuku.checkSelfPermission()) {
            PackageManager.PERMISSION_GRANTED -> {
                shizukuStateHolder.setReady(true)
                shizukuCommandExecutor.bindService()
            }
            else -> {
                shizukuStateHolder.setReady(false)
                if (!Shizuku.shouldShowRequestPermissionRationale()) {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
            }
        }
    }
}
