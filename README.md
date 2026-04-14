# AOT Compiler Manager

Aplicativo Android para recompilar pacotes instalados usando perfis AOT (Ahead-of-Time), sem necessidade de root permanente. Utiliza [Shizuku](https://github.com/RikkaApps/Shizuku) para executar comandos shell com UID 2000.

## Por que usar AOT?

O Android compila apps de forma adaptativa ao longo do uso: começa interpretando bytecode, coleta perfis de execução e eventualmente compila trechos críticos em código nativo. Esse processo é gradual e leva dias ou semanas para atingir o desempenho ideal.

Com a compilação AOT manual você pula essa curva:

| Situação | Sem AOT manual | Com AOT manual |
|----------|---------------|----------------|
| App recém-instalado | Lento até o perfil ser coletado | Compilado imediatamente |
| Após factory reset ou migração | Todo histórico de perfis perdido | Recompila tudo em minutos |
| Apps de uso frequente | Compilação parcial por semanas | `speed-profile` ou `speed` de imediato |
| Dispositivos com pouca RAM | JIT consome memória em runtime | AOT elimina o JIT no caminho crítico |
| ROMs customizadas / GMS removido | Serviço de otimização em background ausente | Controle total e manual |

### Perfis disponíveis

| Perfil | O que faz |
|--------|-----------|
| `speed` | Compila tudo em nativo — máxima performance, maior APK em disco |
| `speed-profile` | Compila apenas os métodos quentes coletados pelo perfil — melhor equilíbrio |
| `quicken` | Otimizações rápidas de DEX sem compilação nativa completa |
| `verify` | Apenas verifica o bytecode, sem compilar — mínimo de espaço |
| `space` | Prioriza tamanho reduzido em detrimento de velocidade |
| `everything` | Compila todos os métodos, incluindo raramente usados |
| `interpret-only` | Desativa toda compilação — útil para diagnóstico |

### Casos de uso práticos

- **Pós factory reset ou troca de dispositivo:** recompila os apps mais usados antes mesmo de abri-los pela primeira vez
- **Dispositivos de trabalho/produção:** garante que apps críticos sempre rodem com máxima performance
- **Desenvolvimento e benchmark:** define um estado de compilação conhecido e reproduzível entre testes
- **ROMs sem Google:** sem o serviço `dexopt` em background do Google Play, a compilação adaptativa nunca acontece espontaneamente

## Funcionalidades

- Lista todos os apps instalados com o perfil AOT atual (`speed-profile`, `quicken`, `verify`, etc.)
- Recompilação individual ou em lote com 7 perfis de compilação
- Reset do perfil AOT por pacote (`--reset`)
- Compilação em background via WorkManager com feedback de progresso
- Log interno acessível em **Configurações → Logs internos** — exibe comandos executados e saída bruta do shell para diagnóstico

## Requisitos

- Android 9+ (API 28)
- [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) instalado e ativo no dispositivo

## Como buildar

**Pré-requisitos:** Android Studio Hedgehog+ ou SDK instalado, Java 17, Shizuku no dispositivo.

```bash
# Clonar o repositório
git clone https://github.com/rafaelhschuh/aot-optmizer-compilator.git
cd aot-optmizer-compilator

# Criar local.properties com o caminho do SDK
echo "sdk.dir=/home/$USER/Android/Sdk" > local.properties

# Build debug
./gradlew assembleDebug

# Instalar no dispositivo conectado
adb install app/build/outputs/apk/debug/app-debug.apk
```

> Em máquinas diferentes, ajustar `org.gradle.java.home` em `gradle.properties` para apontar para um Java 17 local.

## Arquitetura

O app comunica com o processo Shizuku (UID 2000) via AIDL + Binder:

```
App Process (UID normal)
  └─ ShizukuCommandExecutor ──bindUserService()──▶ Shizuku Process (UID 2000)
                                                        └─ RemoteServiceImpl
                                                              └─ ProcessBuilder("cmd package compile ...")
```

Camadas:

| Camada | Responsabilidade |
|--------|-----------------|
| `domain/` | Modelos puros e interfaces de repositório |
| `data/` | Implementações: Shizuku, parser de `dumpsys`, log |
| `service/` | `RemoteServiceImpl` — roda no processo Shizuku |
| `ui/` | Telas Compose + ViewModels |
| `work/` | `CompilationWorker` via WorkManager |

## Stack

- Kotlin + Jetpack Compose + Material 3
- Hilt (injeção de dependência)
- WorkManager (`@HiltWorker`)
- Shizuku 13.x (`bindUserService` + AIDL)
- Navigation Compose 2.8 (rotas type-safe)
- Coroutines + StateFlow

## Licença

Distribuído sob a licença [Apache 2.0](LICENSE).
