package com.xjyzs.operator

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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xjyzs.operator.ui.theme.OperatorTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OperatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) { MainUI() }
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
                focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainUI() {
    val context = LocalContext.current
    var overlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var rootPermission by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }
    val apiPref = context.getSharedPreferences("api", Context.MODE_PRIVATE)
    val imeLst = remember { mutableStateListOf<String>() }
    val pref = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    val historyLst = remember { mutableStateListOf<String>() }
    val newMsg by SharedState.newMsg.collectAsStateWithLifecycle()

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
    LaunchedEffect(newMsg) {
        if (newMsg.isEmpty()) {
            val historyStr = pref.getString("history", "[]")!!
            for (i in JsonParser.parseString(historyStr).asJsonArray) {
                historyLst.add(i.asString)
            }
        } else {
            historyLst.add(newMsg)
            pref.edit { putString("history", Gson().toJson(historyLst).toString()) }
        }
    }
    LaunchedEffect(Unit) {
        try {
            Runtime.getRuntime().exec("su")
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ime list -a -s"))
                val reader =
                    BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
                val outputBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    outputBuilder.append(line).append("\n")
                }
                val output = outputBuilder.toString()
                val list = output.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                imeLst.clear()
                imeLst.addAll(list)
                Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "ime enable com.android.adbkeyboard/.AdbIME"))
            } catch (_: Exception) {
            }
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
        if (apiPref.getString("apiUrl", "")!!.isNotEmpty()) {
            if (overlayPermission) {
                ContextCompat.startForegroundService(
                    context, Intent(context, FloatingWindowService::class.java)
                )
            }
        } else {
            context.startActivity(Intent(context, WelcomeActivity::class.java))
            (context as ComponentActivity).finish()
        }

    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer, topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) }, actions = {
                    IconButton(
                        {
                            val intent = Intent(context, ConfigActivity::class.java)
                            context.startActivity(intent)
                            context.stopService(Intent(context, FloatingWindowService::class.java))
                            (context as ComponentActivity).finish()
                        }, colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                        )
                    ) { Icon(Icons.Default.Settings, null) }
                }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        }, modifier = Modifier.padding(horizontal = 8.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置输入法", fontSize = 22.sp, modifier = Modifier.padding(start = 10.dp))
                TextButton({
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/senzhk/ADBKeyBoard/releases".toUri()
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "未找到浏览器", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("下载 ADB Keyboard", fontSize = 16.sp) }
            }
            Spacer(Modifier.size(6.dp))
            for (i in imeLst) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            Runtime.getRuntime().exec(arrayOf("su", "-c", "ime set $i"))
                        }
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(12.dp, 10.dp)) {
                    Text(i, fontSize = 16.sp)
                }
                Spacer(Modifier.size(6.dp))
            }
            Spacer(Modifier.size(6.dp))
            Text("历史记录", fontSize = 22.sp, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.size(6.dp))
            for (i in 0..<historyLst.size) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            SharedState.update(historyLst[historyLst.size - 1 - i])
                        }
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(12.dp, 10.dp)) {
                    Text(historyLst[historyLst.size - 1 - i], fontSize = 16.sp)
                }
                Spacer(Modifier.size(6.dp))
            }
        }
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