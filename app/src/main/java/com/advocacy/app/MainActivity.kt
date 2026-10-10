// app/src/main/java/com/advocacy/app/MainActivity.kt
package com.advocacy.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

// ==========================================
// DRIVE-COMPATIBLE STORAGE SCHEMA
// ==========================================

data class SentinelSyncPayload(
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val contactName: String = "",
    val contactPhone: String = "",
    val lookName: String = "board",
    val quickAnchors: List<String> = listOf(
        "I need someone to sit with me",
        "I feel overwhelmed",
        "I am in severe pain",
        "I cannot speak right now"
    )
) {
    fun toJson(): String {
        val escapedName = contactName.replace("\"", "\\\"")
        val escapedPhone = contactPhone.replace("\"", "\\\"")
        val anchorsJson = quickAnchors.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        ) { "\"${it.replace("\"", "\\\"")}\"" }

        val escapedLook = lookName.replace("\"", "\\\"")
        return """
        {
          "version": $version,
          "lastModified": $lastModified,
          "contactName": "$escapedName",
          "contactPhone": "$escapedPhone",
          "lookName": "$escapedLook",
          "quickAnchors": $anchorsJson
        }
        """.trimIndent()
    }

    companion object {
        fun fromJson(json: String): SentinelSyncPayload {
            val name = Regex("\"contactName\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""
            val phone = Regex("\"contactPhone\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""
            val look = Regex("\"lookName\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: "board"
            val anchorsBlock = Regex("\"quickAnchors\":\\s*\\[(.*?)\\]").find(json)?.groupValues?.get(1)
            val anchors = if (!anchorsBlock.isNullOrBlank()) {
                Regex("\"(.*?)\"").findAll(anchorsBlock).map { it.groupValues[1] }.toList()
            } else {
                listOf(
                    "I need someone to sit with me",
                    "I feel overwhelmed",
                    "I am in severe pain",
                    "I cannot speak right now"
                )
            }
            return SentinelSyncPayload(
                contactName = name,
                contactPhone = phone,
                lookName = look,
                quickAnchors = anchors
            )
        }
    }
}

class SentinelStorageManager(private val context: Context) {
    private val fileName = "sentinel_backup.json"

    fun savePayload(payload: SentinelSyncPayload) {
        try {
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                output.write(payload.toJson().toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readPayload(): SentinelSyncPayload {
        return try {
            val content = context.openFileInput(fileName).bufferedReader().use { it.readText() }
            SentinelSyncPayload.fromJson(content)
        } catch (e: Exception) {
            SentinelSyncPayload()
        }
    }
}


data class CalmLook(
    val name: String,
    val label: String,
    val page: Color,
    val card: Color,
    val ink: Color,
    val soft: Color,
    val accent: Color,
    val font: String = ""
)

private val lookChoices = listOf(
    "bolt" to "Thunderbolt",
    "sea" to "Ocean",
    "board" to "Whiteboard",
    "bunny" to "Fluffy bunnies"
)

private fun colorOr(raw: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(raw.trim()))
    } catch (e: Exception) {
        fallback
    }
}

fun loadCalmLook(context: Context, name: String): CalmLook {
    val fallback = CalmLook(
        name = "board",
        label = "Whiteboard",
        page = Color(0xFFF7F6F3),
        card = Color(0xFFFFFFFF),
        ink = Color(0xFF1E2430),
        soft = Color(0xFF8A908C),
        accent = Color(0xFF3D5A80)
    )
    val safeName = if (lookChoices.any { it.first == name }) name else "board"
    val id = context.resources.getIdentifier(safeName, "xml", context.packageName)
    if (id == 0) return fallback.copy(name = safeName)
    return try {
        val parser = context.resources.getXml(id)
        var label = lookChoices.first { it.first == safeName }.second
        var page = fallback.page
        var card = fallback.card
        var ink = fallback.ink
        var soft = fallback.soft
        var accent = fallback.accent
        var font = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "look" -> label = parser.getAttributeValue(null, "label") ?: label
                    "page" -> page = colorOr(parser.nextText(), page)
                    "card" -> card = colorOr(parser.nextText(), card)
                    "ink" -> ink = colorOr(parser.nextText(), ink)
                    "soft" -> soft = colorOr(parser.nextText(), soft)
                    "accent" -> accent = colorOr(parser.nextText(), accent)
                    "font" -> font = parser.nextText().trim()
                }
            }
            event = parser.next()
        }
        parser.close()
        CalmLook(safeName, label, page, card, ink, soft, accent, font)
    } catch (e: Exception) {
        fallback.copy(name = safeName, label = lookChoices.first { it.first == safeName }.second)
    }
}


