package com.advocacy.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

// ==========================================
// DRIVE-COMPATIBLE STORAGE SCHEMA
// ==========================================

data class SentinelSyncPayload(
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val contactName: String = "",
    val contactPhone: String = "",
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

        return """
        {
          "version": $version,
          "lastModified": $lastModified,
          "contactName": "$escapedName",
          "contactPhone": "$escapedPhone",
          "quickAnchors": $anchorsJson
        }
        """.trimIndent()
    }

    companion object {
        fun fromJson(json: String): SentinelSyncPayload {
            val name = Regex("\"contactName\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""
            val phone = Regex("\"contactPhone\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (currentScreen == ScreenState.ALERT) Color(0xFFB91C1C) else Color(0xFFF1F5F9)
    ) {
        when (currentScreen) {
            ScreenState.INPUT -> InputScreen(
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
                onOpenSettings = { currentScreen = ScreenState.REGISTER_CONTACT },
                onContinue = {
                    if (isListening) {
                        speechRecognizer.stopListening()
                        isListening = false
                    }
                    currentScreen = ScreenState.ALERT
                }
            )

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

            ScreenState.REGISTER_CONTACT -> ContactRegistrationScreen(
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
            )
        }
    }
}

@Composable
fun ContactRegistrationScreen(
    initialName: String,
    initialPhone: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }

    val warmBackground = Color(0xFFF1F5F9)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF475569)
    val softCardBg = Color(0xFFFFFFFF)
    val accentNavy = Color(0xFF1E293B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
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
    userInput: String,
    onUserInputChange: (String) -> Unit,
    quickAnchors: List<String>,
    onAddAnchor: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newAnchorText by remember { mutableStateOf("") }

    val warmBackground = Color(0xFFF1F5F9)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF475569)
    val softCardBg = Color(0xFFFFFFFF)
    val listeningColor = Color(0xFF0D9488)
    val accentNavy = Color(0xFF1E293B)

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
                vertic
