package com.yourpackage.name // keep your actual package statement at the top

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThoughtCard(
    initialText: String = "",
    onKeepWords: (String) -> Unit = {},
    onLetThisGo: () -> Unit = {},
    onShareWhenReady: ((String) -> Unit)? = null
) {
    var isUpdating by remember { mutableStateOf(false) }
    var thoughtContent by remember { mutableStateOf(initialText) }
    var holdingBuffer by remember { mutableStateOf(initialText) }

    val slate800 = Color(0xFF1E293B)
    val slate700 = Color(0xFF334155)
    val slate900 = Color(0xFF0F172A)
    val teal700 = Color(0xFF0F766E)
    val rose900 = Color(0xFF4C0519)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, slate700, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = slate800)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isUpdating) {
                Text(
                    text = "Thoughts in progress",
                    fontSize = 14.dp.value.sp,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = holdingBuffer,
                    onValueChange = { holdingBuffer = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(slate900, RoundedCornerShape(8.dp)),
                    placeholder = {
                        Text(
                            text = "Put down whatever is on your mind right now...",
                            color = Color(0xFF64748B)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF1F5F9),
                        unfocusedTextColor = Color(0xFFF1F5F9),
                        focusedBorderColor = Color(0xFF14B8A6),
                        unfocusedBorderColor = slate700
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            thoughtContent = holdingBuffer
                            isUpdating = false
                            onKeepWords(holdingBuffer)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = teal700)
                    ) {
                        Text("Keep this", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            holdingBuffer = thoughtContent
                            isUpdating = false
                        }
                    ) {
                        Text("Leave it for now", color = Color(0xFFCBD5E1))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            thoughtContent = ""
                            holdingBuffer = ""
                            isUpdating = false
                            onLetThisGo()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = rose900)
                    ) {
                        Text("Let this go", color = Color(0xFFFDA4AF))
                    }
                }
            } else {
                if (thoughtContent.isBlank()) {
                    Text(
                        text = "A quiet space for your thoughts. Nothing written down yet.",
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF64748B),
                        fontSize = 15.sp
                    )
                } else {
                    Text(
                        text = thoughtContent,
                        color = Color(0xFFF1F5F9),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = slate700.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            holdingBuffer = thoughtContent
                            isUpdating = true
                        }
                    ) {
                        Text("Change my words", color = Color(0xFFCBD5E1))
                    }

                    if (onShareWhenReady != null && thoughtContent.isNotBlank()) {
                        Button(
                            onClick = { onShareWhenReady(thoughtContent) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1))
                        ) {
                            Text("Share when ready", color = Color(0xFFBAE6FD))
                        }
                    }
                }
            }
        }
    }
}
