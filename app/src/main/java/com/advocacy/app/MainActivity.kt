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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                "Attention required immediately. Patient states: \$text"
            } else {
                "Attention required immediately. Patient triggered an urgent distress alert."
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
    INPUT, ALERT, REGISTER_CONTACT, JOURNEY
}

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
            Toast.makeText(context, "Microphone permission is needed for voice check-in", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "No support contact saved yet. Add someone below.", Toast.LENGTH_LONG).show()
            currentScreen = ScreenState.REGISTER_CONTACT
        } else {
            val alertMessage = "URGENT SENTINEL ALERT: \${userInput.ifBlank { "Patient requested immediate emergency help." }}"
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:\${payload.contactPhone}")
                    putExtra("sms_body", alertMessage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open messaging: \${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
        color = if (currentScreen == ScreenState.ALERT) Color(0xFFB91C1C) else SanctuaryColors.Background
    ) {
        when (currentScreen) {
            ScreenState.INPUT -> SanctuaryHomeScreen(
                userInput = userInput,
                onUserInputChange = { userInput = it },
                isListening = isListening,
                onMicClick = { toggleListening() },
                onNavigateSettings = { currentScreen = ScreenState.REGISTER_CONTACT },
                onNavigateJournal = { currentScreen = ScreenState.JOURNEY },
                onTriggerAlert = { currentScreen = ScreenState.ALERT }
            )

            ScreenState.JOURNEY -> JourneyScreen(
                onBack = { currentScreen = ScreenState.INPUT },
                onUrgent = { currentScreen = ScreenState.ALERT }
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
fun SanctuaryHomeScreen(
    userInput: String,
    onUserInputChange: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateJournal: () -> Unit,
    onTriggerAlert: () -> Unit
) {
    var checkInLevel by remember { mutableStateOf(4f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SanctuaryColors.Background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SanctuaryColors.CardBg)
                    .clickable { onNavigateSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = SanctuaryColors.TextCream
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "FLUFFY BUNNY",
                    color = SanctuaryColors.TextCream,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "SCHEME",
                    color = SanctuaryColors.TextCream,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.ic_maya_butterfly),
                    contentDescription = "Maya Emblem",
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "MAYA",
                    color = SanctuaryColors.CardBorderCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // STATUS PILL
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = SanctuaryColors.CardBorderCyan.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(SanctuaryColors.CardBg, shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "「 Your Gentle Sanctuary 」",
                color = SanctuaryColors.CardBorderCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // COZY CHECK-IN CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(SanctuaryColors.CardBorderCyan, SanctuaryColors.CardBorderPink)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .background(SanctuaryColors.CardBg, shape = RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "COZY CHECK-IN",
                    color = SanctuaryColors.TextCream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmotionFace(emoji = "😣", label = "Overwhelmed", tint = SanctuaryColors.EmotionRed, isSelected = checkInLevel < 0.5f) { checkInLevel = 0f }
                    EmotionFace(emoji = "🥺", label = "Sooly", tint = SanctuaryColors.EmotionOrange, isSelected = checkInLevel in 0.5f..1.5f) { checkInLevel = 1f }
                    EmotionFace(emoji = "😐", label = "Expressive", tint = SanctuaryColors.EmotionYellow, isSelected = checkInLevel in 1.5f..2.5f) { checkInLevel = 2f }
                    EmotionFace(emoji = "🙂", label = "Pendily", tint = SanctuaryColors.EmotionGreen, isSelected = checkInLevel in 2.5f..3.5f) { checkInLevel = 3f }
                    EmotionFace(emoji = "☺️", label = "Cozy & Safe", tint = SanctuaryColors.EmotionCyan, isSelected = checkInLevel >= 3.5f) { checkInLevel = 4f }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = checkInLevel,
                    onValueChange = { checkInLevel = it },
                    valueRange = 0f..4f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = SanctuaryColors.CardBorderCyan,
                        activeTrackColor = SanctuaryColors.CardBorderCyan,
                        inactiveTrackColor = SanctuaryColors.SliderTrack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "How is your sensory space today?",
                    color = SanctuaryColors.GlowPeach,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PACER & BURROW CARDS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(175.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SanctuaryColors.CardBg)
                    .clickable { onMicClick() }
                    .padding(14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SOOTHING PACER",
                        color = SanctuaryColors.TextCream,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    PacerPulseImage(isListening = isListening)

                    Text(
                        text = if (isListening) "Listening... 🌸" else "Gentle breathing\nsupport 🌸 Calm",
                        color = SanctuaryColors.TextCream,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(175.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SanctuaryColors.CardBg)
                    .clickable { onNavigateJournal() }
                    .padding(14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RESOURCE\nBURROW",
                        color = SanctuaryColors.TextCream,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Image(
                        painter = painterResource(id = R.drawable.ic_resource_burrow),
                        contentDescription = "Resource Burrow",
                        modifier = Modifier.size(56.dp)
                    )

                    Text(
                        text = "Sensory comforts ➔",
                        color = SanctuaryColors.CardBorderCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "BURROW ESCAPE",
            color = SanctuaryColors.GlowPeach,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        SlideToRestBar(onTriggerAlert = onTriggerAlert)

        Spacer(modifier = Modifier.height(24.dp))

        BottomNavCapsule(
            onHome = {},
            onJournal = onNavigateJournal,
            onPacer = onMicClick,
            onDirectory = onNavigateSettings
        )
    }
}

@Composable
fun JourneyScreen(
    onBack: () -> Unit,
    onUrgent: () -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember(context) { SentinelStorageManager(context) }
    var notes by remember { mutableStateOf(storageManager.getNotes()) }
    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteBody by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SanctuaryColors.Background)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = SanctuaryColors.CardBorderCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = SanctuaryColors.CardBorderCyan, fontSize = 14.sp)
            }
            Text("MAYA 🦋", color = SanctuaryColors.CardBorderPink, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Daily Cozy Journal", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = SanctuaryColors.TextCream)
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = newNoteTitle,
                onValueChange = { newNoteTitle = it },
                placeholder = { Text("Title or focus...", color = SanctuaryColors.TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SanctuaryColors.TextCream,
                    unfocusedTextColor = SanctuaryColors.TextCream,
                    focusedContainerColor = SanctuaryColors.CardBg,
                    unfocusedContainerColor = SanctuaryColors.CardBg,
                    focusedBorderColor = SanctuaryColors.CardBorderPink,
                    unfocusedBorderColor = SanctuaryColors.TextMuted
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = newNoteBody,
                onValueChange = { newNoteBody = it },
                placeholder = { Text("What did your calm or sensory space look like today?", color = SanctuaryColors.TextMuted) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SanctuaryColors.TextCream,
                    unfocusedTextColor = SanctuaryColors.TextCream,
                    focusedContainerColor = SanctuaryColors.CardBg,
                    unfocusedContainerColor = SanctuaryColors.CardBg,
                    focusedBorderColor = SanctuaryColors.CardBorderPink,
                    unfocusedBorderColor = SanctuaryColors.TextMuted
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (newNoteTitle.isNotBlank() || newNoteBody.isNotBlank()) {
                        storageManager.addNote("DAILY", newNoteTitle.trim(), newNoteBody.trim())
                        notes = storageManager.getNotes()
                        newNoteTitle = ""
                        newNoteBody = ""
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SanctuaryColors.CardBorderPink),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Entry", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("RECENT ENTRIES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanctuaryColors.CardBorderCyan)
            Spacer(modifier = Modifier.height(8.dp))

            notes.forEach { note ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryColors.CardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (note.second.isNotBlank()) {
                            Text(note.second, fontWeight = FontWeight.Bold, color = SanctuaryColors.TextCream, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(note.third, color = SanctuaryColors.TextMuted, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onUrgent,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
            Text("Urgent Help Needed", color = Color.White, fontWeight = FontWeight.Bold)
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
            Toast.makeText(context, "No number found on this contact.", Toast.LENGTH_LONG).show()
        }
    }

    val askContacts = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                pickLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open contacts.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SanctuaryColors.Background)
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
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = SanctuaryColors.CardBorderCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = SanctuaryColors.CardBorderCyan, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Support Directory", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SanctuaryColors.TextCream)
            Text(
                "When you slide to rest or summon assistance, an alert goes directly