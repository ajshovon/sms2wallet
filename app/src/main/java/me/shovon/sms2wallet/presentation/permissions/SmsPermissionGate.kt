package me.shovon.sms2wallet.presentation.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Gates the app on the SMS permissions it cannot work without, and kicks off the initial inbox
 * backfill the moment they are granted.
 *
 * Re-checks on every resume rather than only at first composition, so a permission granted (or
 * revoked) in system Settings while the app was backgrounded is picked up on return.
 */
@Composable
fun SmsPermissionGate(
    modifier: Modifier = Modifier,
    viewModel: SmsAccessViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(SmsPermissions.hasRequired(context)) }
    // Distinguishes "not asked yet" from "asked and refused": only the latter should send the
    // user to system Settings, since a first-time refusal can still be retried in-app.
    var hasAsked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasAsked = true
        hasPermission = SmsPermissions.hasRequired(context)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasPermission = SmsPermissions.hasRequired(context)
        }
    }

    // The backfill runs once per grant, not once per composition: keyed on the granted state so
    // recomposition cannot re-trigger a full inbox scan.
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.scanInbox()
    }

    if (hasPermission) {
        content()
        return
    }

    val activity = context as? Activity
    val permanentlyDenied = hasAsked && activity != null &&
        SmsPermissions.REQUIRED.none { activity.shouldShowRequestPermissionRationale(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Sms,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "SMS2Wallet needs to read your SMS",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Transaction messages are parsed on your device. Only the resulting " +
                "transaction is ever sent to Wallet - never the message itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (permanentlyDenied) {
            Text(
                text = "Android won't show the prompt again. Enable SMS access in system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { context.startActivity(appSettingsIntent(context.packageName)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open settings")
            }
        } else {
            Button(
                onClick = { launcher.launch(SmsPermissions.missing(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant SMS access")
            }
        }

        TextButton(onClick = { hasPermission = SmsPermissions.hasRequired(context) }) {
            Text("I've granted it - check again")
        }
    }
}

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
