# Copyright 2024 AOT Compiler Manager Contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0

# ── Shizuku ───────────────────────────────────────────────────────────────────
# Shizuku usa reflection + Binder IPC para localizar UserService por nome.
# Obfuscar esses nomes quebra o binding silenciosamente.
-keep class rikka.shizuku.**        { *; }
-keep interface rikka.shizuku.**    { *; }
-keepclassmembers class * implements rikka.shizuku.ShizukuProvider { *; }
-dontwarn rikka.shizuku.**

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
# O código gerado pelo KSP referencia classes por nome via reflexão em alguns paths.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepnames @dagger.hilt.android.HiltAndroidApp class *
-keepnames @dagger.hilt.android.AndroidEntryPoint class *

# ── Kotlinx Serialization ─────────────────────────────────────────────────────
# Serializers são descobertos por reflexão em runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
}
-keep @kotlinx.serialization.Serializable class com.aotmanager.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
# Workers são instanciados por nome de classe via WorkerFactory do Hilt.
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Compose ───────────────────────────────────────────────────────────────────
# Compose Runtime usa reflexão para alguns mecanismos de estabilidade.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ── Kotlin Reflect ────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# ── Timber ────────────────────────────────────────────────────────────────────
# Remove logs verbose em release — R8 fullMode inlina a verificação do nível.
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
}

# ── Otimizações agressivas (compatível com R8 fullMode) ───────────────────────
-allowaccessmodification
-repackageclasses "c"
