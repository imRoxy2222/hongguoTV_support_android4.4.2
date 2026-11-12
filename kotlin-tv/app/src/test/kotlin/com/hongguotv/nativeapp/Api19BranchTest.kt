package com.hongguotv.nativeapp

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.view.View
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Robolectric 4.14.1 DefaultSdkProvider starts at 5.0.2_r3 (API 21), so an API 19 sandbox
 * cannot be created ("API level 19 is not available"). These tests run on API 21 but force
 * Build.VERSION.SDK_INT to 19 around pure branch checks, proving the KitKat paths avoid
 * API 21+ framework types. This is not a substitute for real Android 4.4.2 device testing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Api19BranchTest {
    private fun asKitKat(block: () -> Unit) {
        val original = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 19)
        try {
            block()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", original)
        }
    }

    private fun context(): Activity =
        Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test fun networkRegistrationIsANoOpOnKitKat() {
        val manager = context().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {}
        asKitKat { NetworkCompat.register(manager, callback) }
    }

    @Test fun lanAddressUsesLegacyInterfacesOnKitKat() {
        val manager = context().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        asKitKat { NetworkCompat.lanAddress(manager) }
    }

    @Test fun packageInstallerIsRejectedOnKitKat() {
        val activity = context()
        asKitKat {
            assertThrows(UnsupportedOperationException::class.java) {
                UpdateInstallSession.submit(activity, File("unused.apk"))
            }
        }
    }

    /**
     * 上游 0.13.2 在首页直接写了 `clipToOutline` / `elevation` / `translationZ`，
     * 这些 setter 在 KitKat 的 View 上并不存在，真机会抛 NoSuchMethodError。
     * 这里确认 19 分支整段跳过，不会触碰任何 API 21 属性。
     */
    @Test fun api21ViewDecorationsAreSkippedOnKitKat() {
        val view = View(context())
        asKitKat {
            Api21Compat.clipToOutline(view)
            Api21Compat.setElevation(view, 8)
            Api21Compat.animateTranslationZ(view, 4)
        }
        assertFalse(view.clipToOutline)
        assertEquals(0f, view.elevation, 0f)
    }

    /** 同一组入口在 Lollipop 及以上必须照旧生效，避免修复把视觉效果一起关掉。 */
    @Test fun api21ViewDecorationsStillApplyFromLollipop() {
        val view = View(context())
        Api21Compat.clipToOutline(view)
        Api21Compat.setElevation(view, 8)
        assertTrue(view.clipToOutline)
        assertEquals(8f, view.elevation, 0f)
    }
}
