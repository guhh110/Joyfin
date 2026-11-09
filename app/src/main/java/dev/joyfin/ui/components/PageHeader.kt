package dev.joyfin.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

/** One title row; overflowing tools scroll horizontally instead of adding header rows. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backFocusKey: String = "header:back",
    leading: @Composable () -> Unit = {},
    scrollActions: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("page:header")) {
        val toolsWidth = minOf(maxWidth * .68f,
            (maxWidth - if (onBack != null) 168.dp else 110.dp).coerceAtLeast(48.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) IconButton("返回", Glyph.Back, onBack, Modifier.focusTarget(backFocusKey))
            leading()
            Text(
                title,
                Modifier.weight(1f).semantics { heading() },
                color = Palette.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.widthIn(max = toolsWidth)
                    .then(if (scrollActions) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions,
            )
        }
    }
}
