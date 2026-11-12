// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.os.Build
import android.view.View

/**
 * Android 5.0（API 21）新增的视图装饰属性。
 *
 * 这些 setter 在 KitKat 的 `View` 类里并不存在，直接调用会抛
 * `NoSuchMethodError`，属于运行期崩溃而非编译期告警。因此一律按
 * `Build.VERSION.SDK_INT` 分流，而不是用 lint 抑制规避
 * （约束见 `ANDROID_COMPATIBILITY.md` 的 Build and verification requirements）。
 *
 * API 19 上这些属性全部跳过：轮廓裁剪退化为直角、阴影与 Z 轴位移不生效，
 * 布局、焦点与播放行为不受影响。
 */
internal object Api21Compat {
    /** 让子视图按背景轮廓裁剪（圆角卡片）。API 19 没有 Outline 机制，退化为不裁剪。 */
    fun clipToOutline(view: View) {
        if (Build.VERSION.SDK_INT >= 21) view.clipToOutline = true
    }

    /** 背景阴影高度，单位为像素。API 19 不绘制阴影，忽略即可。 */
    fun setElevation(view: View, px: Int) {
        if (Build.VERSION.SDK_INT >= 21) view.elevation = px.toFloat()
    }

    /** 焦点态沿 Z 轴抬升的过渡动画。API 19 没有 Z 轴，跳过整段动画。 */
    fun animateTranslationZ(view: View, px: Int, durationMs: Long = 180) {
        if (Build.VERSION.SDK_INT >= 21) {
            view.animate().translationZ(px.toFloat()).setDuration(durationMs).start()
        }
    }
}
