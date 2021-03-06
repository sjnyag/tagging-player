package com.sjn.stamp.ui.fragment

import android.content.DialogInterface
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjn.stamp.R
import com.sjn.stamp.controller.SongController
import com.sjn.stamp.model.Song
import com.sjn.stamp.model.dao.SongDao
import com.sjn.stamp.ui.DialogFacade
import com.sjn.stamp.ui.SongAdapter
import com.sjn.stamp.ui.SongSelectDialog
import com.sjn.stamp.ui.fragment.media.ListFragment
import com.sjn.stamp.ui.item.AbstractItem
import com.sjn.stamp.ui.item.SimpleMediaMetadataItem
import com.sjn.stamp.ui.item.UnknownSongItem
import com.sjn.stamp.ui.observer.StampEditStateObserver
import com.sjn.stamp.utils.MediaItemHelper
import com.sjn.stamp.utils.MediaRetrieveHelper
import com.sjn.stamp.utils.RealmHelper
import eu.davidea.fastscroller.FastScroller
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.helpers.UndoHelper
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem

class UnknownSongListFragment : ListFragment(), UndoHelper.OnUndoListener, FlexibleAdapter.OnItemSwipeListener {

    var dialog: SongSelectDialog? = null

    /**
     * [ListFragment]
     */
    override val menuResourceId: Int
        get() = R.menu.song_list

    override fun onRefresh() {
        activity?.let {
            DialogFacade.createConfirmDialog(it, R.string.dialog_confirm_song_db_refresh,
                    { _, _ -> CreateUnknownSongListAsyncTask(this).execute() },
                    { _, _ -> swipeRefreshLayout?.let { it.isRefreshing = false } },
                    DialogInterface.OnDismissListener { }).show()
        }
    }

    override fun onSelectedStampChange(selectedStampList: List<String>) {}

    override fun onNewStampCreated(stamp: String) {}

    override fun onStampStateChange(state: StampEditStateObserver.State) {}

    override fun onFastScrollerStateChange(scrolling: Boolean) {}

    override fun noMoreLoad(newItemsSize: Int) {}

    override fun onLoadMore(lastPosition: Int, currentPage: Int) {}

    override fun onItemClick(position: Int): Boolean {
        openSongSelectDialog(position)
        return false
    }

    override fun onItemLongClick(position: Int) {}

    override fun onUpdateEmptyView(size: Int) {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_list, container, false)
        if (savedInstanceState != null) {
            listState = savedInstanceState.getParcelable(ListFragment.LIST_STATE_KEY)
        }
        loading = rootView.findViewById(R.id.progressBar)
        emptyView = rootView.findViewById(R.id.empty_view)
        fastScroller = rootView.findViewById(R.id.fast_scroller)
        emptyTextView = rootView.findViewById(R.id.empty_text)
        swipeRefreshLayout = rootView.findViewById(R.id.refresh)
        recyclerView = rootView.findViewById(R.id.recycler_view)

        swipeRefreshLayout?.apply {
            setOnRefreshListener(this@UnknownSongListFragment)
            setColorSchemeColors(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        }
        currentItems = createItemList()
        adapter = SongAdapter(currentItems, this).apply {
            setNotifyChangeOfUnfilteredItems(true)
            setAnimationOnScrolling(false)
        }
        recyclerView?.apply {
            activity?.let {
                this.layoutManager = SmoothScrollLinearLayoutManager(it).apply {
                    listState?.let { onRestoreInstanceState(it) }
                }
            }
            this.adapter = this@UnknownSongListFragment.adapter
        }
        adapter?.apply {
            fastScroller = rootView.findViewById<View>(R.id.fast_scroller) as FastScroller
            isLongPressDragEnabled = false
            isHandleDragEnabled = false
            isSwipeEnabled = true
            setUnlinkAllItemsOnRemoveHeaders(false)
            setDisplayHeadersAtStartUp(false)
            setStickyHeaders(false)
            showAllHeaders()
        }
        notifyFragmentChange()
        loading?.let {
            it.visibility = View.GONE
        }
        return rootView
    }

