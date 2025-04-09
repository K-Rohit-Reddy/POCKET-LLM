package com.example.llama

import android.app.DownloadManager
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.database.getLongOrNull
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.io.File

data class Downloadable(val name: String, val source: Uri, val destination: File) {
    companion object {
        private val TAG: String = Downloadable::class.java.simpleName

        sealed interface State {
            data object Ready : State
            data class Downloading(val id: Long) : State
            data class Downloaded(val downloadable: Downloadable) : State
            data class Error(val message: String) : State
        }

        @Composable
        fun CustomProgressBar(
            progress: Float,
            modifier: Modifier = Modifier
        ) {
            val customGrey = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // Even darker grey
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        color = customGrey,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    .clip(MaterialTheme.shapes.extraLarge)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                )
                Text(
                    text = "Downloading...",
                    color = Color.Black, // Black for better visibility
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                )
            }
        }

        @Composable
        fun DownloadButton(
            viewModel: MainViewModel,
            downloadManager: DownloadManager,
            item: Downloadable,
            onDownloadComplete: (String) -> Unit = {}
        ) {
            var status by remember {
                mutableStateOf<State>(
                    if (item.destination.exists()) State.Downloaded(item) else State.Ready
                )
            }
            var progress by remember { mutableDoubleStateOf(0.0) }

            LaunchedEffect(status) {
                if (status is State.Downloading) {
                    val downloading = status as State.Downloading
                    while (true) {
                        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloading.id))
                        if (cursor == null) {
                            Log.e(TAG, "DownloadManager query returned null")
                            status = State.Error("DownloadManager query failed")
                            return@LaunchedEffect
                        }
                        if (!cursor.moveToFirst() || cursor.count < 1) {
                            cursor.close()
                            Log.i(TAG, "Download canceled or not found")
                            status = State.Ready
                            return@LaunchedEffect
                        }
                        val bytesDownloaded = cursor.getLongOrNull(
                            cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        ) ?: 0L
                        val totalBytes = cursor.getLongOrNull(
                            cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        ) ?: 1L
                        cursor.close()
                        progress = (bytesDownloaded * 1.0) / totalBytes
                        if (bytesDownloaded == totalBytes) {
                            status = State.Downloaded(item)
                            return@LaunchedEffect
                        }
                        delay(1000L)
                    }
                }
            }

            Column {
                // Conditionally render the button only when not downloading
                if (status !is State.Downloading) {
                    Button(
                        onClick = {
                            when (val currentStatus = status) {
                                is State.Downloaded -> {
                                    viewModel.load(item.destination.path)
                                    onDownloadComplete(item.destination.path)
                                }
                                is State.Downloading -> {
                                    // Do nothing while downloading
                                }
                                else -> {
                                    item.destination.delete()
                                    val request = DownloadManager.Request(item.source).apply {
                                        setTitle("Downloading ${item.name}")
                                        setDescription("Model: ${item.name}")
                                        setAllowedNetworkTypes(
                                            DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                                        )
                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationUri(item.destination.toUri())
                                    }
                                    viewModel.log("Saving ${item.name} to ${item.destination.path}")
                                    val downloadId = downloadManager.enqueue(request)
                                    status = State.Downloading(downloadId)
                                }
                            }
                        },
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            when (status) {
                                is State.Downloaded -> "Load ${item.name}"
                                is State.Ready -> "Download ${item.name}"
                                is State.Error -> "Retry ${item.name}"
                                is State.Downloading -> ""
                            }
                        )
                    }
                }
                if (status is State.Downloading) {
                    CustomProgressBar(
                        progress = progress.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}