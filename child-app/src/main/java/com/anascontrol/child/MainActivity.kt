
package com.anascontrol.child

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors

private const val SUPABASE_URL =
    "https://wnqwslwymzzpunfplolm.supabase.co"

private const val SUPABASE_KEY =
    "sb_publishable_mu0PfBf_CR2eStZvhQm4ng_5sOgrttC"

// Edge Function endpoint
private const val PAIRING_ENDPOINT =
    "$SUPABASE_URL/functions/v1/redeem-pairing"

private val httpClient = OkHttpClient()
private val executor = Executors.newSingleThreadExecutor()

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ChildHome(applicationContext)
            }
        }
    }
}

@Composable
fun ChildHome(context: Context) {

    val preferences = remember {
        context.getSharedPreferences(
            "anas_child_connection",
            Context.MODE_PRIVATE
        )
    }

    var deviceName by remember {
        mutableStateOf("My Child Device")
    }

    var pairingCode by remember {
        mutableStateOf("")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var connected by remember {
        mutableStateOf(
            preferences.getString("device_id", "").orEmpty().isNotBlank()
        )
    }

    var deviceId by remember {
        mutableStateOf(
            preferences.getString("device_id", "").orEmpty()
        )
    }

    var familyId by remember {
        mutableStateOf(
            preferences.getString("family_id", "").orEmpty()
        )
    }

    var savedDeviceName by remember {
        mutableStateOf(
            preferences.getString("device_name", "").orEmpty()
        )
    }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    fun connectDevice() {

        val code = pairingCode.trim().lowercase()
        val name = deviceName.trim()

        if (code.length != 12) {
            message = "Valid 12-character pairing code enter karo."
            return
        }

        if (name.length < 2 || name.length > 80) {
            message = "Device name 2 se 80 characters ka hona chahiye."
            return
        }

        loading = true
        message = ""

        executor.execute {
            try {
                val json = JSONObject().apply {
                    put("code", code)
                    put("device_name", name)
                }

                val body = json.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

                val request = Request.Builder()
                    .url(PAIRING_ENDPOINT)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    val responseText =
                        response.body?.string().orEmpty()

                    val result = try {
                        JSONObject(responseText)
                    } catch (_: Exception) {
                        JSONObject()
                    }

                    mainHandler.post {

                        loading = false

                        if (response.isSuccessful) {

                            val newDeviceId =
                                result.optString("device_id")

                            val newFamilyId =
                                result.optString("family_id")

                            val newDeviceName =
                                result.optString("device_name", name)

                            if (
                                newDeviceId.isNotBlank() &&
                                newFamilyId.isNotBlank()
                            ) {

                                deviceId = newDeviceId
                                familyId = newFamilyId
                                savedDeviceName = newDeviceName
                                connected = true

                                preferences.edit()
                                    .putString("device_id", newDeviceId)
                                    .putString("family_id", newFamilyId)
                                    .putString("device_name", newDeviceName)
                                    .apply()

                                pairingCode = ""
                                message = "Device successfully connected!"

                            } else {
                                message =
                                    "Server response incomplete. Please try again."
                            }

                        } else {
                            message = result.optString(
                                "error",
                                result.optString(
                                    "message",
                                    "Pairing failed: $responseText"
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                mainHandler.post {
                    loading = false
                    message =
                        "Connection error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                "ANAS CONTROL",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Child Device",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(28.dp))

            if (connected) {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {

                        Text(
                            "Device Connected",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(12.dp))

                        Text("Status: Connected")

                        Spacer(Modifier.height(8.dp))

                        Text("Device: $savedDeviceName")

                        Spacer(Modifier.height(8.dp))

                        Text("Device ID: $deviceId")

                        Spacer(Modifier.height(8.dp))

                        Text("Family ID: $familyId")
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "This device is paired with its parent account.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {
                        preferences.edit().clear().apply()

                        connected = false
                        deviceId = ""
                        familyId = ""
                        savedDeviceName = ""
                        message = "Local connection details cleared."
                    },
                    enabled = !loading
                ) {
                    Text("Disconnect This Device")
                }

            } else {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {

                        Text(
                            "Connect to Parent",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Parent app se mila pairing code yahan enter karo."
                        )

                        Spacer(Modifier.height(20.dp))

                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = {
                                deviceName = it.take(80)
                            },
                            label = {
                                Text("Device Name")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = {
                                pairingCode = it
                                    .filter { char ->
                                        char.isLetterOrDigit()
                                    }
                                    .take(12)
                                    .lowercase()
                            },
                            label = {
                                Text("12-character Pairing Code")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Ascii
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = { connectDevice() },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (loading)
                                    "Connecting..."
                                else
                                    "Connect Device"
                            )
                        }
                    }
                }
            }

            if (loading) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(20.dp))

                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Transparent parental controls • ANAS CONTROL",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
