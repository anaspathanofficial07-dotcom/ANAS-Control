
package com.anascontrol.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

private const val SUPABASE_URL =
    "https://wnqwslwymzzpunfplolm.supabase.co"

// Apni Supabase Publishable Key yahan paste karo.
// Secret / Service Role Key kabhi mat lagana.
private const val SUPABASE_KEY =
    "PASTE_YOUR_PUBLISHABLE_KEY_HERE"

private val httpClient = OkHttpClient()
private val executor = Executors.newSingleThreadExecutor()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ParentAuthScreen()
            }
        }
    }
}

@Composable
fun ParentAuthScreen() {

    var isSignup by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var accessToken by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }

    val mainHandler = remember {
        android.os.Handler(android.os.Looper.getMainLooper())
    }

    fun authenticate(signup: Boolean) {

        if (email.isBlank() || password.length < 8) {
            message = "Valid email aur minimum 8-character password dalo."
            return
        }

        if (SUPABASE_KEY == "PASTE_YOUR_PUBLISHABLE_KEY_HERE") {
            message = "Pehle Supabase Publishable Key add karo."
            return
        }

        loading = true
        message = ""

        val currentEmail = email.trim()
        val currentPassword = password

        executor.execute {
            try {
                val endpoint = if (signup) {
                    "$SUPABASE_URL/auth/v1/signup"
                } else {
                    "$SUPABASE_URL/auth/v1/token?grant_type=password"
                }

                val json = JSONObject().apply {
                    put("email", currentEmail)
                    put("password", currentPassword)
                }

                val body = json.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    val responseText = response.body?.string().orEmpty()

                    val result = try {
                        JSONObject(responseText)
                    } catch (_: Exception) {
                        JSONObject()
                    }

                    mainHandler.post {
                        loading = false

                        if (response.isSuccessful) {

                            val token = result.optString("access_token")

                            if (token.isNotBlank()) {
                                accessToken = token
                                userEmail = currentEmail
                                loggedIn = true
                                message = "Login successful!"
                            } else {
                                message =
                                    "Signup request successful. Apna email inbox check karo aur verification link open karo."
                            }

                        } else {
                            message = result.optString(
                                "msg",
                                result.optString(
                                    "message",
                                    result.optString(
                                        "error_description",
                                        result.optString(
                                            "error",
                                            "Request failed. Please try again."
                                        )
                                    )
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                mainHandler.post {
                    loading = false
                    message = "Connection error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    fun logout() {
        val token = accessToken

        if (token.isBlank()) {
            loggedIn = false
            return
        }

        loading = true

        executor.execute {
            try {
                val request = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/logout")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .post("".toRequestBody(null))
                    .build()

                httpClient.newCall(request).execute().use {
                    // Clear local session after logout request.
                }

            } catch (_: Exception) {
                // Local logout is still allowed if offline.
            }

            mainHandler.post {
                accessToken = ""
                loggedIn = false
                userEmail = ""
                password = ""
                message = "Logged out."
                loading = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        if (loggedIn) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    "ANAS CONTROL",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(Modifier.height(16.dp))

                Text("Welcome, $userEmail")

                Spacer(Modifier.height(12.dp))

                Text("Parent account connected to Supabase.")

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { logout() },
                    enabled = !loading
                ) {
                    Text("Logout")
                }

                if (loading) {
                    CircularProgressIndicator()
                }
            }

        } else {

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
                    "Parent Account",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    if (isSignup) "Create Account" else "Welcome Back",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (minimum 8 characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { authenticate(isSignup) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSignup) "Create Account" else "Login"
                    )
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        isSignup = !isSignup
                        message = ""
                    },
                    enabled = !loading
                ) {
                    Text(
                        if (isSignup)
                            "Already have an account? Login"
                        else
                            "New user? Create Account"
                    )
                }

                if (loading) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "Secure parent access • ANAS CONTROL",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
