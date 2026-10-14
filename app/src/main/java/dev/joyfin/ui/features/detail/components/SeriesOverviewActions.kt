package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.browse.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.seriesFocus
import dev.joyfin.ui.model.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SeriesOverviewHeader(content: SeriesOverviewContent) {
    val actions = content.actions
    val focus = content.focus
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        IconButton("返回", Glyph.Back, actions.back, Modifier.seriesFocus("header:back", focus))
        ActionButton(
            "字幕管理",
            actions.subtitles,
            Modifier.seriesFocus("detail:subtitles", focus),
            enabled = content.ui.ready,
            quiet = true,
        )
    }
}

@Composable
internal fun SeriesOverviewFeedback(content: SeriesOverviewContent, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Text(
            content.feedback,
            color = Palette.Muted,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SeriesPlayAction(
    content: SeriesOverviewContent,
    modifier: Modifier,
    icon: Glyph? = null,
) {
    val target = content.target
    ActionButton(
        if (target == null)
            if (content.ui.series?.recommendation?.loading == true) "播放本集" else "暂无可播放内容"
        else content.playLabel(target, "detail:play"),
        { target?.let { content.onPlay(it, false, null, "detail:play") } },
        modifier.seriesFocus("detail:play", content.focus),
        icon,
        primary = true,
        enabled = content.canPlay(target, "detail:play"),
        maxLines = 2,
        loading =
            content.preparing(target, "detail:play") ||
                target == null &&
                    (content.ui.busy || content.ui.series?.recommendation?.loading == true),
        enabledWhileLoading = content.preparing(target, "detail:play"),
        loadingLabel = if (content.preparing(target, "detail:play")) "正在准备，点击取消" else "正在加载",
    )
}

@Composable
internal fun SeriesFavoriteAction(content: SeriesOverviewContent, modifier: Modifier) {
    ActionButton(
        if (content.ui.item?.favorite == true) "已收藏" else "收藏",
        content.actions.favorite,
        modifier.seriesFocus("favorite", content.focus),
        quiet = true,
        enabled = content.ui.ready,
        loading = content.ui.favoriteBusy,
        maxLines = 2,
    )
}
