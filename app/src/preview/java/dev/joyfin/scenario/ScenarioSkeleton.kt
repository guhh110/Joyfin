package dev.joyfin.scenario

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import dev.joyfin.ui.components.MediaCardLayout
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette
import kotlin.math.roundToInt

internal enum class CardSkeletonStyle { Detailed, Plain, Drawn }

/** Experimental renderers live only in Preview; all variants keep the normal cell geometry. */
@Composable
internal fun ScenarioSkeleton(modifier: Modifier, wide: Boolean, visible: Boolean, showSubtitle: Boolean, plain: Boolean) {
    val density = LocalDensity.current
    val titleHeight = with(density) { MediaCardLayout.TitleSlot.toDp() }
    val subtitleHeight = with(density) { MediaCardLayout.SubtitleSlot.toDp() }
    Column(modifier
        .padding(vertical = MotionTokens.MediaCardVerticalReserve)
        .padding(MediaCardLayout.ContentInset * 2)
        .clearAndSetSemantics {}
        .drawWithCache {
            val corner = CornerRadius(MediaCardLayout.Corner.toPx())
            val imageHeight = (size.width / MediaCardLayout.ratio(wide)).roundToInt().toFloat()
            val titleTop = imageHeight + MediaCardLayout.TextGap.toPx()
            val titleBlock = with(density) { 15.sp.toPx() }
            val subtitleBlock = with(density) { 12.sp.toPx() }
            onDrawBehind {
                if (visible) {
                    if (plain) drawRoundRect(Palette.Surface, cornerRadius = corner)
                    else {
                        drawRoundRect(Palette.Surface, size = Size(size.width, imageHeight), cornerRadius = corner)
                        drawRoundRect(Palette.Surface,
                            topLeft = Offset(0f, titleTop + (titleHeight.toPx() - titleBlock) / 2),
                            size = Size(size.width * .8f, titleBlock), cornerRadius = corner)
                        if (showSubtitle) drawRoundRect(Palette.Surface,
                            topLeft = Offset(0f, titleTop + titleHeight.toPx() + (subtitleHeight.toPx() - subtitleBlock) / 2),
                            size = Size(size.width * .5f, subtitleBlock), cornerRadius = corner)
                    }
                }
            }
        }) {
        Spacer(Modifier.fillMaxWidth().aspectRatio(MediaCardLayout.ratio(wide)))
        Spacer(Modifier.height(MediaCardLayout.TextGap))
        Spacer(Modifier.height(titleHeight))
        if (showSubtitle) Spacer(Modifier.height(subtitleHeight))
    }
}
