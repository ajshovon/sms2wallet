package me.shovon.sms2wallet.presentation.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import me.shovon.sms2wallet.presentation.theme.IconSize
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * Gates the app on SMS permissions with privacy reassurance cards, and kicks off
 * the initial inbox scan the moment permissions are granted.
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xxl, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SolarIcons.ShieldCheck,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.xl),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "SMS Access Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "SMS2Wallet parses bank & mobile wallet SMS into transactions automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Privacy Assurance Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    PrivacyBullet(
                        icon = SolarIcons.CheckCircle,
                        title = "100% On-Device Parsing",
                        description = "Your SMS messages are processed locally and never uploaded to any server."
                    )
                    PrivacyBullet(
                        icon = SolarIcons.CheckCircle,
                        title = "You Review Everything",
                        description = "Transactions sit in your review queue until you confirm and push them."
                    )
                    PrivacyBullet(
                        icon = SolarIcons.CheckCircle,
                        title = "Supported Bangladeshi Banks",
                        description = "bKash, Nagad, Rocket, Upay, Tap, City Bank, BRAC, EBL, MTB."
                    )
                }
            }

            if (permanentlyDenied) {
                Text(
                    text = "Android won't show the permission prompt again. Please enable SMS access in system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { context.startActivity(appSettingsIntent(context.packageName)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open System Settings")
                }
            } else {
                Button(
                    onClick = { launcher.launch(SmsPermissions.missing(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant SMS Access")
                }
            }

            TextButton(onClick = { hasPermission = SmsPermissions.hasRequired(context) }) {
                Text("I've granted it — check again")
            }
        }
    }
}

@Composable
private fun PrivacyBullet(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Sms2WalletTheme.extendedColors.income,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(IconSize.md)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
