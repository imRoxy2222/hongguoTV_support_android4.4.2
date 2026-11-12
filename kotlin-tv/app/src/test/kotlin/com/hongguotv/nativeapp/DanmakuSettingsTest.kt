// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** 设置页「弹幕」分类与播放器「弹幕设置」读写同一份偏好，标签保持一致。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class DanmakuSettingsTest {
    private fun get(target: Any, name: String) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun texts(view: View): List<TextView> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else if (view is TextView) listOf(view) else emptyList()

    private fun store(context: Context) = context.getSharedPreferences("social", 0)

    @Test
    fun preferencesClampPersistedValuesAndKeepLabelsInSync() {
        val context = RuntimeEnvironment.getApplication()
        store(context)
            .edit()
            .putInt("font", 99)
            .putInt("lanes", 40)
            .putInt("alpha", 5)
            .putString("area", "side")
            .putInt("travel", 99)
            .commit()
        val prefs = DanmakuPreferences(context)
        assertEquals(DanmakuPreferences.MAX_FONT, prefs.font)
        assertEquals(DanmakuPreferences.MAX_LANES, prefs.lanes)
        assertEquals(DanmakuPreferences.MIN_ALPHA, prefs.alpha)
        assertEquals(DanmakuPreferences.AREA_TOP, prefs.area)
        assertEquals(DanmakuPreferences.MIN_TRAVEL, prefs.travel)
        prefs.lanes = 0
        assertEquals("标准（自动）", prefs.densityLabel)
        assertEquals("自动（按屏幕高度）", prefs.densityRowLabel)
        prefs.lanes = 6
        assertEquals("自定义 6 行", prefs.densityLabel)
        assertEquals("6 行", prefs.densityRowLabel)
        prefs.area = DanmakuPreferences.AREA_FULL
        assertEquals("全屏", prefs.areaLabel)
        prefs.travel = DanmakuPreferences.TRAVEL_FAST
        assertEquals("标准（9 秒）", prefs.speedLabel)
    }

    @Test
    fun settingsDanmakuPageEditsThePreferenceThePlayerDialogReads() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            val library = get(activity, "library") as Library
            (get(library, "loader") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            (get(get(activity, "cloudAccount")!!, "worker") as ExecutorService)
                .submit {}
                .get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            MainActivity::class
                .java
                .getDeclaredMethod("switchTab", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(activity, 6)
            val root = activity.findViewById<View>(android.R.id.content)
            root.measure(
                View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 960, 540)
            root.findViewWithTag<View>("settings-category-1").performClick()
            val rows = texts(root).map { it.text.toString() }
            listOf(
                    "默认显示弹幕",
                    "弹幕字号",
                    "弹幕行数",
                    "弹幕区域",
                    "弹幕不透明度",
                    "弹幕速度",
                    "相同弹幕合并",
                )
                .forEach { title -> assertTrue("缺少设置项 $title", rows.any { it.startsWith(title) }) }

            val before = store(activity).getBoolean("enabled", true)
            val enabledRow = texts(root).first { it.text.toString() == "默认显示弹幕" }
            (enabledRow.parent as View).performClick()
            assertEquals(!before, store(activity).getBoolean("enabled", true))
            assertEquals(
                !before,
                ((enabledRow.parent as ViewGroup).getChildAt(1) as Switch).isChecked,
            )

            // 选择器写回偏好并就地刷新标签，不依赖离开页面重进。
            val fontRow = texts(root).first { it.text.toString().startsWith("弹幕字号") }
            fontRow.performClick()
            val picker = ShadowAlertDialog.getLatestAlertDialog()
            val pickerRoot = picker.findViewById<View>(android.R.id.content)
            pickerRoot.findViewWithTag<View>("choice-2").performClick()
            ShadowLooper.idleMainLooper()
            assertEquals(28, store(activity).getInt("font", 0))
            assertEquals("弹幕字号  ·  28", fontRow.text.toString())

            // 播放器菜单入口读到的是同一个值。
            val social = TvSocial(activity, { null }, { null })
            try {
                social.settings(View(activity))
                val dialog = ShadowDialog.getLatestDialog()
                val label =
                    texts(dialog.window!!.decorView)
                        .first { it.text.toString().startsWith("弹幕：") }
                assertEquals("弹幕：${if (before) "关" else "开"}", label.text.toString())
            } finally {
                social.destroy()
            }
        } finally {
            controller.destroy()
        }
    }
}
