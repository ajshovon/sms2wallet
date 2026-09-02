package me.shovon.sms2wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import me.shovon.sms2wallet.presentation.navigation.Sms2WalletRootScreen
import me.shovon.sms2wallet.presentation.permissions.SmsPermissionGate
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Sms2WalletTheme {
                // Nothing below this gate can do anything useful without SMS access, and the
                // gate is what triggers the initial inbox backfill once it is granted.
                SmsPermissionGate {
                    Sms2WalletRootScreen()
                }
            }
        }
    }
}
