package com.xjyzs.operator.utils

import android.content.Context
import android.view.View
import androidx.compose.runtime.mutableStateOf
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xjyzs.operator.Msg

suspend fun buildUserJson(context: Context, str: String = "", mFloatingView: View): Msg {
    return Msg("user", mutableStateOf(JsonArray().apply {
        val obj = JsonObject()
        obj.addProperty("type", "image_url")
        val subObj = JsonObject().apply { addProperty("url", Screenshot.screenshot(context, mFloatingView)) }
        obj.add("image_url", subObj)
        add(obj)
        val obj2 = JsonObject()
        obj2.addProperty("type", "text")
        obj2.addProperty(
            "text",
            str + "${if (str.isNotEmpty()) "\n\n" else ""}{\"current_app\": \"${getCurrentApp()}\"}"
        )
        add(obj2)
    }))
}