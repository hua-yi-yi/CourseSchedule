package com.chen.schedule.util.update

import android.content.Context
import android.content.SharedPreferences

/**
 * 版本更新与镜像设置持久化。
 */
class UpdatePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    /** 是否在打开应用或进入设置时自动检测更新 (默认开启) */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()

    /** 是否启用 GitHub 镜像加速 (默认开启) */
    var useMirror: Boolean
        get() = prefs.getBoolean(KEY_USE_MIRROR, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_MIRROR, value).apply()

    /** 选中的镜像节点 ID */
    var selectedMirrorId: String
        get() = prefs.getString(KEY_SELECTED_MIRROR, GithubMirror.GHFAST.id) ?: GithubMirror.GHFAST.id
        set(value) = prefs.edit().putString(KEY_SELECTED_MIRROR, value).apply()

    /** 选中的镜像枚举 */
    var selectedMirror: GithubMirror
        get() = GithubMirror.fromId(selectedMirrorId)
        set(value) {
            selectedMirrorId = value.id
        }

    /** 上次检测时间戳 */
    var lastCheckTime: Long
        get() = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK_TIME, value).apply()

    companion object {
        private const val KEY_AUTO_CHECK = "auto_check_update"
        private const val KEY_USE_MIRROR = "use_mirror"
        private const val KEY_SELECTED_MIRROR = "selected_mirror"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
    }
}
