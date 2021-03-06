package com.sjn.stamp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.view.View
import android.widget.ImageView
import com.sjn.stamp.R
import com.sjn.stamp.ui.MediaBrowsable
import com.sjn.stamp.ui.custom.TextDrawable
import java.util.*

object AlbumArtHelper {
    private const val IMAGE_VIEW_ALBUM_ART_TYPE_BITMAP = "bitmap"
    private const val IMAGE_VIEW_ALBUM_ART_TYPE_TEXT = "text"

    fun readBitmapSync(context: Context, url: String?, title: String?): Bitmap {
        return readBitmapSync(context, Uri.parse(url), title)
    }

    fun readBitmapSync(context: Context, url: Uri?, title: String?): Bitmap {
        return try {
            readBitmap(context, url) ?: AlbumArtHelper.createTextBitmap(title)
        } catch (e: Exception) {
            AlbumArtHelper.createTextBitmap(title)
        }
    }

    fun readBitmapAsync(context: Context, url: String?, title: String?, onLoad: (Bitmap) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            onLoad(readBitmapSync(context, url, title))
        }
    }

    fun readBitmapAsync(context: Context, url: Uri?, title: String?, onLoad: (Bitmap) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            onLoad(readBitmapSync(context, url, title))
        }
    }

    fun reload(context: Context, view: ImageView, imageType: String?, artUrl: String?, text: String?) {
        view.setTag(R.id.image_view_album_art_url, artUrl)
        if (imageType == "bitmap") {
            view.loadBitmap(context, artUrl, text)
        } else if (imageType == "text") {
            setPlaceHolder(context, view, text)
        }

    }

    fun update(context: Context?, view: ImageView?, artUrl: String?, text: CharSequence?) {
        context?.let { _context ->
            view?.let { _view ->
                artUrl?.let { _artUrl ->
                    updateAlbumArtImpl(_context, _view, _artUrl, text?.toString() ?: "")
                }
            }
        }
    }

    fun searchAndUpdate(context: Context, view: ImageView, title: String, query: String, mediaBrowsable: MediaBrowsable?) {
        if (view.getTag(R.id.image_view_album_art_query) == query && view.getTag(R.id.image_view_album_art_query_result) != null) {
            AlbumArtHelper.update(context, view, view.getTag(R.id.image_view_album_art_query_result).toString(), title)
            return
        }
        view.setTag(R.id.image_view_album_art_query, query)
        view.setTag(R.id.image_view_album_art_query_result, null)
        AlbumArtHelper.setPlaceHolder(context, view, title)
        mediaBrowsable?.searchMediaItems(query, null, object : MediaBrowserCompat.SearchCallback() {
            override fun onSearchResult(query: String, extras: Bundle?, items: List<MediaBrowserCompat.MediaItem>) {
                updateByExistingAlbumArt(context, view, title, query, items)
            }

        })
    }

    private fun setPlaceHolder(context: Context?, view: ImageView?, text: String?) {
        context?.runOnUiThread {
            view?.setTextDrawable(text)
        }
    }

    private fun updateByExistingAlbumArt(context: Context, view: ImageView, text: String, query: String, items: List<MediaBrowserCompat.MediaItem>) {
        if (view.getTag(R.id.image_view_album_art_query) != query) {
            return
        }
        if (items.isEmpty()) {
            setPlaceHolder(context, view, text)
            return
        }
        Thread(Runnable {
            view.loadBitmap(context, items.first().description.iconUri.toString()) {
                if (items.size > 1) {
                    updateByExistingAlbumArt(context, view, text, query, items.drop(1))
                }
            }
        }).start()
    }

    private fun updateAlbumArtImpl(context: Context, view: ImageView, artUrl: String, text: String) {
        view.setTag(R.id.image_view_album_art_url, artUrl)
        if (artUrl.isEmpty()) {
            setPlaceHolder(context, view, text)
            return
        }
        Thread(Runnable {
            view.loadBitmap(context, artUrl, text)
        }).start()
    }

    private fun ImageView.setTextDrawable(text: String?) {
        setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
        setTag(R.id.image_view_album_art_text, text)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        setImageDrawable(createTextDrawable(text ?: ""))
    }

    private fun ImageView.setAlbumArtBitmap(bitmap: Bitmap) {
        setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_BITMAP)
        setImageBitmap(bitmap)
    }

    private fun ImageView.loadBitmap(context: Context, artUrl: String?, onNothing: () -> Unit) {
        var bitmap: Bitmap? = null
        try {
            readBitmap(context, Uri.parse(artUrl))?.let {
                bitmap = it
            }
        } catch (e: Exception) {
        }
        bitmap?.let {
            context.runOnUiThread {
                setTag(R.id.image_view_album_art_url, artUrl)
                setTag(R.id.image_view_album_art_query_result, artUrl)
                setAlbumArtBitmap(it)
            }
        } ?: run {
            onNothing()
        }
    }

    private fun ImageView.loadBitmap(context: Context, artUrl: String?, text: String?) {
        loadBitmap(context, artUrl) { setPlaceHolder(context, this, text) }
    }

    private fun createTextBitmap(text: CharSequence?) =
            toBitmap(createTextDrawable(text?.toString() ?: ""))

    private fun createTextDrawable(text: String): TextDrawable = TextDrawable.builder()
            .beginConfig()
            .useFont(Typeface.DEFAULT)
            .bold()
            .toUpperCase()
            .endConfig()
            .rect()
            .build(if (text.isEmpty()) "" else text[0].toString(), ColorGenerator.MATERIAL.getColor(text))

    fun toBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas()
        canvas.setBitmap(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun readBitmap(context: Context, uri: Uri?) = getThumbnail(context, uri)

    private fun getThumbnail(context: Context, uri: Uri?, size: Int = 256): Bitmap? {
        if (uri == null) {
            return null
        }
        val onlyBoundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, onlyBoundsOptions)
        }
        if (onlyBoundsOptions.outWidth == -1 || onlyBoundsOptions.outHeight == -1) {
            return null
        }
        val originalSize = if (onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth) onlyBoundsOptions.outHeight else onlyBoundsOptions.outWidth
        return context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = if (originalSize > size) originalSize / size else 1
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }
    }


    private class ColorGenerator private constructor(private val colors: List<Int>) {
        private val random: Random = Random(System.currentTimeMillis())

        val randomColor: Int
            get() = colors[random.nextInt(colors.size)]

        internal fun getColor(key: Any?): Int = if (key == null) colors[0] else colors[Math.abs(key.hashCode()) % colors.size]

        companion object {

            internal var DEFAULT: ColorGenerator

            internal var MATERIAL: ColorGenerator

            init {
                DEFAULT = create(Arrays.asList(
                        -0xe9c9c,
                        -0xa7aa7,
                        -0x65bc2,
                        -0x1b39d2,
                        -0x98408c,
                        -0xa65d42,
                        -0xdf6c33,
                        -0x529d59,
                        -0x7fa87f
                ))
                MATERIAL = create(Arrays.asList(
                        -0x1a8c8d,
                        -0xf9d6e,
                        -0x459738,
                        -0x6a8a33,
                        -0x867935,
                        -0x9b4a0a,
                        -0xb03c09,
                        -0xb22f1f,
                        -0xb24954,
                        -0x7e387c,
                        -0x512a7f,
                        -0x759b,
                        -0x2b1ea9,
                        -0x2ab1,
                        -0x48b3,
                        -0x5e7781,
                        -0x6f5b52
                ))
            }

            fun create(colorList: List<Int>): ColorGenerator {
                return ColorGenerator(colorList)
            }
        }
    }

}