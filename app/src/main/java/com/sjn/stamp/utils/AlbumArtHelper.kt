package com.sjn.stamp.utils

import android.app.Activity
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
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.view.View
import android.widget.ImageView
import com.sjn.stamp.R
import com.sjn.stamp.ui.MediaBrowsable
import com.sjn.stamp.ui.custom.TextDrawable
import java.io.FileNotFoundException
import java.util.*

object AlbumArtHelper {
    // Resolution reasonable for carrying around as an icon (generally in
    // MediaDescription.getIconBitmap). This should not be bigger than necessary, because
    // the MediaDescription object should be lightweight. If you set it too high and try to
    // serialize the MediaDescription, you may get FAILED BINDER TRANSACTION errors.
    private const val MAX_ART_WIDTH_ICON = 128  // pixels
    private const val MAX_ART_HEIGHT_ICON = 128  // pixels
    private const val IMAGE_VIEW_ALBUM_ART_TYPE_BITMAP = "bitmap"
    private const val IMAGE_VIEW_ALBUM_ART_TYPE_TEXT = "text"

    fun readBitmapSync(context: Context, url: String?, title: String?): Bitmap {
        return readBitmapSync(context, Uri.parse(url), title)
    }

    fun readBitmapSync(context: Context, url: Uri?, title: String?): Bitmap {
        return try {
            MediaStore.Images.Media.getBitmap(context.contentResolver, url)
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

    fun reload(activity: Activity, view: ImageView, bitmap: Bitmap?, imageType: String?, artUrl: String?, text: String?) {
        view.setTag(R.id.image_view_album_art_url, artUrl)
        view.setTag(R.id.image_view_album_art_text, text)
        view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
        if (imageType == "bitmap") {
            view.setImageBitmap(bitmap)
            try {
                view.setImageBitmap(MediaStore.Images.Media.getBitmap(activity.contentResolver, Uri.parse(artUrl)))
                view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_BITMAP)
            } catch (e: Exception) {
                view.setImageDrawable(createTextDrawable(text ?: ""))
                view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
                view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
        } else if (imageType == "text") {
            view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            view.setImageDrawable(createTextDrawable(text ?: ""))
        }

    }

    fun update(activity: Activity?, view: ImageView?, artUrl: String?, text: CharSequence?) {
        activity?.let { _activity ->
            view?.let { _view ->
                artUrl?.let { _artUrl ->
                    updateAlbumArtImpl(_activity, _view, _artUrl, text?.toString() ?: "")
                }
            }
        }
    }

    fun searchAndUpdate(activity: Activity, view: ImageView, title: String, query: String, mediaBrowsable: MediaBrowsable?) {
        if (view.getTag(R.id.image_view_album_art_query) == query && view.getTag(R.id.image_view_album_art_query_result) != null) {
            AlbumArtHelper.update(activity, view, view.getTag(R.id.image_view_album_art_query_result).toString(), title)
            return
        }
        view.setTag(R.id.image_view_album_art_query, query)
        view.setTag(R.id.image_view_album_art_query_result, null)
        AlbumArtHelper.setPlaceHolder(activity, view, title)
        mediaBrowsable?.search(query, null, object : MediaBrowserCompat.SearchCallback() {
            override fun onSearchResult(query: String, extras: Bundle?, items: List<MediaBrowserCompat.MediaItem>) {
                if (view.getTag(R.id.image_view_album_art_query) != query) {
                    return
                }
                for (metadata in items) {
                    if (metadata.description.iconUri != null) {
                        view.setTag(R.id.image_view_album_art_query_result, metadata.description.iconUri)
                        AlbumArtHelper.update(activity, view, metadata.description.iconUri.toString(), title)
                        break
                    }
                }
            }

        })
    }

    fun setPlaceHolder(activity: Activity?, view: ImageView?, text: String?) {
        activity?.runOnUiThread {
            view?.apply {
                setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
                view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                view.setImageDrawable(createTextDrawable(text ?: ""))
            }
        }
    }

    private fun updateAlbumArtImpl(activity: Activity, view: ImageView, artUrl: String, text: String) {
        view.setTag(R.id.image_view_album_art_url, artUrl)
        view.setTag(R.id.image_view_album_art_text, text)
        view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
        if (artUrl.isEmpty()) {
            //view.setImageDrawable(ContextCompat.getDrawable(activity, R.mipmap.ic_launcher));
            view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            view.setImageDrawable(createTextDrawable(text))
            return
        }

        Thread(Runnable {
            try {
                val bitmap = BitmapFactory.decodeStream(activity.contentResolver.openInputStream(Uri.parse(artUrl)))
                activity.runOnUiThread {
                    if (artUrl == view.getTag(R.id.image_view_album_art_url)) {
                        view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_BITMAP)
                        view.setImageBitmap(bitmap)
                    }
                }
            } catch (e: FileNotFoundException) {
                activity.runOnUiThread {
                    if (artUrl == view.getTag(R.id.image_view_album_art_url)) {
                        view.setTag(R.id.image_view_album_art_type, IMAGE_VIEW_ALBUM_ART_TYPE_TEXT)
                        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        view.setImageDrawable(createTextDrawable(text))
                    }
                }
            }
        }).start()
    }

    fun createTextBitmap(text: CharSequence?) =
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
        if (drawable is BitmapDrawable) {
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

    fun createIcon(bitmap: Bitmap): Bitmap {
        val scaleFactor = Math.min(MAX_ART_WIDTH_ICON.toDouble() / bitmap.width, MAX_ART_HEIGHT_ICON.toDouble() / bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scaleFactor).toInt(), (bitmap.height * scaleFactor).toInt(), false)
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
