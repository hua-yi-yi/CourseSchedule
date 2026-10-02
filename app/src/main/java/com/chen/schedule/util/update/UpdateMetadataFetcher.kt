package com.chen.schedule.util.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal data class UpdateEndpointCandidate(
    val name: String,
    val url: String,
    val isRawVersion: Boolean
)

internal data class ValidUpdateMetadata(
    val json: JsonObject,
    val versionTag: String,
    val sourceName: String
)

/** A successful HTTP response is usable only if it contains a valid release version. */
internal class UpdateMetadataFetcher(
    private val client: OkHttpClient,
    private val userAgent: String
) {
    fun fetch(candidates: List<UpdateEndpointCandidate>): Result<ValidUpdateMetadata> {
        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                val request = Request.Builder()
                    .url(candidate.url)
                    .header("User-Agent", userAgent)
                    .apply {
                        if (!candidate.isRawVersion) header("Accept", "application/vnd.github.v3+json")
                    }
                    .build()
                val metadata = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("更新服务器返回 HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val json = Json.parseToJsonElement(body) as? JsonObject
                        ?: throw IOException("更新服务器未返回有效的版本信息")
                    val tag = stringField(json, "versionTag").ifBlank { stringField(json, "tag_name") }
                    if (!isValidVersionTag(tag)) throw IOException("更新信息缺少有效的版本号")
                    ValidUpdateMetadata(json, tag, candidate.name)
                }
                return Result.success(metadata)
            } catch (error: Exception) {
                lastError = error
            }
        }
        return Result.failure(lastError ?: IOException("无法连接到更新服务器，请检查网络"))
    }

    private fun stringField(json: JsonObject, name: String): String {
        val field = json[name] ?: return ""
        if (field !is JsonPrimitive || !field.isString) throw IOException("更新信息的版本号格式无效")
        return field.content.trim()
    }

    private fun isValidVersionTag(tag: String): Boolean {
        // Keep the existing supported numeric versions and prerelease/build suffixes,
        // while rejecting partial numeric parses such as "error.2" or integer overflow.
        if (!VERSION_TAG.matches(tag)) return false
        val core = tag.removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        return core.split('.').all { it.toIntOrNull() != null }
    }

    private companion object {
        val VERSION_TAG = Regex("[vV]?[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?(?:\\+[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")
    }
}
