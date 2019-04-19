/*
 * Copyright (c) 2019 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.wendergram.glide

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.*
import java.io.IOException
import java.io.InputStream

@GlideModule
class ProgressAppGlideModule : AppGlideModule() {

  override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    super.registerComponents(context, glide, registry)
    val client = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
          val request = chain.request()
          val response = chain.proceed(request)
          val listener = DispatchingProgressListener()
          response.newBuilder()
              .body(OkHttpProgressResponseBody(request.url(), response.body()!!, listener))
              .build()
        }
        .build()
    glide.registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(client))
  }

  private interface ResponseProgressListener {
    fun update(url: HttpUrl, bytesRead: Long, contentLength: Long)
  }

  interface UIonProgressListener {
    /**
     * Control how often the listener needs an update. 0% and 100% will always be dispatched.
     * @return in percentage (0.2 = call [.onProgress] around every 0.2 percent of progress)
     */
    val granualityPercentage: Float

    fun onProgress(bytesRead: Long, expectedLength: Long)
  }

  private class DispatchingProgressListener internal constructor() :
      ProgressAppGlideModule.ResponseProgressListener {

    private val handler: Handler = Handler(Looper.getMainLooper())

    override fun update(url: HttpUrl, bytesRead: Long, contentLength: Long) {
      val key = url.toString()
      val listener = LISTENERS[key] ?: return
      if (contentLength <= bytesRead) {
        forget(key)
      }
      if (needsDispatch(key, bytesRead, contentLength, listener.granualityPercentage)) {
        handler.post { listener.onProgress(bytesRead, contentLength) }
      }
    }

    private fun needsDispatch(key: String, current: Long, total: Long, granularity: Float): Boolean {
      if (granularity == 0f || current == 0L || total == current) {
        return true
      }
      val percent = 100f * current / total
      val currentProgress = (percent / granularity).toLong()
      val lastProgress = PROGRESSES[key]
      return if (lastProgress == null || currentProgress != lastProgress) {
        PROGRESSES[key] = currentProgress
        true
      } else {
        false
      }
    }

    companion object {
      private val LISTENERS = HashMap<String?, UIonProgressListener>()
      private val PROGRESSES = HashMap<String?, Long>()

      internal fun forget(url: String?) {
        LISTENERS.remove(url)
        PROGRESSES.remove(url)
      }

      internal fun expect(url: String?, listener: UIonProgressListener) {
        LISTENERS[url] = listener
      }
    }
  }

  private class OkHttpProgressResponseBody internal constructor(
      private val url: HttpUrl,
      private val responseBody: ResponseBody,
      private val progressListener: ResponseProgressListener) : ResponseBody() {

    private var bufferedSource: BufferedSource? = null

    override fun contentType(): MediaType {
      return responseBody.contentType()!!
    }

    override fun contentLength(): Long {
      return responseBody.contentLength()
    }

    override fun source(): BufferedSource {
      if (bufferedSource == null) {
        bufferedSource = Okio.buffer(source(responseBody.source()))
      }
      return this.bufferedSource!!
    }

    private fun source(source: Source): Source {
      return object : ForwardingSource(source) {
        var totalBytesRead = 0L

        @Throws(IOException::class)
        override fun read(sink: Buffer, byteCount: Long): Long {
          val bytesRead = super.read(sink, byteCount)
          val fullLength = responseBody.contentLength()
          if (bytesRead.toInt() == -1) { // this source is exhausted
            totalBytesRead = fullLength
          } else {
            totalBytesRead += bytesRead
          }
          progressListener.update(url, totalBytesRead, fullLength)
          return bytesRead
        }
      }
    }
  }

  companion object {

    fun forget(url: String?) {
      ProgressAppGlideModule.DispatchingProgressListener.forget(url)
    }

    fun expect(url: String?, listener: ProgressAppGlideModule.UIonProgressListener) {
      ProgressAppGlideModule.DispatchingProgressListener.expect(url, listener)
    }
  }
}
