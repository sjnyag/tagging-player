package com.sjn.stamp.media.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.provider.MediaStore
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.sjn.stamp.R
import com.sjn.stamp.media.StampSession.Companion.EXTRA_CONNECTED_CAST
import com.sjn.stamp.utils.AlbumArtHelper
import com.sjn.stamp.utils.LogHelper
import com.sjn.stamp.utils.NotificationHelper
import com.sjn.stamp.utils.ViewHelper
import java.io.FileNotFoundException

class NotificationContainer(
        private val context: Context,
        private val sessionToken: MediaSessionCompat.Token,
        private val controller: MediaControllerCompat
) {
    companion object {
        private val TAG = LogHelper.makeLogTag(NotificationContainer::class.java)
        const val NOTIFICATION_ID = 412
        const val CHANNEL_ID = "stamp_channel_01"
        const val CHANNEL_NAME = "Controller"
    }

    var notification: Notification? = null
    private var previousState: PlaybackStateCompat? = null
    private val notificationColor: Int = ViewHelper.getThemeColor(context, R.attr.colorPrimary, Color.DKGRAY)
    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)

    init {
        LogHelper.d(TAG, "init")
        NotificationHelper.createChannel(context, CHANNEL_ID, CHANNEL_NAME)
        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        notificationManager.cancelAll()
    }

    fun create(metadata: MediaMetadataCompat?, playbackState: PlaybackStateCompat?) {
        LogHelper.d(TAG, "create")
        if (previousState == playbackState) {
            LogHelper.d(TAG, "create skip")
            return
        }
        previousState = playbackState
        notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .apply {
                    addPreviousAction()
                    addPlayPauseAction(playbackState)
                    addNextAction()
                    setupStyle(metadata)
                    addCastAction()
                    setNotificationPlaybackState(playbackState)
                    setLargeIcon(try {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, metadata?.description?.iconUri)
                    } catch (e: FileNotFoundException) {
                        AlbumArtHelper.createTextBitmap(metadata?.description?.title?.toString())
                    })
                }.build()
    }

    fun start() {
        LogHelper.d(TAG, "start")
        notification?.let { notificationManager.notify(NOTIFICATION_ID, it) }
    }

    fun cancel() {
        LogHelper.d(TAG, "cancel")
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun NotificationCompat.Builder.addPreviousAction() =
            addAction(R.drawable.ic_skip_previous_white_24dp,
                    context.getString(R.string.label_previous), NotificationAction.PREV.createIntent(context))

    private fun NotificationCompat.Builder.addNextAction() =
            addAction(R.drawable.ic_skip_next_white_24dp,
                    context.getString(R.string.label_next), NotificationAction.NEXT.createIntent(context))

    private fun NotificationCompat.Builder.setupStyle(metadata: MediaMetadataCompat?) {
        setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)  // show only play/pause in compact view
                .setMediaSession(sessionToken))
        color = notificationColor
        setSmallIcon(R.mipmap.ic_notification)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        setUsesChronometer(true)
        setDeleteIntent(NotificationAction.killIntent(context))
        setContentIntent(NotificationAction.contentIntent(context, metadata?.description))
        setContentTitle(metadata?.description?.title)
        setContentText(metadata?.description?.subtitle)
    }

    private fun NotificationCompat.Builder.addCastAction() =
            controller.extras?.let {
                it.getString(EXTRA_CONNECTED_CAST)?.let {
                    setSubText(context.resources.getString(R.string.casting_to_device, it))
                    addAction(R.drawable.ic_close_black_24dp, context.getString(R.string.stop_casting), NotificationAction.STOP_CAST.createIntent(context))
                }
            }

    private fun NotificationCompat.Builder.addPlayPauseAction(playbackState: PlaybackStateCompat?) {
        val label: String
        val icon: Int
        val intent: PendingIntent?
        if (playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            label = context.getString(R.string.label_pause)
            icon = R.drawable.stamp_ic_pause_white_24dp
            intent = NotificationAction.PAUSE.createIntent(context)
        } else {
            label = context.getString(R.string.label_play)
            icon = R.drawable.stamp_ic_play_arrow_white_24dp
            intent = NotificationAction.PLAY.createIntent(context)
        }
        addAction(NotificationCompat.Action(icon, label, intent))
    }

    private fun NotificationCompat.Builder.setNotificationPlaybackState(playbackState: PlaybackStateCompat?) {
        if (playbackState?.state == PlaybackStateCompat.STATE_PLAYING && playbackState.position >= 0) {
            setWhen(System.currentTimeMillis() - playbackState.position)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
        } else {
            setWhen(0)
                    .setShowWhen(false)
                    .setUsesChronometer(false)
        }
        // Make sure that the notification can be dismissed by the user when we are not playing:
        setOngoing(playbackState?.state == PlaybackStateCompat.STATE_PLAYING)
    }

    private fun MediaMetadataCompat.getTextDrawableBitmap() =
            AlbumArtHelper.createTextBitmap(description.title)
}