// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context

/**
 * 弹幕显示偏好。
 *
 * 播放器菜单的「弹幕设置」与设置页的「弹幕」分类读写同一份 `social` 偏好，
 * 所以任一处改动都会成为下一次播放的默认显示方式，两处标签也始终一致。
 * 读取时统一收敛取值范围，历史版本写入的越界值不会破坏绘制或轨道排程。
 */
internal class DanmakuPreferences(context: Context) {
    private val store = context.getSharedPreferences("social", 0)

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.edit().putBoolean(KEY_ENABLED, value).apply()

    var merge: Boolean
        get() = store.getBoolean(KEY_MERGE, true)
        set(value) = store.edit().putBoolean(KEY_MERGE, value).apply()

    /** 字号（dp 值）。播放器与设置页都按 20–32、步进 4 调整。 */
    var font: Int
        get() = store.getInt(KEY_FONT, DEFAULT_FONT).coerceIn(MIN_FONT, MAX_FONT)
        set(value) = store.edit().putInt(KEY_FONT, value.coerceIn(MIN_FONT, MAX_FONT)).apply()

    /** 0 表示按屏幕高度自动推算轨道数，正数为固定行数。 */
    var lanes: Int
        get() = store.getInt(KEY_LANES, AUTO_LANES).coerceIn(AUTO_LANES, MAX_LANES)
        set(value) = store.edit().putInt(KEY_LANES, value.coerceIn(AUTO_LANES, MAX_LANES)).apply()

    /** [AREA_TOP] / [AREA_MIDDLE] / [AREA_BOTTOM] / [AREA_FULL]；未知值回退上部。 */
    var area: String
        get() = store.getString(KEY_AREA, AREA_TOP).takeIf { it in AREAS } ?: AREA_TOP
        set(value) = store.edit().putString(KEY_AREA, if (value in AREAS) value else AREA_TOP).apply()

    var alpha: Int
        get() = store.getInt(KEY_ALPHA, DEFAULT_ALPHA).coerceIn(MIN_ALPHA, MAX_ALPHA)
        set(value) = store.edit().putInt(KEY_ALPHA, value.coerceIn(MIN_ALPHA, MAX_ALPHA)).apply()

    /** 单条弹幕完整穿过屏幕的时间（毫秒）。 */
    var travel: Int
        get() = store.getInt(KEY_TRAVEL, TRAVEL_SLOW).coerceIn(MIN_TRAVEL, MAX_TRAVEL)
        set(value) = store.edit().putInt(KEY_TRAVEL, value.coerceIn(MIN_TRAVEL, MAX_TRAVEL)).apply()

    /** 播放器菜单的紧凑标签：少 / 标准（自动） / 多 / 自定义 N 行。 */
    val densityLabel: String
        get() =
            when (val rows = lanes) {
                AUTO_LANES -> "标准（自动）"
                4 -> "少"
                12 -> "多"
                else -> "自定义 $rows 行"
            }

    /** 设置页选择器用的行数文案，与选项列表措辞一致。 */
    val densityRowLabel: String
        get() = laneText(lanes)

    val areaLabel: String
        get() = areaText(area)

    val speedLabel: String
        get() = if (travel == TRAVEL_FAST) "标准（9 秒）" else "慢速（12 秒）"

    companion object {
        const val AREA_TOP = "top"
        const val AREA_MIDDLE = "middle"
        const val AREA_BOTTOM = "bottom"
        const val AREA_FULL = "full"

        const val MIN_FONT = 20
        const val MAX_FONT = 32
        const val DEFAULT_FONT = 24

        const val AUTO_LANES = 0
        const val MAX_LANES = 12

        const val MIN_ALPHA = 20
        const val MAX_ALPHA = 100
        const val DEFAULT_ALPHA = 85

        const val TRAVEL_FAST = 9000
        const val TRAVEL_SLOW = 12000
        const val MIN_TRAVEL = 4000
        const val MAX_TRAVEL = 20000

        private const val KEY_ENABLED = "enabled"
        private const val KEY_MERGE = "merge"
        private const val KEY_FONT = "font"
        private const val KEY_LANES = "lanes"
        private const val KEY_AREA = "area"
        private const val KEY_ALPHA = "alpha"
        private const val KEY_TRAVEL = "travel"

        /** 设置页选项顺序，与播放器菜单的循环顺序保持一致。 */
        val AREA_ORDER = listOf(AREA_TOP, AREA_MIDDLE, AREA_BOTTOM, AREA_FULL)

        /** 行数选项：0 为自动，其余为固定行数。 */
        val LANE_ORDER = listOf(AUTO_LANES, 4, 6, 8, 10, MAX_LANES)

        val FONT_ORDER = listOf(20, 24, 28, 32)
        val ALPHA_ORDER = listOf(60, 75, 85, 100)
        val TRAVEL_ORDER = listOf(TRAVEL_FAST, TRAVEL_SLOW)

        private val AREAS = AREA_ORDER.toSet()

        fun laneText(value: Int) =
            if (value == AUTO_LANES) "自动（按屏幕高度）" else "$value 行"

        fun areaText(value: String) =
            when (value) {
                AREA_MIDDLE -> "中部"
                AREA_BOTTOM -> "下部"
                AREA_FULL -> "全屏"
                else -> "上部"
            }
    }
}
