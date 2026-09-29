package com.advocacy.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Color Palette ---
val CriticalRed = Color(0xFFC40C00)
val CriticalBlack = Color(0xFF0D0D0D)
val CardBackgroundWhite = Color(0xFFFFFFFF)
val TextDark = Color(0xFF1E1E1E)
val TextMuted = Color(0xFF757575)
val DividerLight = Color(0xFFEEEEEE)
val SoftCalmBackground = Color(0xFFF7F8FA)
val SoftPrimary = Color(0xFF3F51B5)

data class TimelineEvent(
    val time: String,
    val description: String
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NurseAlertFlowContainer(
                        onTriggerVerbalAlarm = { textToSpeak ->
                            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "ALARM_TTS")
                        }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

enum class ScreenState {
    PRE_ALARM_INTAKE,
    CRITICAL_ALERT
}

@Composable
fun NurseAlertFlowContainer(
    onTriggerVerbalAlarm: (String) -> Unit
) {
    var currentState by remember { mutableStateOf(ScreenState.PRE_ALARM_INTAKE) }
    var userDistressMessage by remember {
        mutableStateOf("I'm thinking of hurting myself, I have these thoughts for 6 hrs")
    }

    // Default timeline context reflecting patient journal / intake events
    val timelineEvents = remember {
        mutableStateListOf(
            TimelineEvent("10:18 AM", "Parents called"),
            TimelineEvent("10:05 AM", "Played game"),
            TimelineEvent("09:47 AM", "Refused lunch"),
            TimelineEvent("09:32 AM", "Felt homesick")
        )
    }

    AnimatedContent(
        targetState = currentState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AlertScreenTransition"
    ) { state ->
        when (state) {
            ScreenState.PRE_ALARM_INTAKE -> {
                PreAlarmIntakeScreen(
                    onSubmit = { input ->
                        if (input.isNotBlank()) {
                            userDistressMessage = input.trim()
                            val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                            timelineEvents.add(0, TimelineEvent(currentTime, "Requested nurse assistance"))
                        }
                        currentState = ScreenState.CRITICAL_ALERT
                    }
                )
            }
            ScreenState.CRITICAL_ALERT -> {
                CriticalAlertScreen(
                    distressMessage = userDistressMessage,
                    events = timelineEvents,
                    onSoundAlarm = {
                        val speech = "Nurse assistance required immediately. Patient states: $userDistressMessage"
                        onTriggerVerbalAlarm(speech)
                    },
                    onDispatchContacts = {
                        // Hook for SMS / advocacy webhook notification
                    }
                )
            }
        }
    }
}

/**
 * Stage 1: Warm, calm pre-alarm input screen with voice & text dual entry
 */
@Composable
fun PreAlarmIntakeScreen(
    onSubmit: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftCalmBackground)
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = "Please tell me what's wrong",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                color = TextDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Take your time. You can speak or type below, and we'll communicate it directly to the team.",
                fontSize = 15.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("What are you feeling right now?", color = TextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = 140.dp, max = 220.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBackgroundWhite,
                unfocusedContainerColor = CardBackgroundWhite,
                focusedBorderColor = SoftPrimary,
                unfocusedBorderColor = Color(0xFFD6D6D6)
            )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Dual input trigger: Voice recorder button
            IconButton(
                onClick = { isRecording = !isRecording },
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        color = if (isRecording) CriticalRed else SoftPrimary.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Microphone input",
                    tint = if (isRecording) CardBackgroundWhite else SoftPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isRecording) "Listening..." else "Tap to speak",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isRecording) CriticalRed else TextMuted
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { onSubmit(textInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPrimary)
            ) {
                Text(
                    text = "Continue to Alert Screen",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CardBackgroundWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Stage 2: Production-grade Critical Nurse Alert Screen
 */
@Composable
fun CriticalAlertScreen(
    distressMessage: String,
    events: List<TimelineEvent>,
    onSoundAlarm: () -> Unit,
    onDispatchContacts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CriticalRed)
            .padding(horizontal = 24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        // Badge: CRITICAL ALERT
        Surface(
            color = CriticalBlack,
            shape = RoundedCornerShape(50),
            modifier = Modifier.wrapContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CardBackgroundWhite,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CRITICAL ALERT",
                    color = CardBackgroundWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Headline
        Text(
            text = "URGENT:\nHelp me Please",
            color = CardBackgroundWhite,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 44.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Core State / Distress Message
        Text(
            text = distressMessage,
            color = CardBackgroundWhite.copy(alpha = 0.95f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Recent Events Timeline Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            shape = RoundedCornerShape(18.dp),
            color = CardBackgroundWhite,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "RECENT EVENTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(events) { item ->
                        TimelineRow(event = item)
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 1.dp,
                            color = DividerLight
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action 1: Sound Verbal Alarm
        Button(
            onClick = onSoundAlarm,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundWhite),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = TextDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sound Verbal Alarm",
                    color = TextDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action 2: Dispatch Emergency Contacts
        Button(
            onClick = onDispatchContacts,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CriticalBlack),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = CardBackgroundWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Dispatch Emergency Contacts",
                    color = CardBackgroundWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TimelineRow(event: TimelineEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = TextDark,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = event.time,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.width(76.dp)
        )

        VerticalDivider(
            modifier = Modifier
                .height(14.dp)
                .padding(horizontal = 8.dp),
            thickness = 1.dp,
            color = Color.LightGray
        )

        Text(
            text = event.description,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = TextDark
        )
    }
}
