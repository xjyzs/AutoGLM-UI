package com.xjyzs.operator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjyzs.operator.ui.theme.OperatorTheme

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OperatorTheme {
                Surface {
                    WelcomeUI()
                }
            }
        }
    }
}

@Composable
fun WelcomeUI() {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_launcher), null)
            Text("欢迎使用 AutoGLM-UI", fontSize = 32.sp)
            Text("让 AI 直接操作你的手机", color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(40.dp))
            Button({
                context.startActivity(Intent(context, ConfigActivity::class.java))
                (context as ComponentActivity).finish()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开始配置 API ")
                    Icon(Icons.Default.ArrowUpward, null, Modifier.rotate(90f))
                }
            }
        }
    }
}