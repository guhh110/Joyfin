package dev.joyfin.ui.cyber

import androidx.compose.runtime.*
import dev.joyfin.data.Media

/**
 * 赛博全息母舱空间机动姿态 (Deck Stance)
 * 彻底颠覆“单页跳切堆栈路由”，改由母舱空间机动姿态驱动三维视窗的变形与展开。
 */
enum class DeckStance {
    STANDBY,        // 巡航侦察态：主舞台全息流 + 磁浮弹仓滑轨同时运转
    DECONSTRUCT,    // 战术解构态：目标锁定，外骨骼展开，全息核心投射档案、参数与单集数据仓（替代传统详情页）
    ENGAGED,        // 作战巡航态：主视窗切换全景驾驶舱 HUD，激光能量主轴控制（播放状态）
    WING_DEPLOYED,  // 战术副翼态：呼出左翼量子搜索雷达或右翼字幕/系统神经总线
}

enum class WingType {
    NONE,
    SEARCH_RADAR,         // 左副翼：量子搜索雷达
    SUBTITLE_BUS,         // 右副翼：字幕神经总线
    MAINFRAME_SETTINGS,   // 右副翼：机房中控台
}

/**
 * 赛博母舱全局状态控制器
 */
@Stable
class CyberdeckController {
    var stance by mutableStateOf(DeckStance.STANDBY)
        private set

    var activeWing by mutableStateOf(WingType.NONE)
        private set

    var focusedTarget by mutableStateOf<Media?>(null)
        private set

    var selectedSector by mutableStateOf("home") // "home", "tracking", "library"
        private set

    fun engageTarget(media: Media) {
        focusedTarget = media
        stance = DeckStance.DECONSTRUCT
    }

    fun dismissDeconstruct() {
        stance = DeckStance.STANDBY
    }

    fun launchCombatCruise() {
        stance = DeckStance.ENGAGED
    }

    fun abortCombatCruise() {
        if (focusedTarget != null) {
            stance = DeckStance.DECONSTRUCT
        } else {
            stance = DeckStance.STANDBY
        }
    }

    fun deployWing(wing: WingType) {
        activeWing = wing
        stance = DeckStance.WING_DEPLOYED
    }

    fun retractWings() {
        activeWing = WingType.NONE
        stance = if (focusedTarget != null) DeckStance.DECONSTRUCT else DeckStance.STANDBY
    }

    fun selectSector(sector: String) {
        selectedSector = sector
        if (stance == DeckStance.DECONSTRUCT) {
            stance = DeckStance.STANDBY
        }
    }
}

val LocalCyberdeckController = staticCompositionLocalOf { CyberdeckController() }
