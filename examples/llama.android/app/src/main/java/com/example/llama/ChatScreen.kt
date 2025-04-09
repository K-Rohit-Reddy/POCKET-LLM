package com.example.llama

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import android.content.ClipboardManager
import com.example.llama.component.ChatHistoryDrawer
import com.example.llama.component.MessageBubble
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavHostController,
    viewModel: MainViewModel,
    clipboardManager: ClipboardManager
) {
    val scrollState = rememberLazyListState()
    val isGenerating by viewModel.isGenerating
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val currentDate = SimpleDateFormat("MMM dd, yyyy").format(Date())

    // Animation for loading dots
    val infiniteTransition = rememberInfiniteTransition()
    val dotOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Initial welcome message with names
    LaunchedEffect(Unit) {
        if (viewModel.messages.isEmpty()) {
            val welcomeMessage = "${viewModel.assistantName.value}: Hello, ${viewModel.userName.value}! I'm here to assist you. How can I help you today?"
            viewModel.messages.add(welcomeMessage)
        }
    }

    // Scroll to the latest message
    // Track when new content is being generated
    LaunchedEffect(viewModel.isGenerating.value, viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            scope.launch {
                try {
                    // Immediate jump to bottom when new message starts
                    scrollState.scrollToItem(viewModel.messages.size - 1)
                    
                    // If generating, continuously check and scroll as content grows
                    if (viewModel.isGenerating.value) {
                        while (viewModel.isGenerating.value) {
                            delay(100) // Check every 100ms
                            
                            val layoutInfo = scrollState.layoutInfo
                            val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
                            
                            if (lastItem != null && lastItem.index == viewModel.messages.size - 1) {
                                val viewportHeight = layoutInfo.viewportEndOffset
                                val itemBottom = lastItem.offset + lastItem.size
                                
                                if (itemBottom > viewportHeight) {
                                    scrollState.animateScrollToItem(
                                        viewModel.messages.size - 1,
                                        scrollOffset = 0
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("ChatScreen", "Scroll error: ${e.message}")
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistoryDrawer(
                histories = viewModel.chatHistories,
                onHistorySelected = { chatId ->
                    viewModel.loadChat(chatId)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onDeleteChat = { viewModel.deleteChat(it) },
                modifier = Modifier.fillMaxHeight()
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Chat - $currentDate",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(4.dp)
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.History, "History")
                        }
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.messages) { message ->
                        val isUserMessage = message.startsWith("${viewModel.userName.value}:")
                        val displayText = if (isUserMessage) message.removePrefix("${viewModel.userName.value}:") else message.removePrefix("${viewModel.assistantName.value}:")
                        val isLoading = !isUserMessage && message.endsWith(": ") && isGenerating
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            MessageBubble(
                                text = if (isLoading) "..." else displayText,
                                isUser = isUserMessage,
                                modifier = Modifier
                                    .widthIn(max = 300.dp)
                                    .padding(horizontal = 8.dp),
                                isLoading = isLoading,
                                dotOffset = if (isLoading) dotOffset else 0f
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = viewModel.message.value,
                        onValueChange = { viewModel.message.value = it },
                        label = { Text("Message", fontSize = 14.sp) },
                        textStyle = TextStyle(fontSize = 14.sp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        singleLine = false,
                        maxLines = 3,
                        enabled = !isGenerating
                    )
                    if (isGenerating) {
                        IconButton(
                            onClick = { viewModel.pause() },
                            enabled = true,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = "Pause",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (viewModel.message.value.isNotBlank()) {
                                    viewModel.isGenerating.value = true
                                    val userMessage = "${viewModel.userName.value}: ${viewModel.message.value}"
                                    viewModel.messages.add(userMessage)
                                    viewModel.send(viewModel.message.value)
                                    viewModel.message.value = ""
                                }
                            },
                            enabled = !isGenerating && viewModel.message.value.isNotBlank(),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}