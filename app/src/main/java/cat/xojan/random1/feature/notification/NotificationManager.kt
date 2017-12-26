package cat.xojan.random1.feature.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.RemoteException
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import cat.xojan.random1.R
import cat.xojan.random1.feature.mediaplayback.MediaPlaybackFullScreenActivity
import cat.xojan.random1.feature.mediaplayback.MediaPlaybackService






class NotificationManager(private val service: MediaPlaybackService): BroadcastReceiver() {

    private val NOTIFICATION_ID = 683
    private val REQUEST_CODE = 100
    private val CHANNEL_ID = "cat.xojan.random1.feature.notification.MUSIC_CHANNEL_ID"

    private val ACTION_PAUSE = "cat.xojan.random1.feature.notification.pause"
    private val ACTION_PLAY = "cat.xojan.random1.feature.notification.play"
    private val ACTION_PREV = "cat.xojan.random1.feature.notification.prev"
    private val ACTION_NEXT = "cat.xojan.random1.feature.notification.next"
    private val ACTION_STOP = "cat.xojan.random1.feature.notification.stop"

    private val TAG = NotificationManager::class.simpleName

    private val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
    private val previousIntent:PendingIntent
    private val nextIntent:PendingIntent
    private val pauseIntent:PendingIntent
    private val playIntent:PendingIntent
    private val stopIntent:PendingIntent

    private var sessionToken: MediaSessionCompat.Token? = null
    private var mController: MediaControllerCompat? = null
    private var mTransportControls: MediaControllerCompat.TransportControls? = null
    private var mStarted = false

    private var mPlaybackState: PlaybackStateCompat? = null
    private var mMetadata: MediaMetadataCompat? = null

