package me.shovon.sms2wallet

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import me.shovon.sms2wallet.presentation.navigation.Sms2WalletRootScreen
import me.shovon.sms2wallet.presentation.permissions.SmsPermissionGate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import me.shovon.sms2wallet.data.notification.TransactionNotifier
import me.shovon.sms2wallet.domain.model.ThemeMode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.ThemeViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingReviewId = mutableStateOf<Long?>(null)

    /**
     * The activity is `singleTop`-ish via CLEAR_TOP, so a notification tapped while the app is
     * already running arrives here rather than through onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReviewExtra(intent)?.let { pendingReviewId.value = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingReviewId.value?.let { outState.putLong(KEY_PENDING_REVIEW_ID, it) }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedId = savedInstanceState?.getLong(KEY_PENDING_REVIEW_ID, -1L)?.takeIf { it > 0 }
        pendingReviewId.value = savedId ?: readReviewExtra(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        enableEdgeToEdge()
        setContent {
            val currentPendingReviewId by pendingReviewId

            val themeViewModel: ThemeViewModel = hiltViewModel()
            val appearance by themeViewModel.appearance.collectAsStateWithLifecycle()

            // Nothing is drawn until the stored preferences have been read, so a user on dark or
            // AMOLED never sees a white flash while DataStore loads.
            val settings = appearance ?: return@setContent

            val isDark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
            }
            val isAmoled = settings.themeMode == ThemeMode.AMOLED

            DisposableEffect(settings.themeMode, isDark) {
                val statusBarStyle = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                val navBarStyle = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }

                enableEdgeToEdge(
                    statusBarStyle = statusBarStyle,
                    navigationBarStyle = navBarStyle,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }

                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT

                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark

                val windowBgColor = when {
                    isAmoled -> android.graphics.Color.BLACK
                    isDark -> 0xFF191C1A.toInt()
                    else -> 0xFFFBFDF9.toInt()
                }
                window.decorView.setBackgroundColor(windowBgColor)

                onDispose {}
            }

            Sms2WalletTheme(
                themeMode = settings.themeMode,
                accentColor = settings.accentColor,
            ) {
                // Nothing below this gate can do anything useful without SMS access, and the
                // gate is what triggers the initial inbox backfill once it is granted.
                SmsPermissionGate {
                    Sms2WalletRootScreen(
                        openTransactionId = currentPendingReviewId,
                        onTransactionOpened = { pendingReviewId.value = null },
                    )
                }
            }
        }
    }

    private companion object {
        const val KEY_PENDING_REVIEW_ID = "pending_review_transaction_id"
    }

    /** Reads the transaction id a review notification was built with, or null. */
    private fun readReviewExtra(intent: Intent?): Long? =
        intent?.getLongExtra(TransactionNotifier.EXTRA_REVIEW_TRANSACTION_ID, -1L)
            ?.takeIf { it > 0 }
}
