package com.chen.schedule.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 旧版完整备份没有 backupVersion，但始终包含 semester 对象。 */
object ScheduleBackupFormat {
    fun isFullBackup(element: JsonElement): Boolean =
        element is JsonObject &&
            ("backupVersion" in element || element["semester"] is JsonObject)
}
