
package com.anascontrol.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.Executors

private const val SUPABASE_URL =
    "https://wnqwslwymzzpunfplolm.supabase.co"

private const val SUPABASE_KEY =
    "sb_publishable_mu0PfBf_CR2eStZvhQm4ng_5sOgrttC"

private val httpClient = OkHttpClient()
private val executor = Executors.newSingleThreadExecutor()

data class ChildDevice(
    val id: String,
    val name: String,
    val status: String,
    val lastSeen: String
)

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

    val context = LocalContext.current

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

    // Child device states
    var childDevices by remember {
        mutableStateOf<List<ChildDevice>>(emptyList())
    }

    var childDevicesLoading by remember {
        mutableStateOf(false)
    }

    var childDevicesMessage by remember {
        mutableStateOf("")
    }

    // Pairing states
    var pairingDeviceName by remember {
        mutableStateOf("Parent Device")
    }

    var pairingCode by remember { mutableStateOf("") }
    var pairingExpiryEpoch by remember { mutableLongStateOf(0L) }
    var pairingRemainingSeconds by remember {
        mutableLongStateOf(0L)
    }

    var pairingLoading by remember { mutableStateOf(false) }
    var pairingMessage by remember { mutableStateOf("") }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    // Pairing countdown
    LaunchedEffect(pairingExpiryEpoch) {
        while (pairingExpiryEpoch > 0L) {
            val remaining =
                pairingExpiryEpoch - Instant.now().epochSecond

            pairingRemainingSeconds =
                remaining.coerceAtLeast(0L)

            if (remaining <= 0L) break

            delay(1000)
        }
    }

    // Load child devices belonging to this family
    fun loadChildDevices(token: String, selectedFamilyId: String) {

        if (token.isBlank() || selectedFamilyId.isBlank()) return

        childDevicesLoading = true
        childDevicesMessage = ""

        executor.execute {
            try {
                val url =
                    "$SUPABASE_URL/rest/v1/devices" +
                    "?select=id,device_name,device_status,last_seen" +
                    "&family_id=eq.$selectedFamilyId" +
                    "&device_type=eq.child" +
                    "&order=created_at.desc"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    val responseText =
                        response.body?.string().orEmpty()

                    mainHandler.post {
                        childDevicesLoading = false

                        if (response.isSuccessful) {
                            try {
                                val array = JSONArray(responseText)
                                val list = mutableListOf<ChildDevice>()

                                for (i in 0 until array.length()) {
                                    val item = array.getJSONObject(i)

                                    val lastSeenRaw =
                                        item.optString("last_seen")
                                            .takeIf {
                                                it.isNotBlank() &&
                                                it != "null"
                                            }.orEmpty()

                                    val formattedLastSeen =
                                        if (lastSeenRaw.isNotBlank()) {
                                            try {
                                                OffsetDateTime.parse(
                                                    lastSeenRaw
                                                ).toLocalDateTime()
                                                    .toString()
                                                    .replace("T", " ")
                                                    .take(19)
                                            } catch (_: Exception) {
                                                lastSeenRaw
                                            }
                                        } else {
                                            "Not reported yet"
                                        }

                                    list.add(
                                        ChildDevice(
                                            id = item.optString("id"),
                                            name = item.optString("device_name"),
                                            status = item.optString(
                                                "device_status",
                                                "offline"
                                            ),
                                            lastSeen = formattedLastSeen
                                        )
                                    )
                                }

                                childDevices = list

                                childDevicesMessage =
                                    if (list.isEmpty()) {
                                        "Abhi koi Child Device connected nahi hai."
                                    } else {
                                        ""
                                    }

                            } catch (e: Exception) {
                                childDevicesMessage =
                                    "Device data read error: ${e.message}"
                            }

                        } else {
                            childDevicesMessage =
                                "Device load error: $responseText"
                        }
                    }
                }

            } catch (e: Exception) {
                mainHandler.post {
                    childDevicesLoading = false
                    childDevicesMessage =
                        "Connection error: ${e.message}"
                }
            }
        }
    }

    // Load family
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

                    val responseText =
                        response.body?.string().orEmpty()

                    mainHandler.post {
                        familyLoading = false

                        if (response.isSuccessful) {
                            try {
                                val families = JSONArray(responseText)

                                if (families.length() > 0) {
                                    val family =
                                        families.getJSONObject(0)

                                    val loadedFamilyId =
                                        family.optString("id")

                                    familyId = loadedFamilyId
                                    familyName =
                                        family.optString("name")

                                    // Automatically load child devices
                                    loadChildDevices(
                                        token,
                                        loadedFamilyId
                                    )

                                } else {
                                    familyId = ""
                                    familyName = ""
                                    childDevices = emptyList()
                                    childDevicesMessage =
                                        "Pehle Family create karo."
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
                        "Connection error: ${e.message}"
                }
            }
        }
    }

    // Login / Signup
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
                                        "Login response mein user details nahi mili."
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
                                            "Request failed."
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
                    message = "Connection error: ${e.message}"
                }
            }
        }
    }

    // Create family
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

                                    loadChildDevices(
                                        accessToken,
                                        familyId
                                    )

                                } else {
                                    familyMessage =
                                        "Family save hui, lekin response empty hai."
                                }

                            } catch (_: Exception) {
                                familyMessage =
                                    "Family response read nahi hua."
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
                        "Connection error: ${e.message}"
                }
            }
        }
    }

    // Generate pairing code
    fun generatePairingCode() {

        if (familyId.isBlank() || accessToken.isBlank()) {
            pairingMessage = "Family aur login session check karo."
            return
        }

        if (pairingDeviceName.isBlank()) {
            pairingMessage = "Parent device ka naam dalo."
            return
        }

        pairingLoading = true
        pairingMessage = ""
        pairingCode = ""
        pairingExpiryEpoch = 0L

        val json = JSONObject().apply {
            put("p_device_name", pairingDeviceName.trim())
        }

        executor.execute {
            try {
                val body = json.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

                val request = Request.Builder()
                    .url(
                        "$SUPABASE_URL/rest/v1/rpc/create_pairing_code"
                    )
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    val responseText =
                        response.body?.string().orEmpty()

                    mainHandler.post {
                        pairingLoading = false

                        if (response.isSuccessful) {
                            try {
                                val data = if (
                                    responseText.trim().startsWith("[")
                                ) {
                                    JSONArray(responseText)
                                        .optJSONObject(0) ?: JSONObject()
                                } else {
                                    JSONObject(responseText)
                                }

                                val code = listOf(
                                    "code",
                                    "pairing_code",
                                    "raw_code",
                                    "token"
                                ).firstNotNullOfOrNull { key ->
                                    data.optString(key)
                                        .takeIf {
                                            it.isNotBlank() &&
                                            it != "null"
                                        }
                                }.orEmpty()

                                if (code.isBlank()) {
                                    pairingMessage =
                                        "RPC response mein code nahi mila."
                                } else {
                                    pairingCode = code

                                    val expiryText =
                                        data.optString("expires_at")

                                    val expiry = try {
                                        Instant.parse(
                                            expiryText
                                        ).epochSecond
                                    } catch (_: Exception) {
                                        try {
                                            OffsetDateTime.parse(
                                                expiryText
                                            ).toInstant().epochSecond
                                        } catch (_: Exception) {
                                            Instant.now()
                                                .plusSeconds(300)
                                                .epochSecond
                                        }
                                    }

                                    pairingExpiryEpoch = expiry

                                    pairingRemainingSeconds =
                                        (expiry - Instant.now().epochSecond)
                                            .coerceAtLeast(0L)

                                    pairingMessage =
                                        "Pairing code successfully generated."
                                }

                            } catch (e: Exception) {
                                pairingMessage =
                                    "Response read error: ${e.message}"
                            }

                        } else {
                            pairingMessage =
                                "Pairing error: $responseText"
                        }
                    }
                }

            } catch (e: Exception) {
                mainHandler.post {
                    pairingLoading = false
                    pairingMessage =
                        "Connection error: ${e.message}"
                }
            }
        }
    }

    // Copy code
    fun copyPairingCode() {

        if (pairingCode.isBlank() ||
            pairingRemainingSeconds <= 0L
        ) return

        val clipboard =
            context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "ANAS CONTROL Pairing Code",
                pairingCode
            )
        )

        pairingMessage = "Code copied successfully!"
    }

    // Logout
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
                    // Clear local session below
                }

            } catch (_: Exception) {
                // Local logout also works offline
            }

            mainHandler.post {
                accessToken = ""
                userId = ""
                userEmail = ""

                familyId = ""
                familyName = ""
                familyInput = ""

                childDevices = emptyList()
                childDevicesMessage = ""

                pairingCode = ""
                pairingExpiryEpoch = 0L
                pairingRemainingSeconds = 0L
                pairingMessage = ""

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

                Text("Welcome, $userEmail")

                Spacer(Modifier.height(24.dp))

                if (familyLoading) {

                    CircularProgressIndicator()

                    Spacer(Modifier.height(12.dp))

                    Text("Checking your family...")

                } else if (familyId.isNotBlank()) {

                    // Family card
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

                            Text(
                                "Family successfully connected to Supabase."
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Child Device Management
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                "Child Device Management",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Your connected child devices",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(Modifier.height(16.dp))

                            if (childDevicesLoading) {

                                CircularProgressIndicator(
                                    modifier = Modifier.align(
                                        Alignment.CenterHorizontally
                                    )
                                )

                                Spacer(Modifier.height(8.dp))

                                Text("Loading child devices...")

                            } else if (childDevices.isEmpty()) {

                                Text(
                                    childDevicesMessage.ifBlank {
                                        "No child device found."
                                    },
                                    color = MaterialTheme.colorScheme.secondary
                                )

                            } else {

                                Text(
                                    "Total Devices: ${childDevices.size}",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Spacer(Modifier.height(12.dp))

                                childDevices.forEachIndexed { index, device ->

                                    if (index > 0) {
                                        HorizontalDivider()
                                        Spacer(Modifier.height(12.dp))
                                    }

                                    Text(
                                        device.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    val isOnline =
                                        device.status.equals(
                                            "online",
                                            ignoreCase = true
                                        )

                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {
                                        Text("Status: ")

                                        Text(
                                            if (isOnline) "Online" else "Offline",
                                            color = if (isOnline) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        "Device ID: ${device.id}",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        "Last Seen: ${device.lastSeen}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            if (childDevicesMessage.isNotBlank() &&
                                childDevices.isNotEmpty()
                            ) {
                                Spacer(Modifier.height(8.dp))

                                Text(
                                    childDevicesMessage,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = {
                                    loadChildDevices(
                                        accessToken,
                                        familyId
                                    )
                                },
                                enabled = !childDevicesLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Refresh Child Devices")
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Pairing card
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(
                                "Child Device Pairing",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Generate a temporary code to connect an authorized child device."
                            )

                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = pairingDeviceName,
                                onValueChange = {
                                    pairingDeviceName = it
                                },
                                label = {
                                    Text("Parent Device Name")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            if (pairingCode.isNotBlank()) {

                                Text("YOUR PAIRING CODE")

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    pairingCode,
                                    style = MaterialTheme.typography.headlineMedium
                                )

                                Spacer(Modifier.height(8.dp))

                                if (pairingRemainingSeconds > 0L) {

                                    val minutes =
                                        pairingRemainingSeconds / 60

                                    val seconds =
                                        pairingRemainingSeconds % 60

                                    Text(
                                        "Expires in %02d:%02d".format(
                                            minutes,
                                            seconds
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = {
                                            copyPairingCode()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Copy Pairing Code")
                                    }

                                } else {
                                    Text(
                                        "Code expired. Generate a new code.",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(Modifier.height(12.dp))
                            }

                            Button(
                                onClick = {
                                    generatePairingCode()
                                },
                                enabled = !pairingLoading &&
                                    pairingRemainingSeconds <= 0L,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (pairingLoading) {
                                        "Generating..."
                                    } else if (pairingCode.isNotBlank()) {
                                        "Generate New Code"
                                    } else {
                                        "Generate Pairing Code"
                                    }
                                )
                            }

                            if (pairingMessage.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))

                                Text(
                                    pairingMessage,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Share this code only with the child device you are authorized to manage. It expires after 5 minutes.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                } else {

                    // Create Family
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
                                "Apni family ka naam set karo."
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
                                onClick = {
                                    createFamily()
                                },
                                enabled = !creatingFamily,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (creatingFamily) {
                                        "Creating..."
                                    } else {
                                        "Create Family"
                                    }
                                )
                            }
                        }
                    }
                }

                if (familyMessage.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))

                    Text(
                        familyMessage,
                        color = MaterialTheme.colorScheme.error
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
                    onClick = {
                        logout()
                    },
                    enabled = !loading &&
                        !creatingFamily &&
                        !pairingLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }

        } else {

            // Login / Signup screen
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
                    onValueChange = {
                        email = it
                    },
                    label = {
                        Text("Email Address")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                    },
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
                    onClick = {
                        authenticate(isSignup)
                    },
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
                        if (isSignup) {
                            "Already have an account? Login"
                        } else {
                            "New user? Create Account"
                        }
                    )
                }

                if (loading) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))

                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error
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
