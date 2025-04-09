package com.example.llama.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.example.llama.model.ChatHistory
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity // Added missing import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryDrawer(
    histories: List<ChatHistory>,
    onHistorySelected: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity { // Resolved with import
                return Velocity.Zero // Resolved with import
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .nestedScroll(nestedScrollConnection)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat History",
                style = MaterialTheme.typography.headlineSmall
            )
            IconButton(onClick = onNewChat) {
                Icon(Icons.Filled.Add, "New Chat")
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(histories) { history ->
                val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .swipeToDismiss(
                            onDismissed = { showDeleteDialog = history.id }
                        ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = history.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = dateFormat.format(history.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                // Handle click separately with clickable modifier
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHistorySelected(history.id) }
                )
            }
        }

        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Chat") },
                text = { Text("Are you sure you want to delete this chat?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog?.let { onDeleteChat(it) }
                        showDeleteDialog = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// Custom swipe-to-dismiss modifier (simplified placeholder)
fun Modifier.swipeToDismiss(
    onDismissed: () -> Unit
): Modifier {
    return this.then(
        Modifier
            .offset { IntOffset.Zero }
            .pointerInput(Unit) {
                // Placeholder: Proper swipe detection requires Accompanist or full gesture handling
                // For now, this is a stub; replace with actual swipe logic if needed
            }
    )
}