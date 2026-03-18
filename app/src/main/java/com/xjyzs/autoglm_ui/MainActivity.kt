package com.xjyzs.autoglm_ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xjyzs.autoglm_ui.ui.theme.AutoGLMUITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoGLMUITheme {
                MainUI()
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
        lateinit var mediaPlayer: MediaPlayer
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.kpalv) // keep alive
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        mediaPlayer.start()
                    }
                }
            }
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            mediaPlayer.apply {
                isLooping = true
                setVolume(0.01f, 0.01f)
                start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainUI() {
    val context = LocalContext.current
    var overlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var rootPermission by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermission = Settings.canDrawOverlays(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(Unit) {
        try {
            Runtime.getRuntime().exec("su")
        } catch (_: Exception) {
            rootPermission = false
        }
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
    }
    if (!isIgnoringBatteryOptimizations) {
        AlertDialog(
            {},
            {
                TextButton({
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = "package:${context.packageName}".toUri()
                    context.startActivity(intent)
                    isIgnoringBatteryOptimizations = true
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton({
                    isIgnoringBatteryOptimizations = true
                }) { Text("取消") }
            },
            title = { Text("忽略电池优化") },
            text = { Text("由于本应用涉及后台操作，你需要忽略电池优化") })
    }
    if (!overlayPermission) {
        AlertDialog(
            {},
            confirmButton = { TextButton({ requestOverlayPermission(context) }) { Text("确定") } },
            title = { Text("${stringResource(R.string.app_name)} 申请获取悬浮窗权限") },
            text = { Text("在弹窗中选择允许后，你可以与 AI 交互。") })
    }
    if (!rootPermission) {
        AlertDialog(
            {},
            confirmButton = {
                TextButton({
                    try {
                        Runtime.getRuntime().exec("su")
                        rootPermission = true
                    } catch (_: Exception) {
                    }
                }) { Text("确定") }
            },
            title = { Text("${stringResource(R.string.app_name)} 申请获取 root 权限") },
            text = { Text("允许后，你可以与 AI 交互。") })
    }
    LaunchedEffect(overlayPermission) {
        if (overlayPermission) {
            ContextCompat.startForegroundService(
                context, Intent(context, FloatingWindowService::class.java)
            )
        }

    }
    Scaffold() { paddingValues ->
        Button({
            val intent = Intent(context, ConfigActivity::class.java)
            context.startActivity(intent)
            context.stopService(Intent(context, FloatingWindowService::class.java))
            (context as ComponentActivity).finish()
        }, Modifier.padding(paddingValues)) { Text("ConfigUI") }
    }

}

fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()
    )
    context.startActivity(intent)
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}