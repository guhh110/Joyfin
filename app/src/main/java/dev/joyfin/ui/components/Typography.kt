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
        fontSize = 12.sp,
        lineHeight = 19.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
        fontFamily = FontFamily.Serif,
        fontSize = if (large) 54.sp else 36.sp,
        lineHeight = if (large) 65.sp else 46.sp,
        fontWeight = FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
