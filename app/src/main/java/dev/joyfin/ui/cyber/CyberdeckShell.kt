package dev.joyfin.ui.cyber

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.app.Page
import dev.joyfin.app.UiState
import dev.joyfin.data.Home
import dev.joyfin.data.Media
import dev.joyfin.ui.cyber.components.MobileTwinWingCyberdeck
import dev.joyfin.ui.cyber.components.TvBridgeCommandSpine
import dev.joyfin.ui.cyber.sectors.HoloStageSector
import dev.joyfin.ui.cyber.sectors.MagneticSledSector
import dev.joyfin.ui.cyber.sectors.TacticalWingSector
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.ConnectionRoute
import dev.joyfin.ui.features.entry.EntryActions
import dev.joyfin.ui.features.entry.SessionRestoreRoute
import dev.joyfin.ui.features.home.components.homeContent
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

/**
 * 【CYBERDECK MATRIX 赛博全息机甲母舱】全局渲染壳体
 * 彻底颠覆传统的“单页跳切堆栈路由”，改由单舱多维视窗与空间姿态状态机驱动。
 */
@Composable
fun CyberdeckShell(
    ui: UiState,
    actions: BrowseActions,
    images: ArtworkSource,
    autoNext: Boolean,
    entryActions: EntryActions,
    subtitles: dev.joyfin.subtitles.SubtitlePageController? = null,
    audioOutputMode: dev.joyfin.data.AudioOutputMode = dev.joyfin.data.AudioOutputMode.AUTO,
    homeLayout: dev.joyfin.data.HomeLayoutPreferences = dev.joyfin.data.HomeLayoutPreferences(),
    audit: dev.joyfin.audit.LibraryAuditController? = null,
    updates: dev.joyfin.update.AppUpdateController? = null,
    tracking: dev.joyfin.browse.TrackingUiState? = null,
) {
    val deck = remember { CyberdeckController() }
    val tv = LocalDeviceLayout.current.television
    val isEntry = ui.page in setOf(Page.CONNECT, Page.LOGIN, Page.RESTORE)

    // 母舱背景与全息扫描线
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberPalette.VoidBlack),
    ) {
        CyberScanlines(Modifier.fillMaxSize())

        if (isEntry) {
            // === 认证与会话恢复：注入装甲外壳 ===
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(if (tv) 32.dp else 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (ui.page) {
                    Page.CONNECT, Page.LOGIN ->
                        ConnectionRoute(ui.entry, entryActions, login = ui.page == Page.LOGIN)
                    Page.RESTORE ->
                        SessionRestoreRoute(ui.entry, entryActions)
                    else -> Unit
                }
            }
        } else {
            // === CYBERDECK 赛博机甲多维母舱主控视窗 ===
            val home = remember(ui.home) { homeContent(ui.home) }
            val featuredMedia = remember(home) {
                home.resume.firstOrNull() ?: home.latest.firstOrNull() ?: home.rails.firstOrNull()?.items?.firstOrNull()
            }

            // 监听外部页面跳转以展开副翼或切换姿态
            LaunchedEffect(ui.page) {
                when (ui.page) {
                    Page.SEARCH -> deck.deployWing(WingType.SEARCH_RADAR)
                    Page.SUBTITLES -> deck.deployWing(WingType.SUBTITLE_BUS)
                    Page.SETTINGS -> deck.deployWing(WingType.MAINFRAME_SETTINGS)
                    Page.DETAIL -> {
                        // 若由外部触发详情，锁定目标并进入解构态
                        ui.detail.item?.let { deck.engageTarget(it) }
                    }
                    else -> {
                        if (deck.activeWing != WingType.NONE) {
                            deck.retractWings()
                        }
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                // 1. Sector Gamma: 机甲中枢战术龙骨 (TV 舰桥 HUD 顶栏)
                if (tv) {
                    TvBridgeCommandSpine(
                        actions = actions,
                        activeSector = deck.selectedSector,
                    )
                }

                // 2. 中央空间：四象限异形视窗多维展开
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val scrollState = rememberLazyListState()

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (tv) 24.dp else 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Sector Alpha: 全息主舞台视窗 (待机全息流 ↔ 战术绝密档案解构)
                        item("holo_stage") {
                            HoloStageSector(
                                featuredMedia = featuredMedia,
                                focusedTarget = deck.focusedTarget,
                                stance = deck.stance,
                                images = images,
                                actions = actions,
                                onDismissDeconstruct = { deck.dismissDeconstruct() },
                                modifier = Modifier
                                    .padding(horizontal = if (tv) 24.dp else 12.dp)
                                    .heightIn(min = 280.dp),
                            )
                        }

                        // Sector Beta: 磁悬浮弹仓滑轨阵列
                        item("magnetic_sleds") {
                            MagneticSledSector(
                                home = home,
                                images = images,
                                actions = actions,
                                onSelectMedia = { targetMedia ->
                                    deck.engageTarget(targetMedia)
                                },
                            )
                        }
                    }

                    // Sector Delta: 边缘折叠式战术副翼 (搜索雷达 / 字幕总线 / 中控机房)
                    TacticalWingSector(
                        activeWing = deck.activeWing,
                        actions = actions,
                        onRetract = { deck.retractWings() },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }

                // 3. 手机端：不对称悬浮双翼赛博甲板底栏
                if (!tv) {
                    MobileTwinWingCyberdeck(
                        actions = actions,
                        activeSector = deck.selectedSector,
                    )
                }
            }
        }
    }
}
