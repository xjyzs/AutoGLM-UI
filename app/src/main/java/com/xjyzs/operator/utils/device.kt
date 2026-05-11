package com.xjyzs.operator.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import androidx.core.graphics.scale
import androidx.core.view.isGone
import com.xjyzs.operator.height
import com.xjyzs.operator.width
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.sync.Mutex

object Screenshot {
    init {
        System.loadLibrary("native-lib")
    }

    private external fun scaleImageJNI(
        srcArray: ByteArray, srcW: Int, srcH: Int,
        dstArray: ByteArray, dstW: Int, dstH: Int
    )

    private var suProcess: Process? = null
    private var os: OutputStream? = null
    private var bis: BufferedInputStream? = null

    // 内存池复用，避免 GC 卡顿
    private var rawBuffer = ByteArray(0)
    private var scaledBuffer = ByteArray(0)
    private val mutex = Mutex()

    private fun initProcess() {
        if (suProcess == null) {
            suProcess = Runtime.getRuntime().exec("su")
            os = suProcess!!.outputStream
            bis = BufferedInputStream(suProcess!!.inputStream, 8 * 1024 * 1024)
        }
    }

    suspend fun screenshot(context: Context, mFloatingView: View): String {
        // 1. 发送广播隐藏悬浮窗，并等待消失
//        context.sendBroadcast(Intent("ACTION_HIDE_FLOATING"))
//        waitForViewGone(mFloatingView) // 假定你的这部分是挂起函数或阻塞逻辑
        mFloatingView.translationX += 114514f

        var success = false
        var width = 0
        var height = 0
        var pixelCount = 0

        // 2. 在 IO 线程执行截屏获取内存数据
        withContext(Dispatchers.IO) {
            try {
                initProcess()

                while ((bis?.available() ?: 0) > 0) {
                    bis?.read()
                }

                // 发送截屏指令 (获取 Raw Data)
                println("开始截屏")
                os?.write("screencap 2>/dev/null\n".toByteArray())
                os?.flush()
                println("截屏指令已发送")


                // 读取 Header
                val headerSize = if (Build.VERSION.SDK_INT >= 29) 16 else 12
                val header = ByteArray(headerSize)
                var readHeader = 0
                while (readHeader < 12) {
                    val r = bis?.read(header, readHeader, 12 - readHeader) ?: -1
                    if (r == -1) throw Exception("读取Header失败")
                    readHeader += r
                }
                println("Header已读取")

                val headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                width = headerBuffer.int
                height = headerBuffer.int
                headerBuffer.int // format

                // 读取像素数据
                pixelCount = width * height * 4
                if (rawBuffer.size < pixelCount) {
                    rawBuffer = ByteArray(pixelCount)
                }
                println("开始读取像素数据")

                var readTotal = 0
                while (readTotal < pixelCount) {
                    val read = bis?.read(rawBuffer, readTotal, pixelCount - readTotal) ?: -1
                    if (read == -1) break
                    readTotal += read
                }

                if (readTotal == pixelCount) success = true
                println("结束IO")
            } catch (e: Exception) {
                e.printStackTrace()
                suProcess?.destroy()
                suProcess = null
                os = null
                bis = null
            }
        }
        println("截图结束")
        // 3. ★ 核心优化：像素数据一旦到手，立刻恢复悬浮窗！不等待后续处理！ ★
        //context.sendBroadcast(Intent("ACTION_SHOW_FLOATING"))
        mFloatingView.translationX -= 114514f
        println("已显示")

        if (!success) return ""

        // 4. 继续在后台线程处理图像和 Base64 编码
        return withContext(Dispatchers.Default) {
            try {
                val targetShortEdge = 720
                val shortEdge = min(width, height)
                val targetW: Int
                val targetH: Int

                if (shortEdge > targetShortEdge) {
                    val ratio = targetShortEdge.toFloat() / shortEdge
                    targetW = (width * ratio).toInt()
                    targetH = (height * ratio).toInt()
                } else {
                    targetW = width
                    targetH = height
                }

                val finalBitmap = createBitmap(targetW, targetH)

                // 使用 JNI 执行 C++ 极速缩放
                if (targetW != width || targetH != height) {
                    val scaledPixelCount = targetW * targetH * 4
                    if (scaledBuffer.size < scaledPixelCount) {
                        scaledBuffer = ByteArray(scaledPixelCount)
                    }
                    scaleImageJNI(rawBuffer, width, height, scaledBuffer, targetW, targetH)
                    finalBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(scaledBuffer, 0, scaledPixelCount))
                } else {
                    finalBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rawBuffer, 0, pixelCount))
                }

                // JPEG压缩与Base64
                val baos = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                finalBitmap.recycle()

                return@withContext "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext ""
            }
        }
    }
}

