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

package com.raywenderlich.android.wendergram

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.raywenderlich.android.wendergram.glide.GlideImageLoader
import com.raywenderlich.android.wendergram.photo.PhotoAdapter
import com.raywenderlich.android.wendergram.provider.PhotoPreloadModelProvider
import com.raywenderlich.android.wendergram.provider.PhotoProvider
import kotlinx.android.synthetic.main.activity_main.*


/**
 * Main Screen
 */
class MainActivity : AppCompatActivity() {

  private val photoProvider = PhotoProvider()
  private var photoAdapter: PhotoAdapter? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    photoAdapter = PhotoAdapter(photoProvider.photos)
    photoAdapter?.setItemClickListener { it ->
      startActivity(EditPhotoActivity.newIntent(this@MainActivity, it))
    }

    rvPhotos.layoutManager = GridLayoutManager(this, 3)
    rvPhotos.adapter = photoAdapter

    val profilePicUrl = "https://images.unsplash.com/photo-1482849297070-f4fae2173efe"
    loadProfilePic(profilePicUrl)

    preloadRecyclerView()
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.main, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem?): Boolean {
    return when {
      item?.itemId == R.id.clear_cache -> {
        val handler = Handler(Looper.getMainLooper())
        Thread(Runnable {
          Glide.get(this).clearDiskCache()
          handler.post { rvPhotos.adapter?.notifyDataSetChanged() }
        }).start()
        Glide.get(this).clearMemory()
        true
      }
      item?.itemId == R.id.refresh -> {
        rvPhotos.adapter?.notifyDataSetChanged()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun loadProfilePic(profilePicUrl: String) {
    GlideImageLoader(ivProfile, progressBar)
        .load(profilePicUrl,
            RequestOptions()
                .placeholder(R.drawable.ic_profile_placeholder)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transform(CircleCrop())
        )
  }

  private fun preloadRecyclerView() {
    val preloadSizeProvider = ViewPreloadSizeProvider<String>()
    val modelProvider = PhotoPreloadModelProvider(this, photoProvider.photos)
    val preLoader = RecyclerViewPreloader<String>(
        this, modelProvider, preloadSizeProvider, 30)

    rvPhotos.addOnScrollListener(preLoader)
  }

}
