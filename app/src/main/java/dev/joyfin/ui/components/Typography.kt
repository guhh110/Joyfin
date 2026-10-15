package dev.joyfin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.theme.Palette

@Composable
fun Caption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Muted,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier,
        color = color,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        color = Palette.Text,
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    )
}

@Composable
fun FilmTitle(
    text: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    maxLines: Int = 2,
) {
    Text(
        text,
        modifier,
        color = Palette.Text,
        fontFamily = FontFamily.SansSerif,
        fontSize = if (large) 46.sp else 32.sp,
        lineHeight = if (large) 56.sp else 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = if (large) (-1.2).sp else (-0.8).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
