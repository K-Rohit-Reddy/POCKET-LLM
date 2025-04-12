package com.example.llama.component

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.example.llama.R
import com.example.llama.model.ChatHistory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryDrawer(
    histories: List<ChatHistory>,
    onHistorySelected: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    getChatMessages: (String) -> List<String>,
    requestPermissions: (Array<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isDeleting by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    val selectedChats = remember { mutableStateListOf<String>() }
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }

    Log.d("ChatHistoryDrawer", "Rendering drawer with ${histories.size} chat histories")

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                return Velocity.Zero
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Storage Permission Required") },
            text = { Text("Please allow 'All files access' in Settings to save PDFs to Downloads.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
            Row {
                IconButton(onClick = {
                    Log.d("ChatHistoryDrawer", "New chat button clicked")
                    onNewChat()
                }) {
                    Icon(Icons.Filled.Add, "New Chat")
                }
                IconButton(onClick = {
                    if (isDeleting) {
                        if (selectedChats.isNotEmpty()) {
                            Log.d("ChatHistoryDrawer", "Deleting selected chats: $selectedChats")
                            selectedChats.forEach { chatId -> onDeleteChat(chatId) }
                            selectedChats.clear()
                        }
                        isDeleting = false
                    } else {
                        Log.d("ChatHistoryDrawer", "Entering delete mode")
                        isDeleting = true
                        isDownloading = false
                    }
                }) {
                    if (isDeleting) {
                        Icon(Icons.Filled.Check, "Confirm Delete", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Delete, "Delete Chats", tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = {
                    if (isDownloading) {
                        if (selectedChats.isNotEmpty()) {
                            Log.d("ChatHistoryDrawer", "Downloading selected chats: $selectedChats")
                            val hasLegacyPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            val hasManagePermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || Environment.isExternalStorageManager()
                            Log.d("ChatHistoryDrawer", "Legacy permission: $hasLegacyPermission, Manage permission: $hasManagePermission")
                            if (hasLegacyPermission || hasManagePermission) {
                                selectedChats.forEach { chatId ->
                                    val messages = getChatMessages(chatId)
                                    generateChatPdf(context, chatId, messages)
                                }
                                selectedChats.clear()
                                isDownloading = false
                            } else {
                                Log.d("ChatHistoryDrawer", "Permissions not granted, requesting")
                                requestPermissions(arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ))
                                showPermissionDialog = true
                            }
                        } else {
                            Log.d("ChatHistoryDrawer", "No chats selected, exiting download mode")
                            isDownloading = false
                        }
                    } else {
                        Log.d("ChatHistoryDrawer", "Entering download mode")
                        isDownloading = true
                        isDeleting = false
                    }
                }) {
                    if (isDownloading) {
                        Icon(Icons.Filled.Check, "Confirm Download", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Download, "Download Chats", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (histories.isEmpty()) {
                item {
                    Log.d("ChatHistoryDrawer", "No chat histories to display")
                    Text(
                        text = "No chat history yet",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = ComposeColor.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(histories) { history ->
                    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDeleting || isDownloading) {
                                Checkbox(
                                    checked = selectedChats.contains(history.id),
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            selectedChats.add(history.id)
                                            Log.d("ChatHistoryDrawer", "Selected chat: ${history.id}")
                                        } else {
                                            selectedChats.remove(history.id)
                                            Log.d("ChatHistoryDrawer", "Deselected chat: ${history.id}")
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !isDeleting && !isDownloading) {
                                        if (!isDeleting && !isDownloading) {
                                            Log.d("ChatHistoryDrawer", "Selected chat: ${history.id}")
                                            onHistorySelected(history.id)
                                        }
                                    }
                            ) {
                                Text(text = history.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = dateFormat.format(history.date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ComposeColor.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun generateChatPdf(context: android.content.Context, chatId: String, messages: List<String>) {
    val hasLegacyPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    val hasManagePermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    if (!hasLegacyPermission && !hasManagePermission) {
        Log.e("ChatHistoryDrawer", "Storage permission not granted in generateChatPdf")
        return
    }

    val document = PdfDocument()
    var pageNumber = 1
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
    var page = document.startPage(pageInfo)
    var canvas = page.canvas
    val paint = Paint()

    // Border settings
    val borderMargin = 30f
    val contentWidth = pageInfo.pageWidth - 2 * borderMargin
    val contentHeight = pageInfo.pageHeight - 2 * borderMargin
    val borderRect = RectF(borderMargin, borderMargin, borderMargin + contentWidth, borderMargin + contentHeight)

    // Header settings
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()) // Explicit 24-hour format
    val currentDate = dateFormat.format(Date())
    var yPosition = borderMargin + 170f // Below header

    fun drawPageHeader(canvas: Canvas) {
        // Draw date in a curved rectangle (top-left) only on the first page
        if (pageNumber == 1) {
            paint.color = Color.parseColor("#EADDFF") // Same light purple as page number
            paint.style = Paint.Style.FILL
            val dateText = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
            paint.textSize = 14f
            paint.typeface = Typeface.SERIF
            val textWidth = paint.measureText(dateText)
            val rectWidth = textWidth + 20f // Extra padding
            val rectHeight = 30f
            val dateRect = RectF(
                borderMargin + 10f,
                borderMargin,
                borderMargin + 10f + rectWidth,
                borderMargin + rectHeight
            )
            canvas.drawRoundRect(dateRect, 10f, 10f, paint) // Curved rectangle with 10f corner radius

            paint.color = Color.parseColor("#6750A4")
            canvas.drawText(dateText, dateRect.left + 10f, dateRect.top + 20f, paint)
        }

        // Draw header icon (top-right) on every page
        try {
            val svg = SVG.getFromResource(context.resources, R.raw.header_icon)
            val bitmap = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888)
            val svgCanvas = Canvas(bitmap)
            svg.setDocumentWidth(250f)
            svg.setDocumentHeight(250f)
            svg.renderToCanvas(svgCanvas)
            canvas.drawBitmap(bitmap, borderMargin + contentWidth - 210f, borderMargin - 100f, paint)
            bitmap.recycle()
            Log.d("ChatHistoryDrawer", "SVG header icon drawn successfully")
        } catch (e: Exception) {
            Log.e("ChatHistoryDrawer", "Error loading SVG: ${e.message}")
            paint.textSize = 12f
            canvas.drawText("Header Image Missing", borderMargin + contentWidth - 150f, borderMargin + 10f, paint)
        }
    }

    fun drawPageNumber(canvas: Canvas, pageNum: Int) {
        paint.color = Color.parseColor("#EADDFF") // Light purple circle
        paint.style = Paint.Style.FILL
        val boxWidth = 40f
        val boxHeight = 40f
        val boxRect = RectF(
            (contentWidth / 2) - (boxWidth / 2) + borderMargin,
            pageInfo.pageHeight - boxHeight - 20f,
            (contentWidth / 2) + (boxWidth / 2) + borderMargin,
            pageInfo.pageHeight - 20f
        )
        canvas.drawOval(boxRect, paint)

        paint.color = Color.parseColor("#6750A4")
        paint.textSize = 14f
        paint.typeface = Typeface.SERIF // Readable font for page number
        val text = "$pageNum"
        val textWidth = paint.measureText(text)
        canvas.drawText(text, boxRect.left + (boxWidth - textWidth) / 2, boxRect.top + (boxHeight / 2) + 5f, paint)
    }

    drawPageHeader(canvas)

    // Bubble settings
    val bubblePadding = 15f // Increased padding to 15f for more space inside the bubble
    val bubbleRadius = 15f
    val maxBubbleWidth = 400f // Fixed maximum bubble width to 300f to match ChatScreen

    // Function to wrap text within bubble width
    fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth - 2 * bubblePadding) {
                currentLine = StringBuilder(testLine)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

// Draw messages sequentially with only 24-hour time
    paint.textSize = 10f // Set initial font size
    paint.typeface = Typeface.SERIF // Decent readable font for text
    paint.style = Paint.Style.FILL
    messages.forEachIndexed { index, message ->
        val timestamp = timeFormat.format(Date(System.currentTimeMillis() + index * 60000)) // Only 24-hour time
        // Dynamically detect sender and content
        val parts = message.split(":", limit = 2) // Split on first colon only
        val sender = if (parts.size > 1) parts[0].trim() else "Unknown"
        val cleanMessage = if (parts.size > 1) parts[1].trim() else message
        val isUserMessage = sender.toLowerCase() != "assistant" && sender.toLowerCase() != "lara" // Assume non-AI names are users

        val lines = wrapText(cleanMessage, paint, maxBubbleWidth - 2 * bubblePadding) // Adjusted for new padding
        val textHeight = lines.size * 20f
        val bubbleHeight = textHeight + 2 * bubblePadding + 20f // Extra for timestamp
        val bubbleWidth = (listOf(timestamp, sender) + lines).maxOf { paint.measureText(it) } + 2 * bubblePadding // Adjusted for new padding

        if (yPosition + bubbleHeight + 30f > borderMargin + contentHeight - 50f) {
            drawPageNumber(canvas, pageNumber)
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvas = page.canvas
            paint.textSize = 10f // Reapply font size for new page
            drawPageHeader(canvas)
            yPosition = borderMargin + 170f
        }

        if (isUserMessage) {
            // User bubble (right side)
            paint.color = Color.parseColor("#F1F8E9") // Light green
            val bubbleRect = RectF(
                borderMargin + contentWidth - bubbleWidth - 20f,
                yPosition,
                borderMargin + contentWidth - 20f,
                yPosition + bubbleHeight
            )
            canvas.drawRoundRect(bubbleRect, bubbleRadius, bubbleRadius, paint)

            // Tail (left side)
            val tailPath = Path()
            tailPath.moveTo(bubbleRect.left + bubbleRadius, bubbleRect.bottom - bubbleRadius)
            tailPath.lineTo(bubbleRect.left - 10f, bubbleRect.bottom)
            tailPath.lineTo(bubbleRect.left + bubbleRadius, bubbleRect.bottom)
            tailPath.close()
            canvas.drawPath(tailPath, paint)

            // Timestamp and text
            paint.color = Color.GRAY
            canvas.drawText(timestamp, bubbleRect.left + bubblePadding, yPosition + bubblePadding + 12f, paint) // Only 24-hour time
            paint.color = Color.BLACK
            lines.forEachIndexed { i, line ->
                canvas.drawText(line, bubbleRect.left + bubblePadding, yPosition + bubblePadding + 32f + i * 20f, paint)
            }
        } else {
            // AI bubble (left side)
            paint.color = Color.parseColor("#E0F7FA") // Light cyan
            val bubbleRect = RectF(
                borderMargin + 20f,
                yPosition,
                borderMargin + 20f + bubbleWidth,
                yPosition + bubbleHeight
            )
            canvas.drawRoundRect(bubbleRect, bubbleRadius, bubbleRadius, paint)

            // Tail (right side)
            val tailPath = Path()
            tailPath.moveTo(bubbleRect.right - bubbleRadius, bubbleRect.bottom - bubbleRadius)
            tailPath.lineTo(bubbleRect.right + 10f, bubbleRect.bottom)
            tailPath.lineTo(bubbleRect.right - bubbleRadius, bubbleRect.bottom)
            tailPath.close()
            canvas.drawPath(tailPath, paint)

            // Timestamp and text
            paint.color = Color.GRAY
            canvas.drawText(timestamp, bubbleRect.left + bubblePadding, yPosition + bubblePadding + 12f, paint) // Only 24-hour time
            paint.color = Color.BLACK
            lines.forEachIndexed { i, line ->
                canvas.drawText(line, bubbleRect.left + bubblePadding, yPosition + bubblePadding + 32f + i * 20f, paint)
            }
        }
        yPosition += bubbleHeight + 30f
    }

    // Draw page number for the last page
    drawPageNumber(canvas, pageNumber)

    document.finishPage(page)

    // Save PDF to Downloads directory
    val file = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Chat_$chatId.pdf"
    )
    var fos: FileOutputStream? = null
    try {
        fos = FileOutputStream(file)
        document.writeTo(fos)
        fos.flush()
        Log.d("ChatHistoryDrawer", "PDF saved: ${file.absolutePath}")
    } catch (e: Exception) {
        Log.e("ChatHistoryDrawer", "Error saving PDF: ${e.message}")
    } finally {
        fos?.close()
        document.close()
    }
}
