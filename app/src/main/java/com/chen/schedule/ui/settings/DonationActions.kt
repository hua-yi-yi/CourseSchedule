package com.chen.schedule.ui.settings

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.chen.schedule.R

internal fun saveQrCodeToGallery(context: android.content.Context, showToast: Boolean = true): Boolean {
    try {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.qrcode_donation)
        if (bitmap == null) {
            Toast.makeText(context, "无法加载收款码图片", Toast.LENGTH_SHORT).show()
            return false
        }
        val filename = "CourseSchedule_Donation_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CourseSchedule")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            if (showToast) {
                Toast.makeText(context, "收款码已保存到相册，可在微信扫一扫中识别", Toast.LENGTH_LONG).show()
            }
            return true
        } else {
            Toast.makeText(context, "保存图片失败", Toast.LENGTH_SHORT).show()
            return false
        }
    } catch (e: Exception) {
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
