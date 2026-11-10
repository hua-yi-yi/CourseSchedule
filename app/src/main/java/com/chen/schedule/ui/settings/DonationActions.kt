package com.chen.schedule.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.chen.schedule.R

internal fun saveQrCodeToGallery(context: Context, showToast: Boolean = true): Boolean {
    val resolver = context.contentResolver
    var insertedUri: Uri? = null
    try {
        val filename = "CourseSchedule_Donation_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CourseSchedule")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("无法创建相册文件")
        insertedUri = uri
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.qrcode_donation)
            ?: error("无法加载收款码图片")
        try {
            val output = resolver.openOutputStream(uri) ?: error("无法写入相册")
            output.use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) { "无法保存收款码图片" } }
        } finally {
            bitmap.recycle()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(resolver.update(uri, published, null, null) == 1) { "无法在相册中发布图片" }
        }
        if (showToast) {
            Toast.makeText(context, "收款码已保存到相册，可在微信扫一扫中识别", Toast.LENGTH_LONG).show()
        }
        return true
    } catch (e: Exception) {
        insertedUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        return false
    }
}

internal fun openWeChat(context: android.content.Context) {
    try {
        // 方案 1: 优先尝试通过微信 LauncherUI 携带「扫一扫」快捷标记直达扫一扫页面
        val scanIntent = Intent().apply {
            component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            putExtra("LauncherUI.From.Scaner.Shortcut", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(scanIntent)
    } catch (e: Exception) {
        // 方案 2: 若直接启动扫一扫组件受限或报错，回退为常规启动微信客户端（依赖 AndroidManifest 中的 <queries> 声明）
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "未检测到微信客户端，请安装微信或手动打开扫一扫识别", Toast.LENGTH_SHORT).show()
            }
        } catch (ex: Exception) {
            Toast.makeText(context, "打开微信失败: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
