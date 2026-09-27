package com.advocacy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdvocacyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdvocacyApp()
                }
            }
        }
    }
}

@Composable
fun AdvocacyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF90CAF9),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onBackground = Color(0xFFE0E0E0),
            onSurface = Color(0xFFE0E0E0)
        ),
        content = content
    )
}

data class AdvocacyItem(val id: Int, val title: String, val detail: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvocacyApp() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient Advocacy & Safety Engine", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Clinical Notes") },
                    icon = { Text("📋") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Calibration (F-02)") },
                    icon = { Text("🎯") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (selectedTab) {
                0 -> ClinicalNotesScreen()
                1 -> CalibrationScreen()
            }
        }
    }
}

@Composable
fun ClinicalNotesScreen() {
    val items = remember {
        listOf(
            AdvocacyItem(1, "Care Plan Alignment", "Managerial review active. Co-produced discharge readiness criteria pending."),
            AdvocacyItem(2, "Trauma-Informed Allocation", "Primary nurse assignment safety boundary established."),
            AdvocacyItem(3, "Physical Vulnerabilities", "Fibromyalgia flare monitoring & diabetes safety protocols logged.")
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Advocacy Status & Directives",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(items) { item ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.detail, fontSize = 13.sp, color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun CalibrationScreen() {
    var tapCount by remember { mutableStateOf(0) }
    var calibrated by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Gesture Calibration (F-02)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Calibrate micro-tap threshold for acute motor freeze or tremor states.",
            fontSize = 13.sp,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                tapCount++
                if (tapCount >= 5) calibrated = true
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(140.dp)
        ) {
            Text(if (calibrated) "Calibrated" else "Tap ($tapCount/5)")
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (calibrated) {
            Text("Baseline sensitivity logged successfully.", color = Color(0xFF81C784), fontWeight = FontWeight.Medium)
        }
    }
}

