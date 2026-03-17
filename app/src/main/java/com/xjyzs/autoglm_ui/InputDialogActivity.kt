package com.xjyzs.autoglm_ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.xjyzs.autoglm_ui.ui.theme.AutoGLMUITheme

class InputDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AutoGLMUITheme {
                InputDialogActivityUI()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        this.sendBroadcast(Intent("ACTION_SHOW_FLOATING"))
    }
}

@Composable
fun InputDialogActivityUI() {
    val context = LocalContext.current
    val text by SharedState.input.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    AlertDialog({
        context.sendBroadcast(Intent("ACTION_SHOW_FLOATING"))
        (context as ComponentActivity).finish()
    }, confirmButton = {
        TextButton({
            context.sendBroadcast(Intent("ACTION_SHOW_FLOATING"))
            (context as ComponentActivity).finish()
        }) {
            Text("确定")
        }
    }, text = {
        OutlinedTextField(
            text, { SharedState.update(it) }, modifier = Modifier.focusRequester(focusRequester)
        )
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    })
}