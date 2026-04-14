/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.domain.repository

import com.aotmanager.app.domain.model.AppPackage
import com.aotmanager.app.domain.model.CompilationProfile
import com.aotmanager.app.domain.model.CompilationResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato do repositório de pacotes Android.
 *
 * Abstrai o acesso ao [android.content.pm.PackageManager] e aos comandos
 * privilegiados executados via Shizuku, permitindo substituição por fakes
 * em testes unitários sem Shizuku ou Android runtime.
 */
interface PackageRepository {

    /**
     * Retorna um [Flow] reativo com a lista de pacotes instalados.
     *
     * A lista é emitida uma vez ao ser coletada e pode ser re-emitida
     * chamando [refresh]. O Flow não fecha sozinho — use `stateIn` no ViewModel.
     *
     * @param includeSystemApps Se `true`, inclui apps da partição system/priv-app.
     */
    fun getInstalledPackages(includeSystemApps: Boolean): Flow<List<AppPackage>>

    /**
     * Força recarregamento da lista de pacotes.
     * Emite um novo valor no Flow retornado por [getInstalledPackages].
     */
    suspend fun refresh()

    /**
     * Executa `cmd package compile -m <profile> [-f] <packageName>` via Shizuku.
     *
     * @param packageName Nome do pacote a compilar.
     * @param profile     Perfil de compilação desejado.
     * @param force       Se `true`, passa `-f` para forçar mesmo se o perfil já for o correto.
     * @return [CompilationResult.Success] ou [CompilationResult.Failure].
     */
    suspend fun compilePackage(
        packageName: String,
        profile: CompilationProfile,
        force: Boolean = true,
    ): CompilationResult

    /**
     * Consulta o filtro de compilação atual de um pacote via `cmd package dump`.
     *
     * @return String como "speed-profile", "verify", "unknown" (se falhar).
     */
    suspend fun getCompilationFilter(packageName: String): String

    /**
     * Executa `dumpsys package dexopt` (única chamada Shizuku) e retorna o mapa
     * completo de `packageName → compilationFilter` para todos os pacotes instalados.
     *
     * Substitui N chamadas individuais a [getCompilationFilter] pelo custo O(1) de
     * IPC + uma varredura linear no texto de saída.
     *
     * @return Mapa de packageName para compilationFilter. Pacotes sem status detectado
     *         não aparecem no mapa (caller deve tratar ausência como "unknown").
     *         Retorna emptyMap() em caso de falha.
     */
    suspend fun getBulkCompilationProfiles(): Map<String, String>

    /**
     * Executa `cmd package compile --reset <packageName>` para limpar artefatos de
     * compilação AOT e reverter o estado de dexopt ao padrão do sistema.
     *
     * @param packageName Nome do pacote a resetar.
     * @return [CompilationResult.Success] com a saída do comando, ou
     *         [CompilationResult.Failure] em caso de erro.
     */
    suspend fun resetCompilation(packageName: String): CompilationResult
}
