package me.shovon.sms2wallet.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions this app actually has to ask for, and how to check them.
 *
 * Declaring a dangerous permission in the manifest grants nothing on API 23+; without an
 * explicit request `SmsBroadcastReceiver` never fires and `SmsInboxReader` reads an empty
 * cursor. [REQUIRED] is what gates the app's core function; [OPTIONAL] only degrades it.
 */
object SmsPermissions {

    /**
     * Without these the app cannot do its one job. `READ_SMS` backs the inbox backfill scan and
     * `RECEIVE_SMS` backs live delivery - both are needed, since one covers history and the
     * other covers everything arriving from now on.
     */
    val REQUIRED: List<String> = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
    )

    /**
     * Notifications are a genuine extra rather than part of the core loop, and only exist as a
     * runtime permission from API 33 (Tiramisu). Asking for it on older devices throws.
     */
    val OPTIONAL: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** True once every [REQUIRED] permission is granted, i.e. SMS ingest can actually run. */
    fun hasRequired(context: Context): Boolean = REQUIRED.all { isGranted(context, it) }

    /** [REQUIRED] + [OPTIONAL] minus whatever is already granted - what to hand the launcher. */
    fun missing(context: Context): Array<String> =
        (REQUIRED + OPTIONAL).filterNot { isGranted(context, it) }.toTypedArray()
}
