package com.chen.schedule.island

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 静态图文模板：官方模板库 2026-01-29 的 baseInfo(type=2)、picInfo(type=1)
 * 及摘要态 imageTextInfoLeft(type=1) + textInfo。未使用 timerInfo/progressInfo。
 */
internal object XiaomiCourseNotificationPayload {
    const val ICON_KEY = "miui.focus.pic_course"

    fun create(
        state: IslandState,
        protocol: Int,
        business: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        if (protocol !in setOf(2, 3) || business.isBlank()) return null
        val content = when (state) {
            is IslandState.Upcoming -> Content("候课", "即将上课", state.courseName, state.classroom, state.startTime, state.endTime, state.startMillis)
            is IslandState.Ongoing -> Content("上课", "正在上课", state.courseName, state.classroom, state.startTime, state.endTime, state.endMillis)
            else -> return null
        }
        if (content.expiresMillis <= nowMillis ||
            content.expiresMillis - nowMillis > 12 * 60 * 60_000L) return null
        val remaining = content.expiresMillis - nowMillis
        val seconds = ((remaining + 999) / 1000).coerceIn(1, Int.MAX_VALUE.toLong())
        val minutes = ((remaining + 59_999) / 60_000).coerceIn(1, Int.MAX_VALUE.toLong())
        val place = content.classroom.ifBlank { "地点待定" }
        val times = "${content.startTime}–${content.endTime}"
        return buildJsonObject {
            putJsonObject("param_v2") {
                // protocol=1 是 param_v2 载荷版本，不是 notification_focus_protocol ROM 版本。
                put("protocol", 1)
                put("business", business)
                put("updatable", true)
                put("reopen", "reopen")
                put("enableFloat", false)
                put("islandFirstFloat", false)
                put("filterWhenNoPermission", false)
                put("timeout", minutes)
                put("ticker", "${content.status} · ${content.courseName}")
                put("aodTitle", "${content.status} · ${content.courseName} · $times")
                putJsonObject("baseInfo") {
                    put("type", 2)
                    put("title", content.courseName)
                    put("content", "${content.status} · $place")
                    put("subContent", times)
                }
                putJsonObject("picInfo") {
                    put("type", 1)
                    put("pic", ICON_KEY)
                }
                if (protocol == 3) putJsonObject("param_island") {
                    put("islandProperty", 1)
                    put("islandOrder", false)
                    put("islandTimeout", seconds)
                    putJsonObject("bigIslandArea") {
                        putJsonObject("imageTextInfoLeft") {
                            put("type", 1)
                            putJsonObject("picInfo") {
                                put("type", 1)
                                put("pic", ICON_KEY)
                            }
                            putJsonObject("textInfo") {
                                put("title", content.shortStatus)
                                put("showHighlightColor", false)
                            }
                        }
                        putJsonObject("textInfo") {
                            put("title", if (state is IslandState.Upcoming) content.startTime else content.endTime)
                            put("content", if (state is IslandState.Upcoming) "上课" else "下课")
                            put("narrowFont", true)
                            put("showHighlightColor", false)
                        }
                    }
                    putJsonObject("smallIslandArea") {
                        putJsonObject("picInfo") {
                            put("type", 1)
                            put("pic", ICON_KEY)
                        }
                    }
                }
            }
        }.toString()
    }

    private data class Content(
        val shortStatus: String,
        val status: String,
        val courseName: String,
        val classroom: String,
        val startTime: String,
        val endTime: String,
        val expiresMillis: Long
    )
}
