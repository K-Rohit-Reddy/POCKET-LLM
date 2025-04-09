package com.example.llama

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.getSystemService
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.llama.ui.theme.LlamaAndroidTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val downloadManager by lazy { getSystemService<DownloadManager>()!! }
    private val clipboardManager by lazy { getSystemService<ClipboardManager>()!! }
    private val activityManager by lazy { getSystemService<ActivityManager>()!! }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            viewModel.log("Storage permissions granted")
        } else {
            viewModel.log("Storage permissions denied")
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        val requiredPermissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        requiredPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            storagePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )

        val free = Formatter.formatFileSize(this, availableMemory().availMem)
        val total = Formatter.formatFileSize(this, availableMemory().totalMem)
        viewModel.log("Current memory: $free / $total")

        val extFilesDir = getExternalFilesDir(null)
        val models = listOf(
            Downloadable(
                "Orca Mini 3B",
                Uri.parse("https://huggingface.co/Aryanne/Orca-Mini-3B-gguf/resolve/main/q4_0-orca-mini-3b.gguf?download=true"),
                File(extFilesDir, "q4_0-orca-mini-3b.gguf")
            )
        )

        val prefs = getSharedPreferences("LlamaPrefs", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        viewModel.loadPrefs(prefs)

        setContent {
            LlamaAndroidTheme {
                AppNavigation(
                    viewModel = viewModel,
                    downloadManager = downloadManager,
                    clipboardManager = clipboardManager,
                    models = models,
                    isFirstLaunch = isFirstLaunch,
                    prefs = prefs
                )
            }
        }
    }

    private fun availableMemory(): ActivityManager.MemoryInfo {
        return ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    downloadManager: DownloadManager,
    clipboardManager: ClipboardManager,
    models: List<Downloadable>,
    isFirstLaunch: Boolean,
    prefs: android.content.SharedPreferences
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (isFirstLaunch) "setup" else "chat"
    ) {
        composable("setup") {
            SetupScreen(
                navController = navController,
                viewModel = viewModel,
                downloadManager = downloadManager,
                modelOptions = models,
                isFirstLaunch = true,
                prefs = prefs
            )
        }
        composable("chat") {
            ChatScreen(
                navController = navController,
                viewModel = viewModel,
                clipboardManager = clipboardManager
            )
        }
        composable("settings") {
            SettingsScreen(
                navController = navController,
                viewModel = viewModel,
                downloadManager = downloadManager,
                modelOptions = models,
                prefs = prefs
            )
        }
    }
}