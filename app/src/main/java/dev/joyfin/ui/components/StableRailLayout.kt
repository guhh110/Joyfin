package dev.joyfin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.TextUnit

/** Font fallback and missing metadata must not change a lazy row's measured height. */
@Composable
internal fun RailTextSlot(height: TextUnit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val slotHeight = with(LocalDensity.current) { height.toDp() }
    Box(modifier.fillMaxWidth().height(slotHeight), contentAlignment = Alignment.CenterStart) { content() }
}

/** The row scrolls its children horizontally; ancestors align the row as one stable rectangle. */
internal fun Modifier.stableRailViewport(): Modifier = then(StableRailViewportElement)

private data object StableRailViewportElement : ModifierNodeElement<StableRailViewportNode>() {
    override fun create() = StableRailViewportNode()
    override fun update(node: StableRailViewportNode) = Unit
    override fun InspectorInfo.inspectableProperties() { name = "stableRailViewport" }
}

private class StableRailViewportNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
        bringIntoView {
            if (!isAttached || !childCoordinates.isAttached || boundsProvider() == null) null
            else {
                val size = requireLayoutCoordinates().size
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            }
        }
    }
}
