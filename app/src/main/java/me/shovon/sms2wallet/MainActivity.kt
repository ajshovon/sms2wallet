package me.shovon.sms2wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import me.shovon.bdparser.bank.BankParserFactory

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ParserSmokeScreen()
            }
        }
    }
}

/**
 * Temporary scaffold screen: lists the parsers resolved from the :bd-sms-parsers
 * submodule, proving the module wiring works. Replaced by the real navigation graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParserSmokeScreen() {
    val parsers = BankParserFactory.getAllParsers()
    Scaffold(
        topBar = { TopAppBar(title = { Text("SMS2Wallet") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(parsers) { parser ->
                ListItem(
                    headlineContent = { Text(parser.getBankName()) },
                    supportingContent = { Text(parser.getCurrency()) }
                )
            }
        }
    }
}