private fun Typography.cursive(): Typography {
    val cursive = FontFamily.Cursive
    return copy(
        displayLarge = displayLarge.copy(fontFamily = cursive),
        displayMedium = displayMedium.copy(fontFamily = cursive),
        displaySmall = displaySmall.copy(fontFamily = cursive),
        headlineLarge = headlineLarge.copy(fontFamily = cursive),
        headlineMedium = headlineMedium.copy(fontFamily = cursive),
        headlineSmall = headlineSmall.copy(fontFamily = cursive),
        titleLarge = titleLarge.copy(fontFamily = cursive),
        titleMedium = titleMedium.copy(fontFamily = cursive),
        titleSmall = titleSmall.copy(fontFamily = cursive),
        bodyLarge = bodyLarge.copy(fontFamily = cursive),
        bodyMedium = bodyMedium.copy(fontFamily = cursive),
        bodySmall = bodySmall.copy(fontFamily = cursive),
        labelLarge = labelLarge.copy(fontFamily = cursive),
        labelMedium = labelMedium.copy(fontFamily = cursive),
        labelSmall = labelSmall.copy(fontFamily = cursive)
    )
}

@Composable
private fun CalmFont(look: CalmLook, content: @Composable () -> Unit) {
    if (look.font == "cursive") {
        MaterialTheme(typography = MaterialTheme.typography.cursive()) {
            CompositionLocalProvider(
                LocalTextStyle provides TextStyle(fontFamily = FontFamily.Cursive)
            ) {
                content()
            }
        }
    } else {
        content()
    }
}

