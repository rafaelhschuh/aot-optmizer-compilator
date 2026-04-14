/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.aotmanager.app.data.parser.PackageInfoParser
import com.aotmanager.app.data.shizuku.ShizukuCommandExecutor
import com.aotmanager.app.data.shizuku.ShizukuNotReadyException
import com.aotmanager.app.domain.model.AppPackage
import com.aotmanager.app.domain.model.CompilationProfile
import com.aotmanager.app.domain.model.CompilationResult
import com.aotmanager.app.domain.model.LogType
import com.aotmanager.app.domain.repository.LogRepository
import com.aotmanager.app.domain.repository.PackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageRepositoryImpl @Inject constructor(
    private val packageManager: PackageManager,
    private val executor: ShizukuCommandExecutor,
    private val parser: PackageInfoParser,
    private val logRepository: LogRepository,
) : PackageRepository {

    // SharedFlow usado como trigger de refresh — emitir Unit força re-fetch
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    override fun getInstalledPackages(includeSystemApps: Boolean): Flow<List<AppPackage>> =
        refreshTrigger
            .onStart { emit(Unit) }   // carrega imediatamente ao coletar
            .distinctUntilChanged()
            .transformLatest {
                emit(fetchPackages(includeSystemApps))
            }

    override suspend fun refresh() {
        refreshTrigger.emit(Unit)
    }

    override suspend fun compilePackage(
        packageName: String,
        profile: CompilationProfile,
        force: Boolean,
    ): CompilationResult {
        val start = System.currentTimeMillis()
        return try {
            val output = executor.executeCompile(packageName, profile, force)
            val duration = System.currentTimeMillis() - start
            Timber.i("Compilado $packageName ($profile) em ${duration}ms")
            CompilationResult.Success(packageName, profile, output, duration)
        } catch (e: ShizukuNotReadyException) {
            Timber.w(e, "Shizuku não disponível para compilar $packageName")
            CompilationResult.Failure(packageName, "Shizuku não disponível: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Falha ao compilar $packageName")
            CompilationResult.Failure(packageName, e.message ?: "Erro desconhecido", e)
        }
    }

    override suspend fun getCompilationFilter(packageName: String): String {
        return try {
            val dump = executor.dumpPackage(packageName)
            parser.parseCompilationFilter(dump)
        } catch (e: Exception) {
            Timber.w(e, "Não foi possível obter filtro de compilação de $packageName")
            "unknown"
        }
    }

    override suspend fun getBulkCompilationProfiles(): Map<String, String> {
        return try {
            logRepository.log(LogType.APP, "Iniciando getBulkCompilationProfiles (1 chamada Shizuku)")
            val raw = executor.executeBulkDexoptDump()  // executor loga CMD + preview do output

            val lineCount = raw.lines().size
            logRepository.log(LogType.APP, "Output recebido: ${raw.length} chars | $lineCount linhas")

            val profiles = parser.parseBulkDexoptStates(raw)

            // Log detalhado do resultado do parse — essencial para diagnosticar problemas de formato
            logRepository.log(
                LogType.APP,
                "parseBulkDexoptStates concluído: ${profiles.size} profiles extraídos de $lineCount linhas\n" +
                    "Amostra (primeiros 5):\n" +
                    profiles.entries.take(5).joinToString("\n") { (pkg, filter) -> "  $pkg → $filter" }
            )

            Timber.i("getBulkCompilationProfiles: ${profiles.size} perfis retornados")
            profiles
        } catch (e: Exception) {
            logRepository.log(LogType.APP, "ERRO em getBulkCompilationProfiles: ${e.javaClass.simpleName}: ${e.message}")
            Timber.e(e, "Falha ao executar bulk dexopt dump")
            emptyMap()
        }
    }

    override suspend fun resetCompilation(packageName: String): CompilationResult {
        val start = System.currentTimeMillis()
        return try {
            val output = executor.resetCompilation(packageName)
            val duration = System.currentTimeMillis() - start
            Timber.i("Reset $packageName em ${duration}ms")
            // Reutilizamos CompilationResult.Success para sinalizar o reset concluído.
            // profile = VERIFY porque o estado pós-reset equivale a "verify" na maioria dos devices.
            CompilationResult.Success(packageName, CompilationProfile.VERIFY, output, duration)
        } catch (e: ShizukuNotReadyException) {
            Timber.w(e, "Shizuku não disponível para resetar $packageName")
            CompilationResult.Failure(packageName, "Shizuku não disponível: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Falha ao resetar $packageName")
            CompilationResult.Failure(packageName, e.message ?: "Erro desconhecido", e)
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun fetchPackages(includeSystemApps: Boolean): List<AppPackage> =
        withContext(Dispatchers.IO) {
            Timber.d("Carregando pacotes (system=$includeSystemApps)")

            @Suppress("DEPRECATION")
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                packageManager.getInstalledPackages(0)
            }

            installedPackages
                .filter { pkgInfo ->
                    includeSystemApps || (pkgInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0)
                }
                .mapNotNull { pkgInfo ->
                    runCatching {
                        val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                        AppPackage(
                            packageName       = pkgInfo.packageName,
                            label             = packageManager.getApplicationLabel(appInfo).toString(),
                            versionName       = pkgInfo.versionName ?: "—",
                            versionCode       = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                                                    pkgInfo.longVersionCode
                                                else
                                                    @Suppress("DEPRECATION") pkgInfo.versionCode.toLong(),
                            isSystemApp       = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                            // Filtro de compilação não é consultado no carregamento inicial
                            // para evitar N chamadas Shizuku em sequência (lento).
                            compilationFilter = "unknown",
                        )
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
                .also { Timber.d("${it.size} pacotes carregados") }
        }
}
