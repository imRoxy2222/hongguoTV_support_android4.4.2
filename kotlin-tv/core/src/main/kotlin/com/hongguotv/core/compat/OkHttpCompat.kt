// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.core.compat

/**
 * OkHttp 4.x companion extensions re-implemented on top of OkHttp 3.12.x.
 *
 * OkHttp 4.x requires Android 5.0 (API 21) at runtime, while 3.12.x is the last
 * branch that runs on Android 4.4 (API 19). Preserving these call-site names keeps
 * every network caller in core/ and app/ unchanged.
 */
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.ByteString
import java.io.File

fun String.toMediaType(): MediaType =
    MediaType.parse(this) ?: throw IllegalArgumentException("Invalid media type: " + this)

fun String.toRequestBody(contentType: MediaType? = null): RequestBody =
    RequestBody.create(contentType, this)

fun ByteArray.toRequestBody(
    contentType: MediaType? = null,
    offset: Int = 0,
    byteCount: Int = size,
): RequestBody = RequestBody.create(contentType, this, offset, byteCount)

fun ByteString.toRequestBody(contentType: MediaType? = null): RequestBody =
    RequestBody.create(contentType, toByteArray())

fun File.toRequestBody(contentType: MediaType? = null): RequestBody =
    RequestBody.create(contentType, this)

fun String.toResponseBody(contentType: MediaType? = null): ResponseBody =
    ResponseBody.create(contentType, this)

fun ByteArray.toResponseBody(contentType: MediaType? = null): ResponseBody =
    ResponseBody.create(contentType, this)

fun ByteString.toResponseBody(contentType: MediaType? = null): ResponseBody =
    ResponseBody.create(contentType, toByteArray())

fun String.toHttpUrl(): HttpUrl =
    HttpUrl.parse(this) ?: throw IllegalArgumentException("Invalid URL: " + this)

fun String.toHttpUrlOrNull(): HttpUrl? = HttpUrl.parse(this)