suspend fun screenshot(context: Context, mFloatingView: View): String {
    context.sendBroadcast(Intent("ACTION_HIDE_FLOATING"))
    waitForViewGone(mFloatingView)
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
        val output = process.inputStream
        val buffer = ByteArray(4096)
        val baos = ByteArrayOutputStream()
        var bytesRead: Int
        var hasShown=false
        while (true) {
            bytesRead = output.read(buffer)
            if (bytesRead <= 0) break
            if (!hasShown) {
                context.sendBroadcast(Intent("ACTION_SHOW_FLOATING"))
                hasShown=true
            }
            baos.write(buffer, 0, bytesRead)
        }


        val pngBytes = baos.toByteArray()
        val originalBitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)

        width = originalBitmap.width
        height = originalBitmap.height

        val targetShortEdge = 720
        val shortEdge = min(originalBitmap.width, originalBitmap.height)

        val scaledBitmap = if (shortEdge > targetShortEdge) {
            val ratio = targetShortEdge.toFloat() / shortEdge
            val targetWidth = (originalBitmap.width * ratio).toInt()
            val targetHeight = (originalBitmap.height * ratio).toInt()
            originalBitmap.scale(targetWidth, targetHeight)
        } else {
            originalBitmap
        }
        if (scaledBitmap != originalBitmap) {
            originalBitmap.recycle()
        }
        val jpgBaos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, jpgBaos)
        val jpgBytes = jpgBaos.toByteArray()
        scaledBitmap.recycle()
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

suspend fun operation(action: String, args: String, context: Context, mFloatingView: View) {
    try {
        when (action) {
            "Launch" -> launch(args)
            "Tap" -> tap(context, args, mFloatingView)
            "Type" -> type(args)
            "Swipe" -> swipe(context, args, mFloatingView)
            "Back" -> back()
            "Home" -> home()
            "Long Press" -> longPress(context, args, mFloatingView)
            "Double Tap" -> doubleTap(context, args, mFloatingView)
            "Wait" -> wait(args)
        }
    } catch (_: Exception) {
    }
}

fun launch(args: String) {
    val re = Regex("""app="(?<appName>.*?)"""")
    Runtime.getRuntime().exec(
        arrayOf(
            "su",
            "-c",
            "monkey -p ${getPackageName(re.find(args)!!.groups["appName"]!!.value)} -c android.intent.category.LAUNCHER 1"
        )
    )
}

suspend fun tap(context: Context, args: String, mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re = Regex("""\[\s*(?<x>\d+)\s*,\s*(?<y>\d+)\s*]""")
    val x = re.find(args)!!.groups["x"]!!.value.toInt() / 1000f * width
    val y = re.find(args)!!.groups["y"]!!.value.toInt() / 1000f * height
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
    context.sendBroadcast(Intent("ACTION_DISABLE_TOUCH_THROUGH"))
}

suspend fun type(args: String) {
    val re = Regex("""text="(?<txt>.*?)"""", setOf(RegexOption.DOT_MATCHES_ALL))
    val txt = re.find(args)!!.groups["txt"]!!.value
    Runtime.getRuntime().exec(arrayOf("su", "-c", "am broadcast -a ADB_CLEAR_TEXT"))
    delay(200)
    Runtime.getRuntime().exec(
        arrayOf(
            "su",
            "-c",
            "am broadcast -a ADB_INPUT_B64 --es msg ${
                Base64.encodeToString(
                    txt.toByteArray(),
                    Base64.NO_WRAP
                )
            }"
        )
    )

}

suspend fun swipe(context: Context, args: String, mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re = Regex("""\[\s*(?<x1>\d+)\s*,\s*(?<y1>\d+)\s*].*?\[\s*(?<x2>\d+)\s*,\s*(?<y2>\d+)\s*]""")
    val x1 = re.find(args)!!.groups["x1"]!!.value.toInt() / 1000f * width
    val y1 = re.find(args)!!.groups["y1"]!!.value.toInt() / 1000f * height
    val x2 = re.find(args)!!.groups["x2"]!!.value.toInt() / 1000f * width
    val y2 = re.find(args)!!.groups["y2"]!!.value.toInt() / 1000f * height
    val dist_sq = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
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

suspend fun longPress(context: Context, args: String, mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re = Regex("""\[\s*(?<x>\d+)\s*,\s*(?<y>\d+)\s*]""")
    val x = re.find(args)!!.groups["x"]!!.value.toInt() / 1000f * width
    val y = re.find(args)!!.groups["y"]!!.value.toInt() / 1000f * height
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input swipe $x $y $x $y 3000"))
    delay(3000)
    context.sendBroadcast(Intent("ACTION_DISABLE_TOUCH_THROUGH"))
}

suspend fun doubleTap(context: Context, args: String, mFloatingView: View) {
    context.sendBroadcast(Intent("ACTION_ENABLE_TOUCH_THROUGH"))
    waitForTouchThroughEnabled(mFloatingView)
    val re = Regex("""\[\s*(?<x>\d+)\s*,\s*(?<y>\d+)\s*]""")
    val x = re.find(args)!!.groups["x"]!!.value.toInt() / 1000f * width
    val y = re.find(args)!!.groups["y"]!!.value.toInt() / 1000f * height
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
    delay(200)
    Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
    context.sendBroadcast(Intent("ACTION_DISABLE_TOUCH_THROUGH"))
}

suspend fun wait(args: String) {
    val re = Regex("""duration="(?<duration>.*?)sec""")
    var tmp = re.find(args)?.groups["duration"]!!.value
    if (tmp.last() == ' ') {
        tmp = tmp.dropLast(1)
    }
    delay((tmp.toFloat() * 1000).toLong())
}

suspend fun waitForTouchThroughEnabled(mFloatingView: View) {
    suspendCancellableCoroutine { cont ->
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if ((mFloatingView.layoutParams as WindowManager.LayoutParams).flags == (FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE)) {
                    mFloatingView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    cont.resume(Unit) {}
                }
            }
        }
        mFloatingView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }
}

suspend fun waitForViewGone(mFloatingView: View) {
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