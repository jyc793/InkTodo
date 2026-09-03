package com.inktodo.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class InkPalette(
    val paper: Color,
    val paperDeep: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val ink: Color,
    val inkSoft: Color,
    val mist: Color,
    val indigo: Color,
    val moss: Color,
    val jade: Color,
    val cinnabar: Color,
    val wash: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassEdge: Color,
    val shadow: Color,
    val scrim: Color,
)

internal val LightInkPalette = InkPalette(
    paper = Color(0xFFF4F1E8),
    paperDeep = Color(0xFFE6E4DA),
    surface = Color(0xFFF8F6EF),
    surfaceStrong = Color(0xFFFDFCF8),
    ink = Color(0xFF17201D),
    inkSoft = Color(0xFF35423D),
    mist = Color(0xFF5D6863),
    indigo = Color(0xFF304D55),
    moss = Color(0xFF34584B),
    jade = Color(0xFF6F978A),
    cinnabar = Color(0xFFA33C32),
    wash = Color(0xFFD9E4DF),
    glass = Color(0xDDFBF9F3),
    glassStrong = Color(0xF7FDFCF8),
    glassEdge = Color(0x80677770),
    shadow = Color(0x3D07100C),
    scrim = Color(0x80101412),
)

internal val DarkInkPalette = InkPalette(
    paper = Color(0xFF0F1512),
    paperDeep = Color(0xFF080D0B),
    surface = Color(0xFF151D19),
    surfaceStrong = Color(0xFF1C2823),
    ink = Color(0xFFF0F3EE),
    inkSoft = Color(0xFFCED7D2),
    mist = Color(0xFFB3C0BA),
    indigo = Color(0xFF91B3B9),
    moss = Color(0xFF91B4A5),
    jade = Color(0xFF9AC4B6),
    cinnabar = Color(0xFFE27A6E),
    wash = Color(0xFF273A33),
    glass = Color(0xCC18221E),
    glassStrong = Color(0xF0222F29),
    glassEdge = Color(0x806F8F83),
    shadow = Color(0xB3000000),
    scrim = Color(0xB3060A08),
)
