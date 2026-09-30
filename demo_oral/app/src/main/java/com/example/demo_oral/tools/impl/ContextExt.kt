package com.example.demo_oral.tools.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts an activity; false if nothing on the phone can handle it or the system refuses. */
internal fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Log.w("Tools", "No activity for $intent", e)
    false
} catch (e: SecurityException) {
    Log.w("Tools", "Not allowed to start $intent", e)
    false
}
