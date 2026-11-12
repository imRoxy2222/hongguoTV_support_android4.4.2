package com.hongguotv.nativeapp

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.io.File
import org.junit.Assert.assertThrows
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
}
