package dev.joyfin.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media

internal val Media.libraryDirectory: Boolean
    get() = type in setOf("CollectionFolder", "UserView", "Folder")

/** Real cards and their static skeletons share geometry, including focus drawing reserves. */
internal object MediaCardLayout {
    val Corner = 8.dp
    val ContentInset = 3.dp
    val RailGap = 16.dp
    val TextGap = 10.dp
    fun width(television: Boolean, wide: Boolean): Dp =
        if (wide) if (television) 244.dp else 234.dp else if (television) 164.dp else 124.dp
    fun ratio(wide: Boolean): Float = if (wide) 16f / 9f else 2f / 3f
}
