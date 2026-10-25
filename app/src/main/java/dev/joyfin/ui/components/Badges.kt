package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.theme.Palette
import java.util.Locale

/** Micro capsule pill badge for film/show metadata. */
@Composable
fun MediaPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: Glyph? = null,
    iconColor: Color = Palette.Text,
    highlight: Boolean = false,
) {
    val shape = RoundedCornerShape(4.dp)
    val background = if (highlight) Palette.Elevated else Color(0x1AFFFFFF)
    val border = Color(0x33FFFFFF)
    val textColor = if (highlight) Palette.Text else Color(0xDDFFFFFF)

    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            AppIcon(icon, Modifier.size(12.dp), color = iconColor)
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.1.sp,
            maxLines = 1,
        )
    }
}

/** Star rating pill with distinctive star glyph. */
@Composable
fun RatingPill(rating: Double, modifier: Modifier = Modifier) {
    MediaPill(
        text = String.format(Locale.ROOT, "%.1f", rating),
        icon = Glyph.Star,
        iconColor = Color(0xFFFFD166),
        modifier = modifier,
    )
}

/** Quality & dynamic range badge (4K UHD, HDR, Dolby Vision, 5.1ch). */
@Composable
fun QualityBadge(text: String, modifier: Modifier = Modifier) {
    MediaPill(text = text, modifier = modifier)
}

/** Floating badge overlay on media poster cards. */
@Composable
fun CardOverlayBadge(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Glyph? = null,
    iconColor: Color = Palette.Text,
) {
    val shape = if (text.isNullOrBlank() && icon != null) CircleShape else RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xE6141418))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .padding(horizontal = if (text != null) 5.dp else 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) {
                AppIcon(icon, Modifier.size(11.dp), color = iconColor)
            }
            if (!text.isNullOrBlank()) {
                Text(
                    text = text,
                    color = Palette.Text,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