    init {
        updateMediaSessionToken()
        val pkg = service.packageName
        previousIntent = PendingIntent.getBroadcast(service, REQUEST_CODE,
                Intent(ACTION_PREV).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        nextIntent = PendingIntent.getBroadcast(service, REQUEST_CODE,
                Intent(ACTION_NEXT).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        pauseIntent = PendingIntent.getBroadcast(service, REQUEST_CODE,
                Intent(ACTION_PAUSE).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        playIntent = PendingIntent.getBroadcast(service, REQUEST_CODE,
                Intent(ACTION_PLAY).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        stopIntent = PendingIntent.getBroadcast(service, REQUEST_CODE,
                Intent(ACTION_STOP).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        notificationManager.cancelAll()
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see {@link android.media.session.MediaController.Callback#onSessionDestroyed()})
     */
    private fun updateMediaSessionToken() {
        val freshToken = service.sessionToken
        if (sessionToken == null && freshToken != null || sessionToken != null
                && sessionToken != freshToken) {
            if (mController != null) {
                mController!!.unregisterCallback(mCb)
            }
            sessionToken = freshToken
            if (sessionToken != null) {
                mController = MediaControllerCompat(service, sessionToken!!)
                mTransportControls = mController!!.getTransportControls()
                if (mStarted) {
                    mController!!.registerCallback(mCb)
                }
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "Received intent with action " + action!!)
        when (action) {
            ACTION_PAUSE -> mTransportControls?.pause()
            ACTION_PLAY -> mTransportControls?.play()
            ACTION_NEXT -> mTransportControls?.skipToNext()
            ACTION_PREV -> mTransportControls?.skipToPrevious()
            else -> Log.w(TAG, "Unknown intent ignored. Action= " + action)
        }
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before [.stopNotification] is called.
     */
    fun startNotification() {
        if (!mStarted) {
            mMetadata = mController?.metadata
            mPlaybackState = mController?.playbackState

            val notification = createNotification()

            val filter = IntentFilter()
            filter.addAction(ACTION_NEXT)
            filter.addAction(ACTION_PAUSE)
            filter.addAction(ACTION_PLAY)
            filter.addAction(ACTION_PREV)

            service.registerReceiver(this, filter)
            service.startForeground(NOTIFICATION_ID, notification)

            mStarted = true
        }
    }

    private fun createNotification(): Notification? {
        Log.d(TAG, "createNotification")
        if (mMetadata == null || mPlaybackState == null) {
            return null
        }

        val description = mMetadata?.description

        // Notification channels are only supported on Android O+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val notificationBuilder = NotificationCompat.Builder(service, CHANNEL_ID)

        val playPauseButtonPosition = addActions(notificationBuilder, mPlaybackState)
        notificationBuilder
                .setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle()
                        // show only play/pause in compact view
                        .setShowActionsInCompactView(playPauseButtonPosition)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopIntent)
                        .setMediaSession(sessionToken))
                .setDeleteIntent(stopIntent)
                // TODO? .setColor(mNotificationColor)
                .setSmallIcon(R.mipmap.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setContentIntent(createContentIntent(description))
                .setContentTitle(service.getString(R.string.app_name))
                .setContentText(description?.title)
                .setLargeIcon(BitmapFactory.decodeResource(service.resources,
                        R.drawable.default_rac1))

        setNotificationPlaybackState(notificationBuilder, mPlaybackState)
        //TODO ? fetchBitmapFromURLAsync(fetchArtUrl, notificationBuilder)

        return notificationBuilder.build()
    }

    /**
     * Creates Notification Channel. This is required in Android O+ to display notifications.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val notificationChannel = NotificationChannel(CHANNEL_ID,
                    service.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW)

            notificationChannel.description = service.getString(R.string.notification_channel_description)

            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    private fun addActions(notificationBuilder: NotificationCompat.Builder,
                           playbackState: PlaybackStateCompat?): Int {
        Log.d(TAG, "updatePlayPauseAction")

        var playPauseButtonPosition = 0
        // If skip to previous action is enabled
        if (playbackState?.actions != null && PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L) {
            notificationBuilder.addAction(android.R.drawable.ic_media_previous,
                    service.getString(R.string.label_previous), previousIntent)

            // If there is a "skip to previous" button, the play/pause button will
            // be the second one. We need to keep track of it, because the MediaStyle notification
            // requires to specify the index of the buttons (actions) that should be visible
            // when in compact view.
            playPauseButtonPosition = 1
        }

        // Play or pause button, depending on the current state.
        val label: String
        val icon: Int
        val intent: PendingIntent
        if (playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            label = service.getString(R.string.label_pause)
            icon = android.R.drawable.ic_media_pause
            intent = pauseIntent
        } else {
            label = service.getString(R.string.label_play)
            icon = android.R.drawable.ic_media_play
            intent = playIntent
        }
        notificationBuilder.addAction(NotificationCompat.Action(icon, label, intent))

        // If skip to next action is enabled
        if (playbackState?.actions != null && PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L) {
            notificationBuilder.addAction(android.R.drawable.ic_media_next,
                    service.getString(R.string.label_next), nextIntent)
        }

        return playPauseButtonPosition
    }

    private fun createContentIntent(description: MediaDescriptionCompat?): PendingIntent {
        val openUI = Intent(service, MediaPlaybackFullScreenActivity::class.java)
        openUI.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        openUI.putExtra(MediaPlaybackFullScreenActivity.EXTRA_START_FULLSCREEN, true)
        if (description != null) {
            openUI.putExtra(MediaPlaybackFullScreenActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION, description)
        }
        return PendingIntent.getActivity(service, REQUEST_CODE, openUI,
                PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun setNotificationPlaybackState(builder: NotificationCompat.Builder,
                                             playbackState: PlaybackStateCompat?) {
        Log.d(TAG, "updateNotificationPlaybackState. mPlaybackState=" + playbackState)

        if (playbackState == null || !mStarted) {
            Log.d(TAG, "updateNotificationPlaybackState. cancelling notification!")
            service.stopForeground(true)
            return
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(playbackState.state == PlaybackStateCompat.STATE_PLAYING)
    }

    fun stopNotification() {
        if (mStarted) {
            mStarted = false
            try {
                notificationManager.cancel(NOTIFICATION_ID)
                service.unregisterReceiver(this)
            } catch (ex: IllegalArgumentException) {
                // ignore if the receiver is not registered.
            }

            service.stopForeground(true)
        }
    }

    private val mCb = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            mPlaybackState = state
            Log.d(TAG, "Received new playback state: " + state)
            if (state!!.state == PlaybackStateCompat.STATE_STOPPED ||
                    state.state == PlaybackStateCompat.STATE_NONE) {
                stopNotification()
            } else {
                val notification = createNotification()
                if (notification != null) {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            mMetadata = metadata
            Log.d(TAG, "Received new metadata: " + metadata)
            val notification = createNotification()
            if (notification != null) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            Log.d(TAG, "Session was destroyed, resetting to the new session token")
            try {
                updateMediaSessionToken()
            } catch (e: RemoteException) {
                Log.e(TAG, "could not connect media controller ")
                e.printStackTrace()
            }

        }
    }
}