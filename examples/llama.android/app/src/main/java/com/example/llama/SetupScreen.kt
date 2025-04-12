package com.example.llama

import android.app.DownloadManager
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.llama.component.ModelImportCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    navController: NavHostController,
    viewModel: MainViewModel,
    downloadManager: DownloadManager,
    modelOptions: List<Downloadable>,
    isFirstLaunch: Boolean,
    prefs: SharedPreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var copyProgress by remember { mutableStateOf(0f) } // Progress from 0.0 to 1.0

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                try {
                    // Get file name from Uri
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) cursor.getString(displayNameIndex) else null
                        } else null
                    } ?: uri.toString().substringAfterLast('/').takeIf { it.isNotEmpty() }
                    ?: "default_model.gguf"

                    if (fileName.endsWith(".gguf", ignoreCase = true)) {
                        isLoading = true
                        scope.launch {
                            try {
                                // Get file size for progress
                                val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                                val internalFile = File(context.filesDir, fileName)

                                // Copy file asynchronously with progress
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                        FileOutputStream(internalFile).use { outputStream ->
                                            val buffer = ByteArray(8192) // 8 KB buffer
                                            var bytesCopied = 0L
                                            var bytesRead: Int
                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                outputStream.write(buffer, 0, bytesRead)
                                                bytesCopied += bytesRead
                                                if (fileSize > 0) {
                                                    copyProgress = bytesCopied.toFloat() / fileSize
                                                }
                                            }
                                        }
                                    } ?: throw Exception("Failed to open input stream from Uri")
                                }

                                Log.d("SetupScreen", "File copied to internal storage: ${internalFile.absolutePath}")

                                // Load the model
                                viewModel.setModelPath(internalFile.absolutePath)
                                while (viewModel.isLoadingModel.value) {
                                    kotlinx.coroutines.delay(100)
                                }
                                if (viewModel.importError.value.isEmpty()) {
                                    viewModel.savePrefs(prefs)
                                    Log.d("SetupScreen", "Model loaded successfully, navigating to chat")
                                    navController.navigate("chat") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                } else {
                                    Log.e("SetupScreen", "Model load failed: ${viewModel.importError.value}")
                                }
                            } catch (e: Exception) {
                                viewModel.importError.value = "Error copying/loading file: ${e.message}"
                                Log.e("SetupScreen", "Error in process: ${e.stackTraceToString()}")
                            } finally {
                                isLoading = false
                                copyProgress = 0f
                            }
                        }
                    } else {
                        viewModel.importError.value = "Please select a .gguf file"
                        Log.d("SetupScreen", "Invalid file selected: $fileName")
                    }
                } catch (e: Exception) {
                    viewModel.importError.value = "Error initiating file copy: ${e.message}"
                    Log.e("SetupScreen", "Initial error: ${e.stackTraceToString()}")
                }
            } ?: run {
                viewModel.importError.value = "No file selected"
                Log.d("SetupScreen", "File picker returned null URI")
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column {
            Text(
                text = "Welcome!",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Let's set up your AI Assistant",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        var isEditingName by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = viewModel.userName.value,
            onValueChange = { newValue -> viewModel.userName.value = newValue },
            label = { Text("Your Name") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "User"
                )
            },
            trailingIcon = {
                if (isEditingName) {
                    IconButton(
                        onClick = {
                            isEditingName = false
                            viewModel.savePrefs(prefs)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(
                        onClick = { isEditingName = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            readOnly = !isEditingName
        )

        Spacer(modifier = Modifier.height(16.dp))

        var isEditingAssistant by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = viewModel.assistantName.value,
            onValueChange = { newValue -> viewModel.assistantName.value = newValue },
            label = { Text("Assistant Name") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Assistant"
                )
            },
            trailingIcon = {
                if (isEditingAssistant) {
                    IconButton(
                        onClick = {
                            isEditingAssistant = false
                            viewModel.savePrefs(prefs)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(
                        onClick = { isEditingAssistant = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            readOnly = !isEditingAssistant
        )

        Spacer(modifier = Modifier.height(24.dp))

        ModelImportCard(
            onImportClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Or download a predefined model:",
            style = MaterialTheme.typography.titleMedium
        )

        modelOptions.forEach { model ->
            Downloadable.DownloadButton(
                viewModel = viewModel,
                downloadManager = downloadManager,
                item = model,
                onDownloadComplete = {
                    viewModel.savePrefs(prefs)
                    navController.navigate("chat") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }

        // Progress bar for file copying
        if (isLoading && copyProgress > 0f && copyProgress < 1f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Copying file... ${(copyProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = copyProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }

        // Loading indicator for model loading
        if (isLoading && copyProgress >= 1f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 16.dp)
            )
            Text(
                text = "Loading model...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (viewModel.importError.value.isNotEmpty()) {
            Text(
                text = viewModel.importError.value,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
