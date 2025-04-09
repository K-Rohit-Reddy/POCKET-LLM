package com.example.llama

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llama.model.ChatHistory
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException // Added import
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel : ViewModel() {
    val userName = mutableStateOf("User")
    val assistantName = mutableStateOf("Assistant")
    val message = mutableStateOf("")
    val messages = mutableStateListOf<String>()
    val chatHistories = mutableStateListOf<ChatHistory>()
    val modelPath = mutableStateOf("")
    val importError = mutableStateOf("")
    val isLoadingModel = mutableStateOf(false)
    private lateinit var historyFile: File
    private val llama = LLamaAndroid.instance().apply {
        setContextSize(8192) // 8K context size
    }
    var isGenerating = mutableStateOf(false)
    private var sendJob: Job? = null

    fun loadPrefs(prefs: SharedPreferences) {
        userName.value = prefs.getString("userName", "User") ?: "User"
        assistantName.value = prefs.getString("assistantName", "Assistant") ?: "Assistant"
        modelPath.value = prefs.getString("modelPath", "") ?: ""
        historyFile = File(prefs.getString("historyDir", ""), "chat_history.json")
        loadHistory()
        llama.initialize(userName.value, assistantName.value)
        if (modelPath.value.isNotEmpty()) {
            load(modelPath.value)
        }
    }

    fun savePrefs(prefs: SharedPreferences) {
        with(prefs.edit()) {
            putString("userName", userName.value)
            putString("assistantName", assistantName.value)
            putString("modelPath", modelPath.value)
            putBoolean("isFirstLaunch", false)
            putString("historyDir", historyFile.parent)
            apply()
        }
        llama.initialize(userName.value, assistantName.value)
    }

    private fun loadHistory() {
        if (historyFile.exists()) {
            val json = historyFile.readText()
            chatHistories.addAll(Json.decodeFromString<List<ChatHistory>>(json))
        }
    }

    fun saveCurrentChat() {
        if (messages.isNotEmpty()) {
            val chat = ChatHistory(
                id = UUID.randomUUID().toString(),
                title = "Chat ${chatHistories.size + 1} - ${SimpleDateFormat("MMM dd").format(Date())}",
                date = Date(),
                messages = messages.toList()
            )
            chatHistories.add(chat)
            try {
                historyFile.writeText(Json.encodeToString(chatHistories.toList()))
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to save chat history: ${e.message}")
            }
            messages.clear()
        }
    }

    fun loadChat(chatId: String) {
        chatHistories.find { it.id == chatId }?.let {
            messages.clear()
            messages.addAll(it.messages)
            Log.d("MainViewModel", "Loaded chat with ID: $chatId, Messages: ${it.messages}")
        }
    }

    fun deleteChat(chatId: String) {
        chatHistories.removeIf { it.id == chatId }
        try {
            historyFile.writeText(Json.encodeToString(chatHistories.toList()))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to delete chat history: ${e.message}")
        }
    }

    fun setModelPath(path: String) {
        if (!path.endsWith(".gguf")) {
            importError.value = "Please select a .gguf file"
            return
        }
        isLoadingModel.value = true
        importError.value = ""
        viewModelScope.launch {
            try {
                modelPath.value = path
                llama.load(path)
                Log.d("MainViewModel", "Successfully loaded model from: $path")
            } catch (e: Exception) {
                importError.value = "Failed to load model: ${e.message}"
                Log.e("MainViewModel", "Model load error: ${e.stackTraceToString()}")
            } finally {
                isLoadingModel.value = false
            }
        }
    }

    fun startNewChat() {
        saveCurrentChat()
        messages.clear()
        sendJob?.cancel()
        isGenerating.value = false
        // Clear any cached context
        viewModelScope.launch {
            llama.unload()
            if (modelPath.value.isNotEmpty()) {
                llama.load(modelPath.value)
            }
        }
        Log.d("MainViewModel", "Started new chat with full state reset")
    }

    fun send(prompt: String = message.value.trim()) {
        if (prompt.isBlank()) return
        isGenerating.value = true
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Sending prompt: $prompt")
                if (modelPath.value.isEmpty()) {
                    messages.add("${assistantName.value}: No model loaded. Please load a model from Settings.")
                    isGenerating.value = false
                    return@launch
                }

                val responseBuilder = StringBuilder()
                // Add initial assistant message marker
                messages.add("${assistantName.value}: ")
                
                llama.send(prompt).collect { partialResponse ->
                    responseBuilder.append(partialResponse)
                    val currentResponse = responseBuilder.toString().trim()
                    
                    // Only update if we have meaningful content
                    if (currentResponse.isNotEmpty() && 
                        !currentResponse.endsWith("...") && // Skip interim "..."
                        currentResponse != messages.lastOrNull()?.removePrefix("${assistantName.value}: ")?.trim()) {
                        
                        // Format response with proper spacing and line breaks
                        val formattedResponse = currentResponse
                            .replace("\n\n", "\n") // Normalize line breaks
                            .replace(Regex(" {2,}"), " ") // Normalize spaces
                        
                        if (messages.lastOrNull()?.startsWith("${assistantName.value}:") == true) {
                            messages[messages.lastIndex] = "${assistantName.value}: $formattedResponse"
                        } else {
                            messages.add("${assistantName.value}: $formattedResponse")
                        }
                    }
                }

                val fullResponse = responseBuilder.toString().trim()
                if (fullResponse.isNotEmpty() && messages.lastOrNull() != "${assistantName.value}: $fullResponse") {
                    messages[messages.lastIndex] = "${assistantName.value}: $fullResponse"
                }
                Log.d("MainViewModel", "Response generation completed for prompt: $prompt")
            } catch (e: Exception) {
                if (e !is CancellationException) { // Avoid adding cancellation errors
                    messages.add("${assistantName.value}: Error: ${e.message}")
                    Log.e("MainViewModel", "Send error: ${e.stackTraceToString()}")
                }
            } finally {
                isGenerating.value = false
                sendJob = null
            }
        }
    }

    fun pause() {
        sendJob?.cancel()
        isGenerating.value = false
        // Remove any incomplete assistant message
        messages.removeAll { 
            it.startsWith("${assistantName.value}:") && 
            (it.length < 10 || it.endsWith("..."))
        }
        Log.d("MainViewModel", "Paused and cleaned partial responses")
    }

    fun log(message: String) {
        Log.d("MainViewModel", message)
    }

    fun load(path: String) {
        setModelPath(path)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            llama.unload()
        }
    }
}