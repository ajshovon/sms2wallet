package me.shovon.sms2wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import me.shovon.sms2wallet.presentation.navigation.Sms2WalletRootScreen
import me.shovon.sms2wallet.presentation.permissions.SmsPermissionGate
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.ThemeViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val appearance by themeViewModel.appearance.collectAsStateWithLifecycle()

            // Nothing is drawn until the stored preferences have been read, so a user on dark or
            // AMOLED never sees a white flash while DataStore loads.
            val settings = appearance ?: return@setContent

            Sms2WalletTheme(
                themeMode = settings.themeMode,
                accentColor = settings.accentColor,
            ) {
                // Nothing below this gate can do anything useful without SMS access, and the
                // gate is what triggers the initial inbox backfill once it is granted.
                SmsPermissionGate {
                    Sms2WalletRootScreen()
                }
            }
        }
    }
}
