/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.domain.model

/**
 * Resultado de uma operação de compilação AOT para um único pacote.
 *
 * Modelado como sealed class para forçar tratamento exaustivo em `when`.
 */
sealed class CompilationResult {

    /**
     * Compilação executada com sucesso.
     *
     * @property packageName Nome do pacote compilado.
     * @property profile     Perfil utilizado.
     * @property output      Saída completa do comando `cmd package compile`.
     * @property durationMs  Tempo de execução em milissegundos.
     */
    data class Success(
        val packageName: String,
        val profile: CompilationProfile,
        val output: String,
        val durationMs: Long,
    ) : CompilationResult()

    /**
     * Compilação falhou.
     *
     * @property packageName Nome do pacote que falhou.
     * @property errorMessage Mensagem de erro legível.
     * @property cause        Exceção original, se disponível.
     */
    data class Failure(
        val packageName: String,
        val errorMessage: String,
        val cause: Throwable? = null,
    ) : CompilationResult()
}
