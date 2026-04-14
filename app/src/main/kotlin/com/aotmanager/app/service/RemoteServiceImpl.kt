/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.service

import android.os.Process

/**
 * Implementação do [IRemoteService] que roda dentro do processo Shizuku (UID 2000 = shell).
 *
 * Esta classe é instanciada diretamente pelo Shizuku via class loader do app —
 * NÃO é um serviço Android convencional e NÃO precisa ser declarada no
 * AndroidManifest.xml.
 *
 * Como roda como `shell`, pode executar `cmd package compile` e outros
 * comandos que requerem UID elevado.
 *
 * ## Segurança
 * `args` é passado como array para [ProcessBuilder], nunca interpolado em
 * string shell — previne command injection via nomes de pacotes maliciosos.
 */
class RemoteServiceImpl : IRemoteService.Stub() {

    /**
     * Executa o array de argumentos como processo filho com as permissões de shell.
     *
     * @param args Argumentos do processo, ex: ["cmd", "package", "compile", "-m", "speed", "com.foo.bar"]
     * @return stdout do processo (+ stderr se redirectErrorStream=true). Nunca null.
     */
    override fun execute(args: Array<out String>): String {
        return try {
            val process = ProcessBuilder(*args)
                .redirectErrorStream(true)   // stderr -> stdout para simplificar leitura
                .start()

            val output   = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                // Inclui o exit code na saída para diagnóstico na UI
                "[exit=$exitCode]\n$output"
            } else {
                output.ifBlank { "[OK]" }
            }
        } catch (e: Exception) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Executa `cmd package compile --reset <packageName>` para remover artefatos de
     * compilação AOT (odex/oat/art) e reverter o estado de dexopt ao padrão.
     *
     * Delega para [execute] garantindo que os args são passados via [ProcessBuilder]
     * sem interpolação em string shell (sem risco de command injection).
     *
     * @param packageName Nome canônico do pacote a resetar.
     * @return stdout do comando.
     */
    override fun resetCompilation(packageName: String): String =
        execute(arrayOf("cmd", "package", "compile", "--reset", packageName))

    /**
     * Mata o processo deste UserService.
     * Chamado quando o app é destruído para liberar recursos.
     */
    override fun destroy() {
        Process.killProcess(Process.myPid())
    }
}
