package dev.joyfin.ui.cyber

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 赛博机械与战术 HUD 专属调色板 (Cyber-Mecha Matrix Palette)
 * 彻底颠覆平淡灰阶，采用深空钛合金装甲底色搭配高饱和度激光霓虹与战术警示色。
 */
object CyberPalette {
    // === 装甲底板与外骨骼 ===
    val VoidBlack = Color(0xFF060709)       // 深渊虚空黑（全局根底色）
    val SlateMetal = Color(0xFF0C1017)      // 钛合金机身底板
    val PanelSurface = Color(0xFF141922)    // 悬浮装甲舱表面
    val PanelElevated = Color(0xFF1B2330)   // 浮动战术卡片与按键底板
    val PanelPressed = Color(0xFF242E3E)    // 液压按压底色

    // === 核心高能激光与霓虹 (Neon Lasers) ===
    val LaserCyan = Color(0xFF00F0FF)       // 激光霓虹青（主控能量色、TV焦点瞄准、高亮激活态）
    val LaserCyanGlow = Color(0x6600F0FF)   // 激光青发光光晕
    val WarningAmber = Color(0xFFFFB800)    // 战术警示橙金（副强调色、评分星标、进行中任务）
    val WarningAmberGlow = Color(0x66FFB800)// 警示橙光晕
    val MatrixGreen = Color(0xFF00FF66)     // 矩阵核能绿（已看完打勾、系统正常联机）
    val AlertCrimson = Color(0xFFFF0055)    // 高危警报红（未看标记、错误故障、从头重播）

    // === 文字与战术刻印 (Tactical Markings) ===
    val TextBright = Color(0xFFF2F5F8)      // 激光白色主标题文字
    val TextSecondary = Color(0xFF94A3B8)   // 战术中灰副标题
    val TextDim = Color(0xFF4B5565)         // 暗刻度说明文字
    val TextCyan = LaserCyan                // 战术前缀高亮字

    // === 装甲缝隙与战术标尺 (Seams & Overlays) ===
    val ArmorSeam = Color(0x2E00F0FF)       // 装甲缝隙激光微光线
    val ArmorBorder = Color(0x3338455A)     // 钛合金边缘细边框
    val BorderFocus = LaserCyan             // 获焦外框
    val Scanline = Color(0x0A00F0FF)        // 全息扫描线半透明色彩
    val GridDots = Color(0x1A00F0FF)        // 战术全息雷达点阵

    // === 战术能量流渐变 (Plasma Conduits) ===
    val PlasmaCyanBrush = Brush.horizontalGradient(
        listOf(LaserCyan, Color(0xFF00A3FF))
    )
    val ReactorAmberBrush = Brush.horizontalGradient(
        listOf(WarningAmber, Color(0xFFFF7700))
    )
    val ArmorGradient = Brush.verticalGradient(
        listOf(PanelSurface, SlateMetal)
    )
    val HologramGlowBrush = Brush.radialGradient(
        listOf(Color(0x3300F0FF), Color.Transparent)
    )
}
