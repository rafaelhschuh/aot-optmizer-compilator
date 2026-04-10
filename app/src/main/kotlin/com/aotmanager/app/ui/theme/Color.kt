/*
 * Copyright 2024 AOT Compiler Manager Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.aotmanager.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Paleta Material You — gerada pelo Material Theme Builder ─────────────────
// Seed color: #006A6A (teal técnico — evoca compilação, performance)

// Light scheme
val Primary            = Color(0xFF006A6A)
val OnPrimary          = Color(0xFFFFFFFF)
val PrimaryContainer   = Color(0xFF9CF1F1)
val OnPrimaryContainer = Color(0xFF002020)

val Secondary          = Color(0xFF4A6363)
val OnSecondary        = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFCCE8E8)
val OnSecondaryContainer = Color(0xFF051F1F)

val Tertiary           = Color(0xFF4B607C)
val OnTertiary         = Color(0xFFFFFFFF)
val TertiaryContainer  = Color(0xFFD3E4FF)
val OnTertiaryContainer = Color(0xFF041C35)

val Error              = Color(0xFFBA1A1A)
val OnError            = Color(0xFFFFFFFF)
val ErrorContainer     = Color(0xFFFFDAD6)
val OnErrorContainer   = Color(0xFF410002)

val Background         = Color(0xFFFAFDFC)
val OnBackground       = Color(0xFF191C1C)
val Surface            = Color(0xFFFAFDFC)
val OnSurface          = Color(0xFF191C1C)
val SurfaceVariant     = Color(0xFFDAE5E4)
val OnSurfaceVariant   = Color(0xFF3F4948)
val Outline            = Color(0xFF6F7979)
val OutlineVariant     = Color(0xFFBEC9C8)

// Dark scheme
val PrimaryDark            = Color(0xFF80D5D5)
val OnPrimaryDark          = Color(0xFF003737)
val PrimaryContainerDark   = Color(0xFF004F4F)
val OnPrimaryContainerDark = Color(0xFF9CF1F1)

val SecondaryDark          = Color(0xFFB0CCCC)
val OnSecondaryDark        = Color(0xFF1C3434)
val SecondaryContainerDark = Color(0xFF324B4B)
val OnSecondaryContainerDark = Color(0xFFCCE8E8)

val TertiaryDark           = Color(0xFFB3C8E8)
val OnTertiaryDark         = Color(0xFF1C314A)
val TertiaryContainerDark  = Color(0xFF334863)
val OnTertiaryContainerDark = Color(0xFFD3E4FF)

val ErrorDark              = Color(0xFFFFB4AB)
val OnErrorDark            = Color(0xFF690005)
val ErrorContainerDark     = Color(0xFF93000A)
val OnErrorContainerDark   = Color(0xFFFFDAD6)

val BackgroundDark         = Color(0xFF191C1C)
val OnBackgroundDark       = Color(0xFFE0E3E2)
val SurfaceDark            = Color(0xFF191C1C)
val OnSurfaceDark          = Color(0xFFE0E3E2)
val SurfaceVariantDark     = Color(0xFF3F4948)
val OnSurfaceVariantDark   = Color(0xFFBEC9C8)
val OutlineDark            = Color(0xFF889392)
val OutlineVariantDark     = Color(0xFF3F4948)

// ── Cores semânticas do domínio ───────────────────────────────────────────────
/** Cor de badge para perfil "speed" — compilação AOT máxima. */
val ProfileSpeed           = Color(0xFF006A6A)
/** Cor de badge para perfil "speed-profile" — AOT guiado. */
val ProfileSpeedProfile    = Color(0xFF4A6363)
/** Cor de badge para perfil "quicken" — otimização leve. */
val ProfileQuicken         = Color(0xFF4B607C)
/** Cor de badge para perfil "interpret-only" — sem compilação. */
val ProfileInterpretOnly   = Color(0xFFBA1A1A)
/** Cor de status: Shizuku conectado. */
val ShizukuConnected       = Color(0xFF386A1F)
/** Cor de status: Shizuku desconectado. */
val ShizukuDisconnected    = Color(0xFFBA1A1A)
