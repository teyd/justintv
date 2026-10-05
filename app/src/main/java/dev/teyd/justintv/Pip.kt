package dev.teyd.justintv

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.core.content.ContextCompat

/**
 * System picture-in-picture, the window Android draws over other apps.
 *
 * This is separate from the in-app mini player. Flow does the same split: a collapsed player
 * inside the app, and PiP only when you leave.
 */
object Pip {
    const val ACTION_PLAY = "dev.teyd.justintv.PIP_PLAY"
    const val ACTION_PAUSE = "dev.teyd.justintv.PIP_PAUSE"

    fun supported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun params(context: Context, isPlaying: Boolean, autoEnter: Boolean): PictureInPictureParams {
        val playPause = remoteAction(
            context = context,
            icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = if (isPlaying) "Pause" else "Play",
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY,
            requestCode = 1,
        )
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(playPause))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun enter(activity: Activity, isPlaying: Boolean): Boolean {
        if (!supported(activity)) return false
        return try {
            activity.enterPictureInPictureMode(params(activity, isPlaying, autoEnter = true))
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun receiver(onPlay: () -> Unit, onPause: () -> Unit): BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PLAY -> onPlay()
                    ACTION_PAUSE -> onPause()
                }
            }
        }

    fun filter(): IntentFilter = IntentFilter().apply {
        addAction(ACTION_PLAY)
        addAction(ACTION_PAUSE)
    }

    fun register(context: Context, receiver: BroadcastReceiver) {
        ContextCompat.registerReceiver(context, receiver, filter(), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun remoteAction(
        context: Context,
        icon: Int,
        title: String,
        action: String,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(action).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(context, icon), title, title, pending)
    }
}
