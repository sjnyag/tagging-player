package com.sjn.stamp.ui.item.holder

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.transition.TransitionInflater
import android.support.v4.app.Fragment
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.sjn.stamp.R
import com.sjn.stamp.ui.fragment.DetailFragment
import com.sjn.stamp.utils.CompatibleHelper
import com.sjn.stamp.utils.SongStateHelper
import com.sjn.stamp.utils.ViewHelper
import eu.davidea.flexibleadapter.FlexibleAdapter
import java.util.ArrayList

class SongViewHolder constructor(view: View, adapter: FlexibleAdapter<*>, activity: Activity) : StampContainsViewHolder(view, adapter, activity) {

    internal var mediaId: String? = null
    internal var albumArtView: ImageView = view.findViewById(R.id.image)
    internal var title: TextView = view.findViewById(R.id.title)
    internal var subtitle: TextView = view.findViewById(R.id.subtitle)
    internal var date: TextView = view.findViewById(R.id.date)
    internal var imageView: ImageView = view.findViewById(R.id.play_eq)
    private var _frontView: View = view.findViewById(R.id.front_view)

    init {
        this.imageView.setOnClickListener {
            mAdapter.mItemLongClickListener?.onItemLongClick(adapterPosition)
        }
    }

    fun createNextFragment(context: Context): Pair<Fragment, ArrayList<Pair<String, View>>> {
        var imageTransitionName = ""
        var textTransitionName = ""
        return Pair(DetailFragment().apply {
            if (CompatibleHelper.hasLollipop()) {
                sharedElementReturnTransition = TransitionInflater.from(context).inflateTransition(R.transition.change_image_trans)
                exitTransition = TransitionInflater.from(context).inflateTransition(android.R.transition.fade)
                sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(R.transition.change_image_trans)
                enterTransition = TransitionInflater.from(context).inflateTransition(android.R.transition.fade)
                imageTransitionName = albumArtView.transitionName
                textTransitionName = title.transitionName
            }
            arguments = Bundle().apply {
                putString("TRANS_TITLE", textTransitionName)
                putString("TITLE", title.text.toString())
                putString("TRANS_IMAGE", imageTransitionName)
                putParcelable("IMAGE", ViewHelper.toBitmap(albumArtView.drawable))
            }
        }, ArrayList<Pair<String, View>>().apply {
            add(Pair(imageTransitionName, albumArtView))
            add(Pair(textTransitionName, title))
        })
    }

    override fun getActivationElevation(): Float = ViewHelper.dpToPx(itemView.context, 4f)

    override fun getFrontView(): View = _frontView

    fun update(view: View, mediaId: String, isPlayable: Boolean) {
        val cachedState = view.getTag(R.id.tag_mediaitem_state_cache) as Int?
        val state = SongStateHelper.getMediaItemState(this.activity, mediaId, isPlayable)
        if (cachedState == null || cachedState != state) {
            val drawable = SongStateHelper.getDrawableByState(this.activity, state)
            if (drawable != null) {
                this.imageView.setImageDrawable(drawable)
                this.imageView.visibility = View.VISIBLE
            } else {
                this.imageView.visibility = View.GONE
            }
            view.setTag(R.id.tag_mediaitem_state_cache, state)
        }
    }
}