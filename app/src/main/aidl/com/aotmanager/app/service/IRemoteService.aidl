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

    /** Finaliza o processo do UserService. */
    void destroy();
}
