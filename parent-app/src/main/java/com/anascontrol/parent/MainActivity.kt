package com.anascontrol.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ParentHome() } }
    }
}

@Composable
fun ParentHome() {
    var status by remember { mutableStateOf("Not connected") }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ANAS CONTROL", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Parent app • Starter build", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Card {
                Column(Modifier.padding(20.dp)) {
                    Text("Device connection", style = MaterialTheme.typography.titleLarge)
                    Text(status)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { status = "Backend setup pending" }) {
                Text("Check setup")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "This starter does not collect personal data or run hidden monitoring. " +
                "Pairing, permissions and parental policies will be added in later tested phases.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