    override fun onItemSwipe(position: Int, direction: Int) {
        if (adapter?.getItem(position) !is UnknownSongItem) return
        val item = adapter?.getItem(position) as UnknownSongItem
        activity?.run {
            tryRemove(item, position)
        }
    }

    private fun tryRemove(item: AbstractItem<*>, position: Int) {
        val positions = mutableListOf(position)
        val message = StringBuilder().append(item.title).append(" ").append(getString(R.string.action_deleted))
        if (item.isSelectable) adapter?.setRestoreSelectionOnUndo(false)
        adapter?.isPermanentDelete = false
        swipeRefreshLayout?.isRefreshing = true
        activity?.let {
            UndoHelper(adapter, this@UnknownSongListFragment)
                    .withPayload(null)
                    .withConsecutive(true)
                    .start(positions, it.findViewById(R.id.main_view), message, getString(R.string.undo), UndoHelper.UNDO_TIMEOUT)
        }
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        swipeRefreshLayout?.isEnabled = actionState == ItemTouchHelper.ACTION_STATE_IDLE
    }

    override fun onActionCanceled(action: Int) {
        adapter?.restoreDeletedItems()
        swipeRefreshLayout?.isRefreshing = false
        if (adapter?.isRestoreWithSelection == true) listener?.restoreSelection()
    }

    override fun onActionConfirmed(action: Int, event: Int) {
        swipeRefreshLayout?.isRefreshing = false
        for (adapterItem in adapter?.deletedItems ?: emptyList()) {
            try {
                when (adapterItem.layoutRes) {
                    R.layout.recycler_unknown_song_item -> {
                        val item = adapterItem as UnknownSongItem
                        activity?.let {
                            SongController(it).delete(item.song)
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    private fun openSongSelectDialog(position: Int) {
        val unknownSongItem = adapter?.getItem(position) as? UnknownSongItem ?: return
        context?.let {
            dialog = SongSelectDialog(it).apply {
                setOnSongSelectedListener(object : SongSelectDialog.OnSongSelectedListener {
                    override fun onSongSelected(item: SimpleMediaMetadataItem) {
                        context?.let {
                            SongController(it).resolveMediaMetadata(item.mediaId)?.let {
                                openMergeConfirmDialog(unknownSongItem.song, it, position)
                            }
                        }
                    }

                })
            }
        }
        dialog?.show()
    }

    private fun openMergeConfirmDialog(unknownSong: Song, mediaMetadata: MediaMetadataCompat, position: Int) {
        activity?.let {
            DialogFacade.createConfirmDialog(it, resources.getString(R.string.dialog_confirm_merge_song, unknownSong.title, MediaItemHelper.getTitle(mediaMetadata))
            ) { _, _ ->
                activity?.let {
                    if (SongController(it).mergeSong(unknownSong, mediaMetadata)) {
                        it.runOnUiThread {
                            adapter?.removeItem(position)
                            adapter?.notifyItemRemoved(position)
                            dialog?.dismiss()
                        }
                    }
                }
            }.show()
        }
    }

    @Synchronized
    private fun createItemList(): List<AbstractFlexibleItem<*>> =
            SongDao.findUnknown(RealmHelper.realmInstance).mapIndexedTo(ArrayList()) { id, song -> UnknownSongItem(id.toString(), song) }

    private class CreateUnknownSongListAsyncTask constructor(val fragment: UnknownSongListFragment) : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg params: Void): Void? {
            fragment.context?.let {
                SongController(it).refreshAllSongs(MediaRetrieveHelper.allMediaMetadataCompat(it, null))
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            if (!fragment.isAdded) {
                return
            }
            fragment.currentItems = fragment.createItemList()
            fragment.activity?.runOnUiThread {
                fragment.adapter?.updateDataSet(fragment.currentItems)
                fragment.swipeRefreshLayout?.let {
                    it.isRefreshing = false
                }
            }
        }

    }
}