// ==========================================
// MAIN ACTIVITY
// ==========================================

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var speechRecognizer: SpeechRecognizer
    private var tts: TextToSpeech? = null
    private var isTtsInitialized by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme {
                SentinelApp(
                    speechRecognizer = speechRecognizer,
                    onSpeak = { textToSpeak -> speakAlert(textToSpeak) }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.UK
            isTtsInitialized = true
        }
    }

    private fun speakAlert(text: String) {
        if (isTtsInitialized) {
            val spokenMessage = if (text.isNotBlank()) {
                "Attention required immediately. Patient states: $text"
            } else {
                "Attention required immediately. Patient has triggered an urgent alert."
            }
            tts?.speak(spokenMessage, TextToSpeech.QUEUE_FLUSH, null, "UrgentAlertTTS")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}

enum class ScreenState {
    INPUT, ALERT, REGISTER_CONTACT
}

data class TimelineItem(val time: String, val description: String)

@Composable
fun SentinelApp(
    speechRecognizer: SpeechRecognizer,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val storageManager = remember(context) { SentinelStorageManager(context) }

    var currentScreen by remember { mutableStateOf(ScreenState.INPUT) }
    var userInput by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf(storageManager.readPayload()) }
    var showLookDialog by remember { mutableStateOf(false) }
    val look = remember(payload.lookName) { loadCalmLook(context, payload.lookName) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == false) {
            Toast.makeText(context, "Microphone permission required for speech input", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                mainExecutor.execute { isListening = true }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                mainExecutor.execute { isListening = false }
            }

            override fun onError(error: Int) {
                mainExecutor.execute { isListening = false }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                mainExecutor.execute {
                    if (!matches.isNullOrEmpty()) {
                        userInput = matches[0]
                    }
                    isListening = false
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                mainExecutor.execute {
                    if (!matches.isNullOrEmpty()) {
                        userInput = matches[0]
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer.setRecognitionListener(listener)

        onDispose {
            speechRecognizer.stopListening()
        }
    }

    val toggleListening = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        } else {
            if (isListening) {
                speechRecognizer.stopListening()
                isListening = false
            } else {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer.startListening(intent)
                isListening = true
            }
        }
    }

    val dispatchEmergencyAlert = {
        if (payload.contactPhone.isBlank()) {
            Toast.makeText(context, "No emergency contact saved yet. Please add someone below.", Toast.LENGTH_LONG).show()
            currentScreen = ScreenState.REGISTER_CONTACT
        } else {
            val alertMessage = "URGENT SENTINEL ALERT: $userInput"
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:${payload.contactPhone}")
                    putExtra("sms_body", alertMessage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open messaging: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val sampleTimeline = listOf(
        TimelineItem("10:18 AM", "Parents called"),
        TimelineItem("10:05 AM", "Played game"),
        TimelineItem("09:47 AM", "Refused lunch"),
        TimelineItem("09:32 AM", "Felt homesick")
    )

    if (showLookDialog) {
        CalmFont(look) {
        AlertDialog(
            onDismissRequest = { showLookDialog = false },
            containerColor = look.card,
            titleContentColor = look.ink,
            textContentColor = look.ink,
            title = { Text("Calm look", fontWeight = FontWeight.Bold, color = look.ink) },
            text = {
                Column {
                    lookChoices.forEach { (name, label) ->
                        TextButton(
                            onClick = {
                                val updated = payload.copy(
                                    lookName = name,
                                    lastModified = System.currentTimeMillis()
                                )
                                storageManager.savePayload(updated)
                                payload = updated
                                showLookDialog = false
                            }
                        ) {
                            Text(
                                text = if (payload.lookName == name) "$label  (this one)" else label,
                                color = look.ink,
                                fontWeight = if (payload.lookName == name) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            showLookDialog = false
                            currentScreen = ScreenState.REGISTER_CONTACT
                        }
                    ) {
                        Text("Support contact", color = look.soft)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLookDialog = false }) {
                    Text("Leave it for now", color = look.soft)
                }
            }
        )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (currentScreen == ScreenState.ALERT) Color(0xFFB91C1C) else look.page
    ) {
        when (currentScreen) {
            ScreenState.INPUT -> CalmFont(look) { InputScreen(
                look = look,
                userInput = userInput,
                onUserInputChange = { userInput = it },
                quickAnchors = payload.quickAnchors,
                onAddAnchor = { newAnchor ->
                    val updatedAnchors = payload.quickAnchors + newAnchor
                    val updatedPayload = payload.copy(
                        quickAnchors = updatedAnchors,
                        lastModified = System.currentTimeMillis()
                    )
                    storageManager.savePayload(updatedPayload)
                    payload = updatedPayload
                },
                isListening = isListening,
                onMicClick = { toggleListening() },
                onOpenLook = { showLookDialog = true },
                onContinue = {
                    if (isListening) {
                        speechRecognizer.stopListening()
                        isListening = false
                    }
                    currentScreen = ScreenState.ALERT
                }
            ) }

            ScreenState.ALERT -> AlertScreen(
                patientMessage = userInput,
                timeline = sampleTimeline,
                onBackToEdit = { currentScreen = ScreenState.INPUT },
                onDismissAlert = {
                    userInput = ""
                    currentScreen = ScreenState.INPUT
                },
                onSoundAlarm = { onSpeak(userInput) },
                onDispatch = { dispatchEmergencyAlert() }
            )

            ScreenState.REGISTER_CONTACT -> CalmFont(look) { ContactRegistrationScreen(
                look = look,
                initialName = payload.contactName,
                initialPhone = payload.contactPhone,
                onSave = { name, phone ->
                    val updated = payload.copy(
                        contactName = name,
                        contactPhone = phone,
                        lastModified = System.currentTimeMillis()
                    )
                    storageManager.savePayload(updated)
                    payload = updated
                    Toast.makeText(context, "Contact details saved", Toast.LENGTH_SHORT).show()
                    currentScreen = ScreenState.INPUT
                },
                onCancel = { currentScreen = ScreenState.INPUT }
            ) }
        }
    }
}

private fun readPickedPerson(context: Context, uri: Uri): Pair<String, String> {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return "" to ""
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val pickedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
            val pickedNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
            if (pickedName.isNotBlank() || pickedNumber.isNotBlank()) {
                return pickedName to pickedNumber
            }
            val contactNameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val contactName = if (contactNameIdx >= 0) cursor.getString(contactNameIdx).orEmpty() else ""
            contactName to ""
        } ?: ("" to "")
    } catch (e: Exception) {
        "" to ""
    }
}

@Composable
fun ContactRegistrationScreen(
    look: CalmLook,
    initialName: String,
    initialPhone: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    val context = LocalContext.current

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        val picked = readPickedPerson(context, uri)
        if (picked.first.isNotBlank()) name = picked.first
        if (picked.second.isNotBlank()) {
            phone = picked.second
        } else {
            Toast.makeText(context, "No number on that person. You can type one below.", Toast.LENGTH_LONG).show()
        }
    }

    fun openPhonePeople() {
        try {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            pickLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open your contacts. You can type the name and number below.", Toast.LENGTH_LONG).show()
        }
    }

    val askContacts = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openPhonePeople()
        } else {
            Toast.makeText(context, "That is ok. You can type their name and number below.", Toast.LENGTH_LONG).show()
        }
    }

    val warmBackground = look.page
    val textPrimary = look.ink
    val textSecondary = look.soft
    val softCardBg = look.card
    val accentNavy = look.accent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBackground)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onCancel() }
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Leave it for now", color = textSecondary, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Support Contact",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textPrimary
            )
            Text(
                text = "When you need to reach out, Sentinel will prepare a direct message to this person immediately.",
                fontSize = 14.sp,
                color = textSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            OutlinedButton(
                onClick = {
                    val allowed = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (allowed) openPhonePeople() else askContacts.launch(Manifest.permission.READ_CONTACTS)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentNavy),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentNavy)
            ) {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = accentNavy)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose someone from my phone", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Or type their name and number below. Either way is fine.",
                fontSize = 14.sp,
                color = textSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )

            Text(
                text = "NAME OR RELATIONSHIP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Sarah, Advocate, Sister", color = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = softCardBg,
                    unfocusedContainerColor = softCardBg,
                    focusedBorderColor = Color(0xFF94A3B8),
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PHONE NUMBER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = { Text("e.g. +447123456789", color = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = softCardBg,
                    unfocusedContainerColor = softCardBg,
                    focusedBorderColor = Color(0xFF94A3B8),
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onSave(name, phone) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentNavy)
        ) {
            Text("Keep this person", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun InputScreen(
    look: CalmLook,
    userInput: String,
    onUserInputChange: (String) -> Unit,
    quickAnchors: List<String>,
    onAddAnchor: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onOpenLook: () -> Unit,
    onContinue: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newAnchorText by remember { mutableStateOf("") }

    val warmBackground = look.page
    val textPrimary = look.ink
    val textSecondary = look.soft
    val softCardBg = look.card
    val listeningColor = Color(0xFF0D9488)
    val accentNavy = look.accent

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Grounding Anchor", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newAnchorText,
                    onValueChange = { newAnchorText = it },
                    placeholder = { Text("e.g. Please bring water") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newAnchorText.isNotBlank()) {
                            onAddAnchor(newAnchorText.trim())
                            newAnchorText = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Remember this phrase", fontWeight = FontWeight.Bold, color = accentNavy)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Leave it for now", color = textSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBackground)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SENTINEL ADVOCACY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFF64748B)
                )
                IconButton(onClick = onOpenLook) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Calm look",
                        tint = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Take a breath.\nYou are heard here.",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textPrimary,
                lineHeight = 34.sp
            )
            Text(
                text = "Speak or tap below. Whatever you share goes straight to the team.",
                fontSize = 14.sp,
                color = textSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.clickable { showAddDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add custom phrase",
                                tint = accentNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add phrase",
                                color = accentNavy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                items(quickAnchors) { anchor ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = softCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.clickable { onUserInputChange(anchor) }
                    ) {
                        Text(
                            text = anchor,
                            color = accentNavy,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = userInput,
                onValueChange = onUserInputChange,
                placeholder = {
                    Text(
                        text = if (isListening) "Listening to you now..." else "Tap the circle below to speak, or write here...",
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = softCardBg,
                    unfocusedContainerColor = softCardBg,
                    focusedBorderColor = Color(0xFF94A3B8),
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                )
            )

            AnimatedVisibility(visible = isListening) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(listeningColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I'm listening. Take all the time you need.",
                        color = listeningColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (isListening) listeningColor.copy(alpha = 0.18f) else Color(0xFFE2E8F0))
                    .clickable { onMicClick() }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isListening) listeningColor else accentNavy)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp, 28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isListening) "Tap circle to pause" else "Tap circle to speak",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isListening) listeningColor else textSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentNavy)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "I need immediate help",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AlertScreen(
    patientMessage: String,
    timeline: List<TimelineItem>,
    onBackToEdit: () -> Unit,
    onDismissAlert: () -> Unit,
    onSoundAlarm: () -> Unit,
    onDispatch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF7F1D1D)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CRITICAL ALERT",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
                TextButton(onClick = onBackToEdit) {
                    Text("Change my words", color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "URGENT: Help me Please",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 34.sp
            )
            Text(
                text = if (patientMessage.isBlank()) "No extra words yet." else patientMessage,
                fontSize = 16.sp,
                color = Color(0xFFFEE2E2),
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
            )

            Text(
                text = "RECENT EVENTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFECACA),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            timeline.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(item.time, color = Color(0xFFFECACA), fontSize = 13.sp, modifier = Modifier.width(84.dp))
                    Text(item.description, color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSoundAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Read my words aloud", color = Color(0xFF7F1D1D), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDispatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D))
            ) {
                Text("Reach out to my person", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onDismissAlert,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I'm feeling steadier now", color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
            }
        }
    }
}
