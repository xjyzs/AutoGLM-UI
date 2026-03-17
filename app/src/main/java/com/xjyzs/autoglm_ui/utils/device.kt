package com.xjyzs.autoglm_ui.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import com.xjyzs.autoglm_ui.height
import com.xjyzs.autoglm_ui.width
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.isGone

suspend fun screenshot(context: Context,mFloatingView: View): String {
    println(System.currentTimeMillis())
    context.sendBroadcast(Intent("ACTION_HIDE_FLOATING"))
    waitForViewGone(mFloatingView)
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
        val output = process.inputStream
        val buffer = ByteArray(4096)
        val baos = ByteArrayOutputStream()
        var bytesRead: Int
        while (true) {
            bytesRead = output.read(buffer)
            if (bytesRead <= 0) break
            context.sendBroadcast(Intent("ACTION_SHOW_FLOATING"))
            baos.write(buffer, 0, bytesRead)
        }
        val pngBytes = baos.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
        width = bitmap.width
        height = bitmap.height
        val jpgBaos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpgBaos)
        val jpgBytes = jpgBaos.toByteArray()
        println(System.currentTimeMillis())
        return "data:image/jpeg;base64," + Base64.encodeToString(jpgBytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    context.sendBroadcast(Intent("ACTION_SHOW_FLOATING"))
    return ""
}

fun getCurrentApp(): String {
    val re = Regex("""Window\{.*? u.*? (?<packageName>.*?)/""")
    val process = Runtime.getRuntime().exec(
        arrayOf("su", "-c", "dumpsys window | grep mCurrentFocus")
    )
    val result = process.inputStream.bufferedReader().use {
        it.readText()
    }
    val packageName = re.find(result)?.groups?.get("packageName")?.value
    process.waitFor()
    return getAppName(packageName ?: "系统桌面")
}

suspend fun operation(action: String, args: String,context: Context,mFloatingView: View) {
    when (action) {
        "Launch" -> launch(args)
        "Tap" -> tap(context,args,mFloatingView)
        "Type" -> type(args)
        "Swipe" -> swipe(context,args,mFloatingView)
        "Back" -> back()
        "Home" -> home()
        "Long Press" -> longPress(context,args,mFloatingView)
        "Double Tap" -> doubleTap(context,args,mFloatingView)
        "Wait" -> wait(args)
    }
}

fun launch(args: String) {
    val re=Regex("""app="(?<appName>.*?)"""")
    Runtime.getRuntime().exec(arrayOf("su", "-c", "monkey -p ${getPackageName(re.find(args)!!.groups["appName"]!!.value)} -c android.intent.category.LAUNCHER 1"))
}

suspend fun tap(context: Context, args: String,mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re=Regex("""element=\[(?<x>.*?),(?<y>.*?)]""")
    val x=re.find(args)!!.groups["x"]!!.value.toInt()/1000f*width
    val y=re.find(args)!!.groups["y"]!!.value.toInt()/1000f*height
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
    context.sendBroadcast(Intent("ACTION_DISABLE_TOUCH_THROUGH"))
}

suspend fun type(args: String) {
    val re=Regex("""text="(?<txt>.*?)"""",setOf(RegexOption.DOT_MATCHES_ALL))
    val txt=re.find(args)!!.groups["txt"]!!.value
    Runtime.getRuntime().exec(arrayOf("su", "-c", "am broadcast -a ADB_CLEAR_TEXT"))
    delay(200)
    Runtime.getRuntime().exec(arrayOf("su", "-c", "am broadcast -a ADB_INPUT_B64 --es msg ${Base64.encodeToString(txt.toByteArray(), Base64.NO_WRAP)}"))

}

suspend fun swipe(context: Context,args: String,mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re=Regex("""start=\[(?<x1>.*?),(?<y1>.*?)].*?end=\[(?<x2>.*?),(?<y2>.*?)]""")
    val x1=re.find(args)!!.groups["x1"]!!.value.toInt()/1000f*width
    val y1=re.find(args)!!.groups["y1"]!!.value.toInt()/1000f*height
    val x2=re.find(args)!!.groups["x2"]!!.value.toInt()/1000f*width
    val y2=re.find(args)!!.groups["y2"]!!.value.toInt()/1000f*height
    val dist_sq = (x1 - x2)*(x1 - x2) + (y1 - y2)*(y1 - y2)
    val duration_ms = max(1000f, min(dist_sq / 1000, 2000f)).toLong()
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input swipe $x1 $y1 $x2 $y2 $duration_ms"))
    delay(duration_ms)
    context.sendBroadcast(Intent("ACTION_DISABLE_TOUCH_THROUGH"))
}

fun back() {
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 4"))
}

fun home() {
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent KEYCODE_HOME"))
}

suspend fun longPress(context: Context,args: String,mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re=Regex("""element=\[(?<x>.*?),(?<y>.*?)]""")
    val x=re.find(args)!!.groups["x"]!!.value.toInt()/1000f*width
    val y=re.find(args)!!.groups["y"]!!.value.toInt()/1000f*height
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input swipe $x $y $x $y 3000"))
    delay(3000)
    context.sendBroadcast(Intent("ACTION_DISABLE_TOUCH_THROUGH"))
}

suspend fun doubleTap(context: Context,args: String,mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re=Regex("""element=\[(?<x>.*?),(?<y>.*?)]""")
    val x=re.find(args)!!.groups["x"]!!.value.toInt()/1000f*width
    val y=re.find(args)!!.groups["y"]!!.value.toInt()/1000f*height
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
    delay(200)
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
    context.sendBroadcast(Intent("ACTION_DISABLE_TOUCH_THROUGH"))
}

suspend fun wait(args: String) {
    val re=Regex("""duration="(?<duration>.*?)sec""")
    var tmp = re.find(args)?.groups["duration"]!!.value
    if (tmp.last()==' '){
        tmp= tmp.dropLast(1)
    }
    delay((tmp.toFloat()*1000).toLong())
}

suspend fun waitForTouchThroughEnabled(mFloatingView: View){
    suspendCancellableCoroutine { cont ->
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if ((mFloatingView.layoutParams as WindowManager.LayoutParams).flags== (FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE)) {
                    mFloatingView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    cont.resume(Unit) {}
                }
            }
        }
        mFloatingView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }
}
suspend fun waitForViewGone(mFloatingView: View){
    suspendCancellableCoroutine { cont ->
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (mFloatingView.isGone) {
                    mFloatingView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    cont.resume(Unit) {}
                }
            }
        }
        mFloatingView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }
}