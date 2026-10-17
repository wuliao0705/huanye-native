package com.wuliao.huanye

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃捕获：任何未捕获异常写入 /sdcard/Android/data/<pkg>/files/crash.txt
 * 同时弹一个原生对话框显示错误摘要（不依赖 Compose）。
 * @author 无聊
 */
object CrashHandler {

    fun install(app: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        val dir: File = app.getExternalFilesDir(null) ?: File(app.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "crash.txt")

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                file.appendText(
                    "\n===== $ts =====\n" +
                    "Thread: ${t.name}\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    sw.toString()
                )
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    /** 读取最近崩溃记录（给"我的"页展示） */
    fun readRecent(ctx: Context): String {
        val dir: File = ctx.getExternalFilesDir(null) ?: File(ctx.filesDir, "logs")
        val f = File(dir, "crash.txt")
        if (!f.exists()) return ""
        val txt = f.readText()
        return if (txt.length > 4000) txt.takeLast(4000) else txt
    }

    fun clear(ctx: Context) {
        val dir: File = ctx.getExternalFilesDir(null) ?: File(ctx.filesDir, "logs")
        File(dir, "crash.txt").delete()
    }
}
