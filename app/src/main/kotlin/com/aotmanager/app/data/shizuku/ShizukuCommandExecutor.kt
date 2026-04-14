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
import com.aotmanager.app.domain.model.LogType
import com.aotmanager.app.domain.repository.LogRepository
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
 * Executa comandos privilegiados via Shizuku UserService e registra cada operação
 * no [LogRepository] para diagnóstico em runtime.
 *
 * ## Log de COMMAND
 * ```
 * → cmd package compile -m speed-profile -f com.example.app
 * ← Success (42ms)
 *
 * → dumpsys package dexopt
 * ← [3421 chars | 178 linhas] (1823ms)
 *   Dexopt state:
 *     [com.android.phone]
 *       arm64: [status=speed-profile]…  (primeiras 30 linhas)
 *   [TRUNCADO — 148 linhas omitidas]
 * ```
 */
@Singleton
class ShizukuCommandExecutor @Inject constructor(
    private val shizukuStateHolder: ShizukuStateHolder,
    private val logRepository: LogRepository,
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
     * Loga o comando e a resposta no [LogRepository] (tipo COMMAND).
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
        val ms    = System.currentTimeMillis() - start
        Timber.i("compile $packageName: ${ms}ms → ${out.take(80)}")
        out
    }

    /**
     * Executa `cmd package dump <packageName>` para consultar status individual.
     * Saída longa — o parser usa apenas a seção "Dexopt state".
     */
    suspend fun dumpPackage(packageName: String): String = withContext(Dispatchers.IO) {
        runRemote(arrayOf("cmd", "package", "dump", packageName))
    }

    /**
     * Executa `dumpsys package dexopt` para obter o estado de compilação de TODOS os pacotes
     * em uma única chamada Shizuku — O(1) IPC.
     *
     * ## Log gerado
     * - Linha 1: o comando executado
     * - Linha 2: resumo (chars, linhas, duração)
     * - Linhas seguintes: primeiras [BULK_LOG_PREVIEW_LINES] linhas do output raw
     *   (essencial para diagnosticar variações de formato entre ROMs/versões Android)
     *
     * ## Por que `dumpsys` e não `cmd package dump dexopt`?
     * `cmd package dump dexopt` pode tratar "dexopt" como nome de pacote em versões
     * mais antigas — `dumpsys package dexopt` é estável em Android 9–14.
     */
    suspend fun executeBulkDexoptDump(): String = withContext(Dispatchers.IO) {
        val cmd = "dumpsys package dexopt"
        logRepository.log(LogType.COMMAND, "→ $cmd")
        Timber.d("Iniciando bulk dexopt dump")

        val start = System.currentTimeMillis()
        val out: String = try {
            runRemote(arrayOf("dumpsys", "package", "dexopt"))
        } catch (e: Exception) {
            val errMsg = "✗ ERRO: ${e.javaClass.simpleName}: ${e.message}"
            logRepository.log(LogType.COMMAND, errMsg)
            Timber.e(e, "Falha no bulk dexopt dump")
            throw e
        }
        val ms    = System.currentTimeMillis() - start
        val lines = out.lines()

        // Resumo de metadados
        logRepository.log(
            LogType.COMMAND,
            "← [${out.length} chars | ${lines.size} linhas | ${ms}ms]"
        )

        // Preview das primeiras linhas — imprescindível para diagnosticar o formato do output
        val preview = lines.take(BULK_LOG_PREVIEW_LINES).joinToString("\n")
        val truncNote = if (lines.size > BULK_LOG_PREVIEW_LINES)
            "\n… [TRUNCADO — ${lines.size - BULK_LOG_PREVIEW_LINES} linhas omitidas]"
        else ""
        logRepository.log(LogType.COMMAND, preview + truncNote)

        Timber.i("Bulk dexopt dump: ${out.length} chars, ${lines.size} linhas, ${ms}ms")
        out
    }

    /**
     * Executa `cmd package compile --reset <packageName>`.
     * Loga o comando e resultado (tipo COMMAND).
     */
    suspend fun resetCompilation(packageName: String): String = withContext(Dispatchers.IO) {
        Timber.d("reset: $packageName")
        val svc = waitForService(timeoutMs = 5_000)
            ?: throw ShizukuNotReadyException(
                "UserService não conectou após 5s. Verifique se Shizuku está ativo."
            )
        val start = System.currentTimeMillis()
        val out = svc.resetCompilation(packageName)
        Timber.i("reset $packageName: ${System.currentTimeMillis() - start}ms → ${out.take(80)}")
        out
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    /**
     * Ponto central de execução: loga o comando e a resposta (ou exceção) para
     * todos os métodos exceto [executeBulkDexoptDump] (que tem log especializado).
     */
    private suspend fun runRemote(args: Array<String>): String {
        val cmdStr = args.joinToString(" ")
        logRepository.log(LogType.COMMAND, "→ $cmdStr")

        val svc = waitForService(timeoutMs = 5_000)
            ?: run {
                val err = "✗ ShizukuNotReady ao executar: $cmdStr"
                logRepository.log(LogType.COMMAND, err)
                throw ShizukuNotReadyException(
                    "UserService não conectou após 5s. Verifique se Shizuku está ativo e com permissão concedida."
                )
            }

        return try {
            val out = svc.execute(args)
            val preview = if (out.length <= CMD_LOG_PREVIEW_CHARS) out
                          else out.take(CMD_LOG_PREVIEW_CHARS) + "\n… [${out.length} chars total]"
            logRepository.log(LogType.COMMAND, "← $preview")
            out
        } catch (e: Exception) {
            logRepository.log(LogType.COMMAND, "✗ EXCEÇÃO: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private suspend fun waitForService(timeoutMs: Long): IRemoteService? =
        withTimeoutOrNull(timeoutMs) {
            while (remoteService == null) {
                delay(200)
            }
            remoteService
        } ?: remoteService

    companion object {
        /** Chars máximos logados para respostas curtas (compile, reset, dump individual). */
        private const val CMD_LOG_PREVIEW_CHARS = 400

        /** Linhas do output bulk logadas para diagnóstico do parser. */
        private const val BULK_LOG_PREVIEW_LINES = 60
    }
}

class ShizukuNotReadyException(message: String) : Exception(message)
class ShizukuExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
