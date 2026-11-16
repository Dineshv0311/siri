package com.siri.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.siri.assistant.service.AssistantForegroundService
import com.siri.assistant.service.AssistantNotificationListenerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("siri_assistant_prefs", Context.MODE_PRIVATE) }

    var geminiKey by remember { mutableStateOf(viewModel.engine.geminiRepository.getApiKey()) }
    var porcupineKey by remember {
        mutableStateOf(prefs.getString("porcupine_access_key", "").orEmpty())
    }
    var alwaysOnEnabled by remember {
        mutableStateOf(prefs.getBoolean("always_on_service", false))
    }

    Scaffold(
        containerColor = Color(0xFF0B0B0E),
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0B0E))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: AI Configuration
            SettingsSectionCard(title = "AI & NLU Engine") {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = {
                        geminiKey = it
                        viewModel.engine.geminiRepository.setApiKey(it)
                    },
                    label = { Text("Gemini API Key", color = Color.Gray) },
                    placeholder = { Text("AIzaSy...", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF2E2E38)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Powers general knowledge Q&A and advanced natural language intent understanding.",
                    color = Color(0xFF757575),
                    fontSize = 11.sp
                )
            }

            // Section: Wake Word Engine
            SettingsSectionCard(title = "Wake-Word Detection") {
                OutlinedTextField(
                    value = porcupineKey,
                    onValueChange = {
                        porcupineKey = it
                        prefs.edit().putString("porcupine_access_key", it.trim()).apply()
                    },
                    label = { Text("Picovoice Porcupine AccessKey", color = Color.Gray) },
                    placeholder = { Text("Enter AccessKey...", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF2E2E38)
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Always-On Background Wake-Word", color = Color.White, fontSize = 14.sp)
                        Text(
                            "Keeps assistant listening for \"Hey Siri\" while phone is in standby.",
                            color = Color(0xFF757575),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = alwaysOnEnabled,
                        onCheckedChange = { checked ->
                            alwaysOnEnabled = checked
                            prefs.edit().putBoolean("always_on_service", checked).apply()
                            viewModel.toggleForegroundService(checked)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                    )
                }
            }

            // Section: Android System Integration
            SettingsSectionCard(title = "System Assistant & Permissions") {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Set as Default Digital Assistant", color = Color(0xFF80D8FF))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Enable Notification Listener Access", color = Color(0xFF80D8FF))
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Permission Status Checklist:", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow("Microphone (Audio Record)", Manifest.permission.RECORD_AUDIO)
                PermissionRow("Read Contacts", Manifest.permission.READ_CONTACTS)
                PermissionRow("Direct Call (CALL_PHONE)", Manifest.permission.CALL_PHONE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow("Notifications", Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Section: Maintenance
            SettingsSectionCard(title = "History & Reset") {
                Button(
                    onClick = { viewModel.clearHistory() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E1C1C)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear Conversation History", color = Color(0xFFFF8A80))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF16161B))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
fun PermissionRow(name: String, permission: String) {
    val context = LocalContext.current
    val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = Color(0xFFCCCCCC), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isGranted) "Granted" else "Missing",
                color = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252),
                fontSize = 12.sp
            )
        }
    }
}
