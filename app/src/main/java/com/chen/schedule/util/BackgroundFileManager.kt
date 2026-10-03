package com.chen.schedule.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 自定义背景与小组件背景文件安全持久化与图像采样解码工具。
 *
 * 核心设计：
 * 1. 本地持久化：从 Uri 复制并压缩到内部私有目录，防止系统重启后 content:// 权限失效。
 * 2. 内存防爆 (OOM 保护)：先读 bounds 计算采样比，大图智能下采样。
 * 3. 屏幕方向校准：根据 EXIF orientation 自动纠正手机拍摄照片旋转角度。
 * 4. 小组件 Binder 保护：限制小组件壁纸物理分辨率至 720px 以内，杜绝 RemoteViews 1MB 事务超限崩溃。
 */
object BackgroundFileManager {

    private const val DIR_NAME = "backgrounds"
    const val APP_BG_FILENAME = "app_bg.jpg"
    const val WIDGET_BG_FILENAME = "widget_bg.jpg"

    private const val MAX_APP_WIDTH = 1440
    private const val MAX_APP_HEIGHT = 2560
    private const val MAX_WIDGET_WIDTH = 720
    private const val MAX_WIDGET_HEIGHT = 720

    /**
     * 从 Content Uri 复制并缩放保存自定义背景图到应用私有目录。
     * @return 成功返回保存后的绝对文件路径，失败返回 null。
     */
    fun saveBackgroundFromUri(context: Context, uri: Uri, isWidget: Boolean = false): String? {
        return try {
            val maxW = if (isWidget) MAX_WIDGET_WIDTH else MAX_APP_WIDTH
            val maxH = if (isWidget) MAX_WIDGET_HEIGHT else MAX_APP_HEIGHT
            val targetFilename = if (isWidget) WIDGET_BG_FILENAME else APP_BG_FILENAME

            // 1. 获取图片尺寸
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // 2. 计算采样率
            options.inSampleSize = calculateInSampleSize(options, maxW, maxH)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // 3. 解码 Bitmap
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // 4. 校准 EXIF 方向
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val orientedBitmap = applyExifOrientation(rawBitmap, orientation)

            // 5. 若仍超出尺寸则精确缩放
            val finalBitmap = scaleIfNeeded(orientedBitmap, maxW, maxH)

            // 6. 写入内部存储
            val bgDir = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
            val destFile = File(bgDir, targetFilename)
            FileOutputStream(destFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, if (isWidget) 82 else 88, out)
                out.flush()
            }

            if (finalBitmap != orientedBitmap && !orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
            if (orientedBitmap != rawBitmap && !rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 清除保存的背景图。
     */
    fun clearBackground(context: Context, isWidget: Boolean = false): Boolean {
        return try {
            val targetFilename = if (isWidget) WIDGET_BG_FILENAME else APP_BG_FILENAME
            val bgDir = File(context.filesDir, DIR_NAME)
            val file = File(bgDir, targetFilename)
            if (file.exists()) file.delete() else true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检查背景图文件是否存在。
     */
    fun backgroundExists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    /**
     * 采样加载 Bitmap，供应用主界面或预览使用。
     */
    fun loadBitmap(path: String?, maxWidth: Int = MAX_APP_WIDTH, maxHeight: Int = MAX_APP_HEIGHT): Bitmap? {
        if (!backgroundExists(path)) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 为小组件专门优化的紧凑位图加载，严控尺寸与内存。
     */
    fun loadWidgetBitmap(path: String?, maxWidth: Int = 600, maxHeight: Int = 600): Bitmap? {
        if (!backgroundExists(path)) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            val decoded = BitmapFactory.decodeFile(path, options) ?: return null
            scaleIfNeeded(decoded, maxWidth, maxHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun scaleIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxWidth && h <= maxHeight) return bitmap

        val ratio = min(maxWidth.toFloat() / w, maxHeight.toFloat() / h)
        val targetW = max(1, (w * ratio).toInt())
        val targetH = max(1, (h * ratio).toInt())

        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled != bitmap) bitmap.recycle()
            scaled
        } catch (_: Exception) {
            bitmap
        }
    }
}
