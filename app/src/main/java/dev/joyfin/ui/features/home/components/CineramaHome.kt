package dev.joyfin.ui.features.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import dev.joyfin.browse.HomeRegion
import dev.joyfin.browse.HomeUiState
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.data.Media
import dev.joyfin.data.playLabel
import dev.joyfin.data.statusLabel
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.rememberMotionDuration
import dev.joyfin.ui.theme.rememberMotionSpec
import dev.joyfin.ui.theme.CinemaStyle
import kotlinx.coroutines.delay
import java.util.Locale

private val CinemaBlack = CinemaStyle.Background
private val CinemaMuted = CinemaStyle.Muted
private val CinemaAccents = listOf(CinemaStyle.Accent, Color(0xFF3B82F6), Color(0xFFA855F7))

/** Native TV stage: a wide screen, two folded previews and floating controls. */
@Composable
internal fun CineramaHome(
    ui: HomeUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    layout: HomeLayoutPreferences,
    tracking: TrackingUiState?,
) {
    val home = homeContent(ui)
    val collections = remember(home, layout, tracking) { cineramaCollections(home, layout, tracking) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var cursors by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    var direction by remember { mutableIntStateOf(1) }
    val index = collections.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val collection = collections.getOrNull(index)
    val cursor = collection?.items?.indexOfFirst { it.id == cursors[collection.id] }?.coerceAtLeast(0) ?: 0
    val current = collection?.items?.getOrNull(cursor)
    val tv = LocalDeviceLayout.current.television
    val memory = LocalScreenFocus.current
    var stageFocused by remember { mutableStateOf(false) }
    val resumeActions = LocalResumeQuickAction.current
    val accent by animateColorAsState(
        CinemaAccents[index % CinemaAccents.size],
        rememberMotionSpec(500), label = "cinemaAccent",
    )

    // Return to the remembered film with a usable stage focus after another page is removed.
    LaunchedEffect(Unit) {
        if (tv) {
            withFrameNanos {}
            memory?.rememberTarget?.invoke("tunnel:screen")
            memory?.restore()
        }
    }

    // Resolve vanished selections once; a late-arriving region must not steal the active library.
    LaunchedEffect(collection?.id, current?.id, collection?.loading) {
        if (collection != null) {
            selectedId = collection.id
            if (current != null && !collection.loading && cursors[collection.id] != current.id) {
                cursors = HashMap(cursors).apply { put(collection.id, current.id) }
            }
        }
    }
    fun stepLibrary(delta: Int) {
        if (collections.size > 1) selectedId = collections[wrappedIndex(index, delta, collections.size)].id
    }
    fun stepMovie(delta: Int) {
        if (collection == null || collection.items.size < 2) return
        direction = delta
        cursors = HashMap(cursors).apply {
            put(collection.id, collection.items[wrappedIndex(cursor, delta, collection.items.size)].id)
        }
    }
    fun retry() {
        when {
            collection?.id == "tracking" -> actions.refreshTracking()
            collection?.library != null -> actions.retryLibrary(collection.library.id)
            collection?.region != null -> actions.retryHome(collection.region)
            else -> HomeRegion.entries.forEach(actions.retryHome)
        }
    }
    fun more() {
        when {
            collection?.id == "tracking" -> actions.tracking()
            collection?.library != null -> actions.libraryWorks(collection.library.id, collection.title)
            else -> actions.allMedia()
        }
    }
    fun play() {
        if (current == null) {
            if (collection?.error != null) retry() else more()
            return
        }
        val episode = if (collection.id == "tracking") {
            tracking?.entries?.firstOrNull { it.series.id == current.id }?.progress?.target
        } else null
        when {
            episode != null -> actions.play(episode, false, null)
            current.isFolder -> actions.open(current)
            else -> actions.play(current, false, null)
        }
    }
    fun details() { current?.let(actions.open) ?: more() }

    val playLabel = if (collection?.id == "tracking") {
        tracking?.entries?.firstOrNull { it.series.id == current?.id }?.playLabel()
    } else null
    val primaryLabel = playLabel ?: when {
        current == null && collection?.error != null -> "重新加载"
        current == null -> "浏览媒体库"
        current.resumeTicks > 0 -> "继续观看"
        current.isFolder -> "查看剧集"
        else -> "开启放映"
    }

    val focusKey = memory?.target?.invoke()
    BackHandler(enabled = tv && (focusKey == "tunnel:screen" || focusKey?.startsWith("cinema:") == true)) {
        memory?.requesters?.get("header:tracking")?.requestFocus()
    }

    Box(Modifier.fillMaxSize().background(CinemaBlack).testTag("home:cinerama")) {
        // Soft light rather than another legible poster. Compose blur is unavailable on API 28–30.
        Box(Modifier.fillMaxSize().drawWithCache {
            val glow = Brush.radialGradient(
                listOf(accent.copy(alpha = .075f), Color.Transparent),
                center = Offset(size.width * .55f, size.height * .45f),
                radius = size.width * .65f,
            )
            onDrawBehind { drawRect(glow) }
        })

        Box(Modifier.fillMaxSize()) {
            if (collection == null) {
                Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 100.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    val error = ui.error ?: listOf(home.resumeLoad, home.latestLoad, home.librariesLoad).firstNotNullOfOrNull { it.error }
                    Text(if (error != null) "暂时无法连接放映厅" else "放映厅已就绪", color = Color.White,
                        fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(if (error != null) browseErrorText(error) else "暂无可展示的栏目，可浏览媒体库或调整首页栏目。",
                        color = CinemaMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    ActionButton(if (error != null) "重新加载" else "浏览媒体库",
                        { if (error != null) retry() else actions.library(null, "媒体库") },
                        Modifier.focusTarget("tunnel:screen"), primary = true)
                    Row {
                        if (error == null) ActionButton("刷新媒体库", ::retry, quiet = true)
                        ActionButton("首页栏目设置", actions.settings, quiet = true)
                    }
                }
            } else {
                // Keep the stage focus slot separate from its floating action buttons so
                // requesting it after a page return cannot focus one of the child buttons.
                Box(
                    Modifier.fillMaxSize()
                        .focusTarget("tunnel:screen")
                        .onFocusChanged { stageFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (!stageFocused) return@onPreviewKeyEvent false
                            val action: (() -> Unit)? = when (event.key) {
                                Key.DirectionUp -> { { stepLibrary(-1) } }
                                Key.DirectionDown -> { { stepLibrary(1) } }
                                Key.DirectionLeft -> { { stepMovie(-1) } }
                                Key.DirectionRight -> { { stepMovie(1) } }
                                Key.Menu -> { { if (collection.id == "resume" && current != null) resumeActions(current) else details() } }
                                else -> null
                            }
                            if (action != null) {
                                if (event.type == KeyEventType.KeyDown) action()
                                true
                            } else false
                        }
                        .semantics {
                            contentDescription = "${collection.title}，${current?.name ?: if (collection.loading) "正在加载" else "暂无作品"}"
                            customActions = listOf(
                                CustomAccessibilityAction("上一个媒体库") { stepLibrary(-1); true },
                                CustomAccessibilityAction("下一个媒体库") { stepLibrary(1); true },
                                CustomAccessibilityAction("上一部作品") { stepMovie(-1); true },
                                CustomAccessibilityAction("下一部作品") { stepMovie(1); true },
                                CustomAccessibilityAction("查看详情") { details(); true },
                            )
                        }
                        .stableAction(enabled = !collection.loading || current != null,
                            onLongClick = { if (collection.id == "resume" && current != null) resumeActions(current) else details() },
                            onClick = ::play),
                )
                TunnelPlanes(
                    collections, index, cursors, images, accent, direction, tracking,
                    Modifier.fillMaxSize(),
                    primaryLabel = primaryLabel,
                    onPlay = ::play,
                    onDetails = ::details,
                    onMore = ::more,
                )

                // A refresh failure does not remove playable content, and remains actionable.
                if (current != null && collection.error != null) {
                    Row(Modifier.align(Alignment.TopStart).fillMaxWidth(.6f)
                        .padding(horizontal = 80.dp)
                        .padding(top = 90.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("刷新失败，正在显示上次内容", Modifier.weight(1f), color = CinemaMuted, fontSize = 12.sp)
                        ActionButton("重试", ::retry, quiet = true, maxLines = 1)
                    }
                }
                CinemaFooter(cursor, collection.items.size, accent,
                    Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun TunnelPlanes(
    collections: List<CineramaCollection>, selected: Int, cursors: Map<String, String>,
    images: ArtworkSource, accent: Color, direction: Int, tracking: TrackingUiState?,
    modifier: Modifier, primaryLabel: String, onPlay: () -> Unit, onDetails: () -> Unit, onMore: () -> Unit,
) {
    val centerHeight = .50f
    val previous = if (collections.size > 2) wrappedIndex(selected, -1, collections.size) else -1
    val next = if (collections.size > 1) wrappedIndex(selected, 1, collections.size) else -1
    val visible = setOfNotNull(collections.getOrNull(previous)?.id, collections[selected].id, collections.getOrNull(next)?.id)
    var retained by remember { mutableStateOf(visible) }
    val duration = rememberMotionDuration(650)
    LaunchedEffect(visible) {
        retained = retained + visible
        delay(duration.toLong())
        retained = visible
    }
    BoxWithConstraints(modifier.testTag("tunnel:stage")) {
        val stageHeight = maxHeight
        val density = LocalDensity.current
        val fontScale = density.fontScale.coerceAtLeast(1f)
        val floatSpec = rememberMotionSpec<Float>(650)
        val dpSpec = rememberMotionSpec<androidx.compose.ui.unit.Dp>(650)
        val fadeSpec = rememberMotionSpec<Float>(300)
        val slideSpec = rememberMotionSpec<IntOffset>(420)
        collections.forEachIndexed { i, item ->
            if (item.id !in visible && item.id !in retained) return@forEachIndexed
            key(item.id) {
                val visibleRole = when (i) { selected -> 0; previous -> -1; next -> 1; else -> null }
                var lastVisibleRole by remember { mutableIntStateOf(visibleRole ?: 1) }
                SideEffect { if (visibleRole != null) lastVisibleRole = visibleRole }
                // Retiring folds leave through their nearest edge rather than crossing the stage.
                val role = visibleRole ?: if (lastVisibleRole < 0) -2 else 2
                val top by animateDpAsState(stageHeight * when (role) {
                    0 -> .25f; -1 -> .045f; 1 -> .715f; -2 -> -.32f; else -> 1.08f
                }, dpSpec, label = "planeTop")
                val height by animateDpAsState(stageHeight * if (role == 0) centerHeight else .24f, dpSpec, label = "planeHeight")
                val zoom by animateFloatAsState(if (role == 0) 1f else .94f, floatSpec, label = "planeZoom")
                val corner by animateDpAsState(if (role == 0) 12.dp else 10.dp, dpSpec, label = "planeCorner")
                val fold by animateFloatAsState(when { role < 0 -> -58f; role > 0 -> 58f; else -> 0f }, floatSpec, label = "planeFold")
                val opacity by animateFloatAsState(when (role) { 0 -> 1f; -1, 1 -> .42f; else -> 0f }, floatSpec, label = "planeOpacity")
                val metadataOpacity by animateFloatAsState(if (role == 0) 1f else 0f, fadeSpec, label = "planeMetadata")
                val movie = item.items.firstOrNull { it.id == cursors[item.id] } ?: item.items.firstOrNull()
                val shape = when (role) {
                    -1 -> RoundedCornerShape(topStart = corner, topEnd = corner)
                    1 -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
                    else -> RoundedCornerShape(corner)
                }
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = top)
                        .fillMaxWidth(.90f).height(height)
                        .zIndex(if (role == 0) 1f else 0f)
                        .graphicsLayer {
                            alpha = opacity
                            scaleX = zoom; scaleY = zoom
                            rotationX = fold
                            cameraDistance = size.width
                            shadowElevation = if (role == 0) 24.dp.toPx() else 0f
                            this.shape = shape; clip = true
                        }
                        .background(Color(0xFF11151D))
                        .border(.5.dp, Color.White.copy(alpha = if (role == 0) .09f else .04f), shape),
                ) {
                    AnimatedContent(movie, Modifier.fillMaxSize(), transitionSpec = {
                        (fadeIn(fadeSpec) + slideInHorizontally(slideSpec) { it / 14 * direction }) togetherWith
                            (fadeOut(fadeSpec) + slideOutHorizontally(slideSpec) { -it / 14 * direction })
                    }, label = "cinemaSlice") { media ->
                        if (media != null) Artwork(if (role == 0) images.backdrop(media) else images.thumbnail(media), images,
                            Modifier.fillMaxSize(), alignment = BiasAlignment(0f, -.2f))
                    }
                    Box(Modifier.fillMaxSize().background(
                        if (role == 0) Brush.horizontalGradient(
                            0f to CinemaBlack.copy(alpha = .90f), .38f to CinemaBlack.copy(alpha = .48f), 1f to Color.Transparent)
                        else Brush.verticalGradient(if (role == -1)
                            listOf(CinemaBlack.copy(alpha = .12f), CinemaBlack.copy(alpha = .68f))
                        else listOf(CinemaBlack.copy(alpha = .68f), CinemaBlack.copy(alpha = .12f)))
                    ))
                    if (role == 0) {
                        val entry = if (item.id == "tracking") tracking?.entries?.firstOrNull { it.series.id == movie?.id } else null
                        CinemaMetadata(item, movie, accent, stageHeight * centerHeight / fontScale < 245.dp,
                            stageHeight * centerHeight / fontScale >= 195.dp,
                            entry?.statusLabel(), Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = metadataOpacity }) {
                            CinemaActions(primaryLabel, onPlay, onDetails, onMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaMetadata(
    collection: CineramaCollection, media: Media?, accent: Color, compact: Boolean, showDescription: Boolean,
    trackingStatus: String?, modifier: Modifier, actions: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(.50f)
        .padding(start = 40.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (media == null) {
            MicroText("JELLYFIN / PRIVATE CINEMA", accent)
            Text(when { collection.loading -> "正在布置放映厅…"; collection.error != null -> "暂时无法加载作品"; else -> "这里还没有作品" },
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Normal, maxLines = 2)
            if (collection.loading) SkeletonText(Modifier.fillMaxWidth(.7f), 2)
            else {
                Text(collection.error?.let(::browseErrorText) ?: "添加影片后，便可在这里开启放映。",
                    color = CinemaMuted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            actions()
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (media.year > 0) CinemaBadge(media.year.toString())
            if (media.ticks > 0) CinemaBadge("${media.ticks / 600_000_000L} min")
            media.rating?.takeIf { it.isFinite() && it > 0 }?.let {
                CinemaBadge("★ ${String.format(Locale.ROOT, "%.1f", it)}", accent)
            }
        }
        Text(media.seriesName.ifBlank { media.name }, color = Color.White, fontSize = 18.sp,
            fontWeight = FontWeight.Medium, lineHeight = 25.sp, letterSpacing = .45.sp,
            maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        if (!compact && media.originalTitle.isNotBlank() && media.originalTitle != media.name) {
            MicroText(media.originalTitle.uppercase(Locale.ROOT), CinemaMuted, spacing = 2f)
        }
        if (!trackingStatus.isNullOrBlank()) Text(trackingStatus, color = accent.copy(alpha = .85f),
            fontSize = 6.5.sp, lineHeight = 10.sp, letterSpacing = .4.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showDescription && media.overview.isNotBlank()) Text(media.overview,
            Modifier.widthIn(max = 230.dp), color = CinemaMuted, fontSize = 7.sp,
            fontWeight = FontWeight.Light, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(if (compact) 3.dp else 8.dp))
        actions()
    }
}

@Composable
private fun CinemaActions(label: String, play: () -> Unit, details: () -> Unit, more: () -> Unit) {
    val memory = LocalScreenFocus.current
    val stageNavigation = Modifier.focusProperties {
        up = memory?.requesters?.get("tunnel:screen") ?: FocusRequester.Default
        down = memory?.requesters?.get("tunnel:screen") ?: FocusRequester.Default
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CinemaAction(label, Glyph.Play, play, stageNavigation.focusTarget("cinema:play"), primary = true, showLabel = true)
        CinemaAction("详情", Glyph.Info, details, stageNavigation.focusTarget("cinema:detail"), showLabel = true)
        CinemaAction("全部作品", Glyph.Library, more, stageNavigation.focusTarget("cinema:all"))
    }
}

/** Small, local controls keep the stage quiet while preserving a clear remote focus ring. */
@Composable
private fun CinemaAction(
    label: String, icon: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, showLabel: Boolean = false, selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusAmount by animateFloatAsState(if (focused) 1f else 0f, rememberMotionSpec(160), label = "cinemaActionFocus")
    val shape = RoundedCornerShape(4.dp)
    val foreground = if (primary) CinemaBlack else if (selected) CinemaStyle.Accent else Color.White.copy(alpha = .6f + .4f * focusAmount)
    val labelVisible = showLabel || selected || focused
    Row(modifier
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = label; role = Role.Button; this.selected = selected }
        .graphicsLayer { scaleX = 1f + .04f * focusAmount; scaleY = scaleX }
        .background(if (primary) Color(0xFFECEEEB) else Color.White.copy(alpha = .08f * focusAmount), shape)
        .border(1.dp, Color.White.copy(alpha = .8f * focusAmount), shape)
        .stableAction(enabled = true, onClick = onClick)
        .height(23.dp)
        .then(if (labelVisible) Modifier.padding(horizontal = if (primary) 12.dp else 7.dp) else Modifier.width(23.dp)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        AppIcon(icon, Modifier.size(if (labelVisible) 8.dp else 10.dp), foreground)
        if (labelVisible) Text(label, color = foreground, fontSize = 6.5.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun CinemaBadge(label: String, color: Color = Color(0xFFDDE2E9)) {
    Text(label, Modifier.background(Color.White.copy(alpha = .055f), RoundedCornerShape(4.dp))
        .border(.5.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
        color = color, fontSize = 5.5.sp, fontFamily = FontFamily.Monospace, letterSpacing = .5.sp, maxLines = 1)
}

@Composable
private fun CinemaFooter(
    cursor: Int, count: Int, accent: Color, modifier: Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                MicroText("↑ ↓  切换栏目", CinemaMuted.copy(alpha = .65f), spacing = .5f)
                MicroText("← →  切换作品", CinemaMuted.copy(alpha = .65f), spacing = .5f)
                MicroText("OK  播放", CinemaMuted.copy(alpha = .65f), spacing = .5f)
                MicroText("返回  快捷入口", CinemaMuted.copy(alpha = .65f), spacing = .5f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val start = (cursor - 4).coerceAtLeast(0).coerceAtMost((count - 9).coerceAtLeast(0))
                (start until (start + 9).coerceAtMost(count)).forEach { dot ->
                    val width by animateDpAsState(if (dot == cursor) 18.dp else 4.dp, rememberMotionSpec(300), label = "cinemaDot")
                    Box(Modifier.width(width).height(3.dp).background(if (dot == cursor) accent else Color.White.copy(alpha = .22f), CircleShape))
                }
            }
    }
}

@Composable
private fun MicroText(text: String, color: Color, spacing: Float = 1.5f) {
    Text(text, color = color, fontSize = 6.sp, fontFamily = FontFamily.Monospace,
        letterSpacing = spacing.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
