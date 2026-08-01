//package com.example.musicplayer.data
//
//import android.annotation.SuppressLint
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.content.Intent
//import android.os.Build
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.core.app.NotificationCompat
//import androidx.media3.common.util.UnstableApi
//import androidx.media3.exoplayer.ExoPlayer
//import androidx.media3.session.MediaSession
//import androidx.media3.session.MediaSessionService
//import com.example.musicplayer.R
//import com.example.musicplayer.core.MainActivity
//
//@ExperimentalMaterial3Api
//@UnstableApi
//class PlaybackService : MediaSessionService() {
//
//    private var mediaSession: MediaSession? = null
//    private var player: ExoPlayer? = null
//
//    companion object {
//        private const val CHANNEL_ID = "music_playback_channel"
//        private const val NOTIFICATION_ID = 1001
//    }
//
//    @SuppressLint("ObsoleteSdkInt")
//    override fun onCreate() {
//        super.onCreate()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                "Play Music",
//                NotificationManager.IMPORTANCE_LOW
//            ).apply {
//                description = "Show Playing State"
//                setSound(null, null)
//            }
//            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
//        }
//
//        val intent = Intent(this, MainActivity::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
//        }
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            0,
//            intent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
//
//        player = ExoPlayer.Builder(this).build()
//        mediaSession = MediaSession.Builder(this, player!!)
//            .setSessionActivity(
//                pendingIntent
//            )
//            .build()
//        startForeground(NOTIFICATION_ID, createInitialNotification())
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        super.onStartCommand(intent, flags, startId)
//        return START_STICKY
//    }
//
//    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
//        return mediaSession
//    }
//
//    override fun onDestroy() {
//        mediaSession?.run {
//            player.release()
//            release()
//        }
//        super.onDestroy()
//    }
//
//    private fun createInitialNotification(): Notification {
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            0,
//            Intent(this, MainActivity::class.java),
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
//
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("MusicPlayer")
//            .setSmallIcon(R.drawable.app_icon)
//            .setContentIntent(pendingIntent)
//            .setPriority(NotificationCompat.PRIORITY_LOW)
//            .setCategory(NotificationCompat.CATEGORY_SERVICE)
//            .build()
//    }
//}

package com.example.musicplayer.data

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.core.MainActivity

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        /*
         * startForeground را اینجا اجرا نکن.
         * MediaSessionService خودش نوتیفیکیشن پخش را مدیریت می‌کند.
         */
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return

        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }

        mediaSession = null
        super.onDestroy()
    }
}