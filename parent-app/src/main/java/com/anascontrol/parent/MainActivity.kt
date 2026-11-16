
package com.anascontrol.parent

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

private const val SUPABASE_URL =
    "https://wnqwslwymzzpunfplolm.supabase.co"

private const val SUPABASE_KEY =
    "sb_publishable_mu0PfBf_CR2eStZvhQm4ng_5sOgrttC"

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
    var userId by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }

    var familyLoading by remember { mutableStateOf(false) }
    var familyId by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf("") }
    var familyInput by remember { mutableStateOf("") }
    var familyMessage by remember { mutableStateOf("") }
    var creatingFamily by remember { mutableStateOf(false) }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    fun loadFamily(token: String, parentId: String) {
        familyLoading = true
        familyMessage = ""

        executor.execute {
            try {
                val request = Request.Builder()
                    .url(
                        "$SUPABASE_URL/rest/v1/families" +
                            "?select=id,name&owner_id=eq.$parentId"
                    )
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()

                    mainHandler.post {
                        familyLoading = false

                        if (response.isSuccessful) {
                            try {
                                val families = JSONArray(responseText)

                                if (families.length() > 0) {
                                    val family = families.getJSONObject(0)
                                    familyId = family.optString("id")
                                    familyName = family.optString("name")
                                } else {
                                    familyId = ""
                                    familyName = ""
                                }
                            } catch (_: Exception) {
                                familyMessage =
                                    "Family data read nahi ho paya."
                            }
                        } else {
                            familyMessage =
                                "Family load error: $responseText"
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    familyLoading = false
                    familyMessage =
                        "Connection error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    fun authenticate(signup: Boolean) {

        if (email.isBlank() || password.length < 8) {
            message =
                "Valid email aur minimum 8-character password dalo."
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
                            val token =
                                result.optString("access_token")

                            val user =
                                result.optJSONObject("user")

                            val id =
                                user?.optString("id").orEmpty()

                            if (token.isNotBlank() && id.isNotBlank()) {
                                accessToken = token
                                userId = id
                                userEmail = currentEmail
                                loggedIn = true

                                loadFamily(token, id)
                            } else {
                                message =
                                    if (signup) {
                                        "Account created. Ab Login karo."
                                    } else {
                                        "Login response mein user details nahi mili. Dobara login karo."
                                    }
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
                    message =
                        "Connection error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    fun createFamily() {

        val name = familyInput.trim()

        if (name.length < 2) {
            familyMessage =
                "Family ka naam kam se kam 2 characters ka rakho."
            return
        }

        if (accessToken.isBlank() || userId.isBlank()) {
            familyMessage = "Please dobara login karo."
            return
        }

        creatingFamily = true
        familyMessage = ""

        val json = JSONObject().apply {
            put("name", name)
            put("owner_id", userId)
        }

        executor.execute {
            try {
                val body = json.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/families?select=id,name")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    val responseText =
                        response.body?.string().orEmpty()

                    mainHandler.post {
                        creatingFamily = false

                        if (response.isSuccessful) {
                            try {
                                val result = JSONArray(responseText)

                                if (result.length() > 0) {
                                    val family =
                                        result.getJSONObject(0)

                                    familyId =
                                        family.optString("id")

                                    familyName =
                                        family.optString("name")

                                    familyInput = ""
                                    familyMessage =
                                        "Family successfully created!"
                                } else {
                                    familyMessage =
                                        "Family save hui, lekin response empty hai. Refresh karke check karo."
                                }
                            } catch (_: Exception) {
                                familyMessage =
                                    "Family save hui, lekin response read nahi hua."
                            }
                        } else {
                            familyMessage =
                                "Family create error: $responseText"
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    creatingFamily = false
                    familyMessage =
                        "Connection error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    fun logout() {
        val token = accessToken

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
                    // Session is cleared locally below.
                }
            } catch (_: Exception) {
                // Local logout is allowed even if offline.
            }

            mainHandler.post {
                accessToken = ""
                userId = ""
                userEmail = ""
                familyId = ""
                familyName = ""
                familyInput = ""
                loggedIn = false
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(Modifier.height(24.dp))

                Text(
                    "ANAS CONTROL",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(Modifier.height(8.dp))

                Text("Parent Dashboard")

                Spacer(Modifier.height(8.dp))

                Text(
                    "Welcome, $userEmail",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(24.dp))

                if (familyLoading) {

                    CircularProgressIndicator()

                    Spacer(Modifier.height(12.dp))

                    Text("Checking your family...")

                } else if (familyId.isNotBlank()) {

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                "Your Family",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                familyName,
                                style = MaterialTheme.typography.headlineSmall
                            )

                            Spacer(Modifier.height(8.dp))

                            Text("Family ID: $familyId")

                            Spacer(Modifier.height(16.dp))

                            Text("Family successfully connected to Supabase.")
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        "Next phase: Child Device Pairing",
                        style = MaterialTheme.typography.titleMedium
                    )

                } else {

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                "Create Your Family",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Apni family ka naam set karo. Iske baad child device pairing add karenge."
                            )

                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = familyInput,
                                onValueChange = {
                                    familyInput = it
                                },
                                label = {
                                    Text("Family Name")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = { createFamily() },
                                enabled = !creatingFamily,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (creatingFamily)
                                        "Creating..."
                                    else
                                        "Create Family"
                                )
                            }
                        }
                    }
                }

                if (familyMessage.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))

                    Text(
                        familyMessage,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (familyId.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            loadFamily(accessToken, userId)
                        },
                        enabled = !familyLoading
                    ) {
                        Text("Refresh Family")
                    }
                }

                Spacer(Modifier.height(28.dp))

                OutlinedButton(
                    onClick = { logout() },
                    enabled = !loading && !creatingFamily,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
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
                    label = {
                        Text("Password (minimum 8 characters)")
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
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
                        color = MaterialTheme.colorScheme.primary
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
