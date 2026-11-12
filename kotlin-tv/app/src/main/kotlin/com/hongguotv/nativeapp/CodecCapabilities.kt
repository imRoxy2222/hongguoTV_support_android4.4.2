// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import android.media.MediaCodecList
import android.media.MediaFormat
import com.hongguotv.core.Rendition
import com.hongguotv.core.VideoTracks

/**
 * Device decoder capability probe. TV boxes commonly cap AVC decoders near 1920x1088 with
 * width/height checked independently, so portrait sizes like 1080x1920 are rejected while landscape
 * 1920x1080 plays fine — and some boxes claim support they don't have. Learned runtime failures
 * (persisted across restarts) override the static probe.
 */
object CodecCapabilities {
    /** MediaCodecList(int)/getCodecInfos() are API 21+; KitKat uses the indexed legacy API. */
    private fun decoderInfos(): Array<android.media.MediaCodecInfo> =
        if (Build.VERSION.SDK_INT >= 21) ModernCodecList.infos() else legacyDecoderInfos()

    /**
     * The no-arg MediaCodecList constructor and getCodecCount/getCodecInfoAt are no longer in
     * the public compileSdk 36 stubs, but they still exist on KitKat devices. Resolve them
     * reflectively so the API 19 probe keeps working; callers catch failures and must never
     * let a broken ROM probe block playback.
     */
    private fun legacyDecoderInfos(): Array<android.media.MediaCodecInfo> {
        val type = MediaCodecList::class.java
        val list = type.getConstructor().newInstance()
        val count = type.getMethod("getCodecCount").invoke(list) as Int
        val at = type.getMethod("getCodecInfoAt", java.lang.Integer.TYPE)
        return Array(count) { i -> at.invoke(list, i) as android.media.MediaCodecInfo }
    }

    @RequiresApi(21)
    private object ModernCodecList {
        fun infos(): Array<android.media.MediaCodecInfo> =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
    }

    /**
     * VideoCapabilities is API 21+. On KitKat only the declared MIME support can be
     * checked; persisted runtime failures still override an over-optimistic result.
     */
    private fun legacySupports(type: String): Boolean =
        decoderInfos().any { info ->
            !info.isEncoder && runCatching { info.getCapabilitiesForType(type) }.isSuccess
        }

    @RequiresApi(21)
    private fun modernSupports(type: String, width: Int, height: Int): Boolean =
        decoderInfos().any { info ->
            !info.isEncoder &&
                runCatching {
                    info.getCapabilitiesForType(type).videoCapabilities?.isSizeSupported(width, height) == true
                }.getOrDefault(false)
        }

    private fun legacyCapabilityLines(): List<String> =
        decoderInfos()
            .filter { !it.isEncoder }
            .flatMap { info ->
                listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
                    .filter { type -> runCatching { info.getCapabilitiesForType(type) }.isSuccess }
                    .map { type -> "${info.name} $type (API 19: MIME declared)" }
            }

    @RequiresApi(21)
    private fun modernCapabilityLines(): List<String> =
        decoderInfos()
            .filter { !it.isEncoder }
            .flatMap { info ->
                listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
                    .filter { type -> runCatching { info.getCapabilitiesForType(type) }.isSuccess }
                    .map { type ->
                        val video = info.getCapabilitiesForType(type).videoCapabilities
                            ?: return@map "${info.name} $type (no VideoCapabilities)"
                        "${info.name} $type up to ${video.supportedWidths.upper}x${video.supportedHeights.upper}"
                    }
            }

    private val lessons = DecoderLessons()
    private var prefs: SharedPreferences? = null
    private val verdicts = HashMap<String, Boolean>()
    @Volatile private var logged = false

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.getSharedPreferences("native-decoder-lessons-v1", Context.MODE_PRIVATE)
        prefs = store
        lessons.restore(store.getStringSet("failures", emptySet()) ?: emptySet())
    }

    fun mime(codec: String): String? =
        when (codec.lowercase()) {
            "h264",
            "avc",
            "avc1",
            "avc3" -> MediaFormat.MIMETYPE_VIDEO_AVC
            "bytevc1",
            "hvc1",
            "hev1",
            "h265",
            "hevc" -> MediaFormat.MIMETYPE_VIDEO_HEVC
            else -> null
        }

    fun supports(codec: String, width: Int?, height: Int?): Boolean {
        if (lessons.fails(codec, width, height)) return false
        val type = mime(codec) ?: return true
        if (width == null || height == null || width <= 0 || height <= 0) return true
        val key = "$type $width $height"
        synchronized(verdicts) {
            verdicts[key]?.let {
                return it
            }
        }
        val verdict =
            runCatching {
                    if (Build.VERSION.SDK_INT >= 21) modernSupports(type, width, height)
                    else legacySupports(type)
                }
                .getOrDefault(true) // A broken ROM probe must not block playback.
        synchronized(verdicts) { verdicts[key] = verdict }
        return verdict
    }

    fun supports(rendition: Rendition) =
        supports(rendition.codec, rendition.width, rendition.height)

    fun supports(track: VideoTracks.Track) = supports(track.fourcc, track.width, track.height)

    /**
     * Records a runtime decode failure; true when the static probe had claimed support
     * (over-reporting).
     */
    fun learnFailure(codec: String, width: Int?, height: Int?): Boolean {
        val overReported = supports(codec, width, height)
        val key = lessons.remember(codec, width, height) ?: return false
        prefs?.edit()?.putStringSet("failures", lessons.keys())?.apply()
        android.util.Log.w(
            "HongguoTV",
            "Learned decoder failure $key (static probe claimed support: $overReported)",
        )
        return overReported
    }

    fun resetLearned() {
        lessons.reset()
        prefs?.edit()?.putStringSet("failures", lessons.keys())?.apply()
    }

    fun learnedCount() = lessons.size()

    fun logDeviceCapabilities() {
        if (logged) return
        logged = true
        val lines =
            runCatching {
                    if (Build.VERSION.SDK_INT >= 21) modernCapabilityLines()
                    else legacyCapabilityLines()
                }
                .getOrNull() ?: return
        android.util.Log.i("HongguoTV", "Decoder capabilities: ${lines.joinToString("; ")}")
    }
}
