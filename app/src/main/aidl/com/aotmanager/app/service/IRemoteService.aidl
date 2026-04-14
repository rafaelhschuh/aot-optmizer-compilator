/*
 * Copyright 2024 AOT Compiler Manager Contributors
 * Licensed under the Apache License, Version 2.0
 */
package com.aotmanager.app.service;

/**
 * Interface AIDL do UserService do Shizuku.
 * Implementada em RemoteServiceImpl que roda no processo Shizuku (UID shell).
 */
interface IRemoteService {
    /** Executa args como ProcessBuilder no contexto privilegiado. Retorna stdout+stderr. */
    String execute(in String[] args);

    /**
     * Executa `cmd package compile --reset <packageName>` para limpar artefatos de
     * compilação (odex/oat/art) e reiniciar o estado de dexopt para o padrão.
     * Retorna stdout+stderr do comando.
     */
    String resetCompilation(String packageName);

    /** Finaliza o processo do UserService. */
    void destroy();
}
