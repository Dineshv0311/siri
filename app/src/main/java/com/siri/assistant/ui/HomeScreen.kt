package com.siri.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siri.assistant.assistant.AssistantState
import com.siri.assistant.data.database.ConversationEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateSettings: () -> Unit
) {
    val state by viewModel.assistantState.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val history by viewModel.conversationHistory.collectAsState()
    val lastSpoken by viewModel.lastSpokenText.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom of conversation
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0B0B0E),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state) {
                                        AssistantState.LISTENING -> Color(0xFF00E5FF)
                                        AssistantState.PROCESSING, AssistantState.EXECUTING -> Color(0xFFFFD600)
                                        AssistantState.SPEAKING -> Color(0xFFFF4081)
                                        AssistantState.ERROR -> Color(0xFFFF5252)
                                        AssistantState.STANDBY -> Color(0xFF757575)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Siri Assistant",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0B0E)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Orb Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AssistantOrb(
                    state = state,
                    rmsDb = rmsDb,
                    onClick = { viewModel.onOrbClick() }
                )
            }

            // State caption
            Text(
                text = when (state) {
                    AssistantState.STANDBY -> "Tap orb or say \"Hey Siri\""
                    AssistantState.LISTENING -> "Listening..."
                    AssistantState.PROCESSING -> "Thinking..."
                    AssistantState.EXECUTING -> "Executing action..."
                    AssistantState.SPEAKING -> "Speaking..."
                    AssistantState.ERROR -> "Something went wrong"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = when (state) {
                    AssistantState.LISTENING -> Color(0xFF00E5FF)
                    AssistantState.PROCESSING, AssistantState.EXECUTING -> Color(0xFFFFD600)
                    AssistantState.SPEAKING -> Color(0xFFFF4081)
                    else -> Color(0xFF9E9E9E)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick suggestion chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "Call Mom",
                    "Open YouTube",
                    "Turn on flashlight",
                    "Set alarm for 7 AM",
                    "What's the weather today?",
                    "Read my notifications"
                )
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = { viewModel.submitTextCommand(suggestion) },
                        label = { Text(suggestion, color = Color.LightGray, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF18181C)
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = Color(0xFF282830)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conversation transcript
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141418))
                    .border(1.dp, Color(0xFF22222A), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Conversation transcript will appear here.\nSpeak or type a command to begin.",
                            color = Color(0xFF616161),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { message ->
                            MessageBubble(message)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type a voice command...", color = Color.Gray, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF18181C),
                        unfocusedContainerColor = Color(0xFF18181C),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF2A2A35)
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.submitTextCommand(textInput)
                            textInput = ""
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.submitTextCommand(textInput)
                            textInput = ""
                        } else {
                            viewModel.onOrbClick()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF202028))
                ) {
                    Icon(
                        imageVector = if (textInput.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                        contentDescription = "Submit",
                        tint = if (state == AssistantState.LISTENING) Color(0xFF00E5FF) else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ConversationEntity) {
    val isUser = message.sender == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isUser) Color(0xFF2979FF) else Color(0xFF222228)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                text = message.message,
                color = Color.White,
                fontSize = 14.sp
            )
            if (!message.actionType.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ACTION: ${message.actionType}",
                    color = Color(0xFF80D8FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
