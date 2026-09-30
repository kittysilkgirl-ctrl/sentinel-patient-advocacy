package com.advocacy.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
    INPUT, ALERT
}

data class TimelineItem(val time: String, val description: String)

@Composable
fun SentinelApp(
    speechRecognizer: SpeechRecognizer,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var currentScreen by remember { mutableStateOf(ScreenState.INPUT) }
    var userInput by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission required for speech input", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                mainExecutor.execute {
                    isListening = true
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                mainExecutor.execute {
                    isListening = false
                }
            }

            override fun onError(error: Int) {
                mainExecutor.execute {
                    isListening = false
                }
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
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    val sampleTimeline = listOf(
        TimelineItem("10:18 AM", "Parents called"),
        TimelineItem("10:05 AM", "Played game"),
        TimelineItem("09:47 AM", "Refused lunch"),
        TimelineItem("09:32 AM", "Felt homesick")
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (currentScreen == ScreenState.INPUT) Color(0xFFF1F5F9) else Color(0xFFB91C1C)
    ) {
        when (currentScreen) {
            ScreenState.INPUT -> InputScreen(
                userInput = userInput,
                onUserInputChange = { userInput = it },
                isListening = isListening,
                onMicClick = { toggleListening() },
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
                onSoundAlarm = { onSpeak(userInput) }
            )
        }
    }
}

@Composable
fun InputScreen(
    userInput: String,
    onUserInputChange: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onContinue: () -> Unit
) {
    val warmBackground = Color(0xFFF1F5F9)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF475569)
    val softCardBg = Color(0xFFFFFFFF)
    val listeningColor = Color(0xFF0D9488) // Calming clinical teal
    val accentNavy = Color(0xFF1E293B)

    val quickAnchors = listOf(
        "I need someone to sit with me",
        "I feel overwhelmed",
        "I am in severe pain",
        "I cannot speak right now"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(warmBackground)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SENTINEL ADVOCACY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFF64748B)
            )

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

            // Quick Anchors: One-tap choices when speech/typing fails
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
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

        // Expanded touch target zone for tremor/stress accessibility
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
                        text = "Send to Alert Screen",
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
    onSoundAlarm: () -> Unit
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
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                TextButton(onClick = onBackToEdit) {
                    Text("Edit", color = Color(0xFFFEE2E2), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "URGENT:\nHelp me Please",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF991B1B)
            ) {
                Text(
                    text = if (patientMessage.isNotBlank()) patientMessage else "I am feeling in crisis and need urgent support right now.",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RECENT EVENTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    timeline.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.time,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.width(72.dp)
                            )
                            Text(
                                text = item.description,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSoundAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = "Sound Verbal Alarm",
                    color = Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* Emergency dispatch logic */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(
                    text = "Dispatch Emergency Contacts",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
