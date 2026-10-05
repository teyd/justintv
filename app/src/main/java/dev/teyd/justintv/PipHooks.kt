package dev.teyd.justintv

import android.app.Activity

/** Set by the compose tree so the activity can enter PiP when the user leaves. */
object PipHooks {
    var onLeave: (() -> Unit)? = null
}
