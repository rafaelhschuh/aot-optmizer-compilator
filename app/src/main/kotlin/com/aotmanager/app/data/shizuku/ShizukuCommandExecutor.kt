/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.data.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.aotmanager.app.BuildConfig
import com.aotmanager.app.domain.model.CompilationProfile
import com.aotmanager.app.service.IRemoteService
import com.aotmanager.app.service.RemoteServiceImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executa comandos privilegiados via Shizuku UserService.
 *
 * ## Arquitetura
 * ```
 * App Process (UID normal)
 *   └─ ShizukuCommandExecutor (Hilt Singleton)
 *          │  Shizuku.bindUserService()
 *          ▼
 * Shizuku Process (UID 2000 = shell)
 *   └─ RemoteServiceImpl (IRemoteService.Stub)
 *          │  ProcessBuilder(args).start()
 *          ▼
 * cmd package compile -m speed-profile -f com.example.app
 * ```
 *
 * O UserService é instanciado pelo Shizuku usando o class loader do app.
 * A comunicação é via Binder (AIDL), o que garante que os dados são
 * serializados de forma segura entre processos.
 *
 * ## Por que UserService e não Shizuku.newProcess()?
 * Em Shizuku 13.x, `newProcess()` está anotado com `@RestrictTo(LIBRARY_GROUP_PREFIX)`.
 * O UserService é a API oficial e suportada para execução de código privilegiado.
 */
@Singleton
class ShizukuCommandExecutor @Inject constructor(
    private val shizukuStateHolder: ShizukuStateHolder,
) {
    private var remoteService: IRemoteService? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, RemoteServiceImpl::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("remote_service")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    /** Conexão Binder com o UserService. Chamada no processo Shizuku. */
    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remoteService = IRemoteService.Stub.asInterface(binder)
            Timber.i("ShizukuRemoteService conectado")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remoteService = null
            Timber.w("ShizukuRemoteService desconectado")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Faz bind do UserService. Chamar quando `ShizukuStateHolder.isReady == true`. */
    fun bindService() {
        if (!Shizuku.pingBinder()) {
            Timber.w("bindService chamado mas Shizuku não está disponível")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Timber.w("bindService chamado mas sem permissão")
            return
        }
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            Timber.d("bindUserService solicitado")
        } catch (e: Exception) {
            Timber.e(e, "Falha ao solicitar bindUserService")
        }
    }

    /** Libera o UserService. Chamar em onDestroy da Activity. */
    fun unbindService() {
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (e: Exception) {
            Timber.w(e, "Falha ao unbindUserService — ignorado")
        }
    }

    // ── Comandos públicos ─────────────────────────────────────────────────────

    /**
     * Executa `cmd package compile -m <profile> [-f] <packageName>`.
     *
     * @return Saída do comando.
     * @throws ShizukuNotReadyException se o UserService não está conectado.
     */
    suspend fun executeCompile(
        packageName: String,
        profile: CompilationProfile,
        force: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val args = buildList {
            add("cmd"); add("package"); add("compile")
            add("-m"); add(profile.cmdValue)
            if (force) add("-f")
            add(packageName)
        }.toTypedArray()

        Timber.d("compile: ${args.joinToString(" ")}")
        val start = System.currentTimeMillis()
        val out   = runRemote(args)
        Timber.i("compile $packageName: ${System.currentTimeMillis() - start}ms → ${out.take(80)}")
        out
    }

    /**
     * Executa `cmd package dump <packageName>` para consultar status de compilação.
     * Saída é grande — o parser usa apenas a seção "Dexopt state".
     */
    suspend fun dumpPackage(packageName: String): String = withContext(Dispatchers.IO) {
        runRemote(arrayOf("cmd", "package", "dump", packageName))
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    private suspend fun runRemote(args: Array<String>): String {
        // Aguarda até 5s o service conectar (pode ter lag de binding)
        val svc = waitForService(timeoutMs = 5_000)
            ?: throw ShizukuNotReadyException(
                "UserService não conectou após 5s. Verifique se Shizuku está ativo e com permissão concedida."
            )
        return svc.execute(args)
    }

    /**
     * Aguarda [timeoutMs] ms pelo service ficar não-null via polling.
     * O binding do UserService é assíncrono — poll a cada 200ms até o timeout.
     */
    private suspend fun waitForService(timeoutMs: Long): IRemoteService? =
        withTimeoutOrNull(timeoutMs) {
            while (remoteService == null) {
                delay(200)
            }
            remoteService
        } ?: remoteService
}

/** Lançada quando Shizuku ou o UserService não estão disponíveis. */
class ShizukuNotReadyException(message: String) : Exception(message)

/** Lançada em erros de execução do processo remoto. */
class ShizukuExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
