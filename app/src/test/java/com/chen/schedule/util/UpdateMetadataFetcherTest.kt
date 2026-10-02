package com.chen.schedule.util

import com.chen.schedule.util.update.UpdateEndpointCandidate
import com.chen.schedule.util.update.UpdateMetadataFetcher
import com.chen.schedule.util.update.VersionComparator
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateMetadataFetcherTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun candidate(path: String, isRawVersion: Boolean = true) =
        UpdateEndpointCandidate(path, server.url(path).toString(), isRawVersion)

    private fun fetch(vararg candidates: UpdateEndpointCandidate) =
        UpdateMetadataFetcher(client, "CourseSchedule-Test").fetch(candidates.toList())

    @Test fun emptyObjectFallsBackToValidRelease() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1.3.3","name":"Release"}"""))

        val metadata = fetch(candidate("/mirror"), candidate("/official", false)).getOrThrow()

        assertEquals("v1.3.3", metadata.versionTag)
        assertEquals("/official", metadata.sourceName)
        assertTrue(VersionComparator.isNewer(metadata.versionTag, "1.3.2"))
        assertEquals("/mirror", server.takeRequest().path)
        val officialRequest = server.takeRequest()
        assertEquals("/official", officialRequest.path)
        assertEquals("application/vnd.github.v3+json", officialRequest.getHeader("Accept"))
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedMissingAndInvalidVersionsAllContinueToNextCandidate() {
        val invalidBodies = listOf(
            "{",
            "[]",
            """{"message":"upstream unavailable"}""",
            """{"versionTag":""}""",
            """{"versionTag":123}""",
            """{"tag_name":null}""",
            """{"versionTag":"unknown"}""",
            """{"versionTag":"error.2"}""",
            """{"versionTag":"v1..3"}""",
            """{"versionTag":"v2147483648.0.0"}"""
        )
        invalidBodies.forEach { server.enqueue(MockResponse().setBody(it)) }
        server.enqueue(MockResponse().setBody("""{"versionTag":"1.3.3"}"""))
        val candidates = (invalidBodies.indices.map { candidate("/invalid-$it") } + candidate("/valid"))

        val metadata = fetch(*candidates.toTypedArray()).getOrThrow()

        assertEquals("1.3.3", metadata.versionTag)
        assertEquals("/valid", metadata.sourceName)
        assertEquals(invalidBodies.size + 1, server.requestCount)
        candidates.forEach { assertEquals(it.name, server.takeRequest().path) }
    }

    @Test fun allInvalidCandidatesFailInsteadOfReturningAnUpToDateVersion() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"tag_name":"not-a-version"}"""))

        val result = fetch(candidate("/first"), candidate("/second"))

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("版本号"))
        assertEquals(2, server.requestCount)
    }

    @Test fun validEqualAndOlderReleasesRemainUsableWithoutFallback() {
        for (tag in listOf("v1.3.2", "1.3.1")) {
            server.enqueue(MockResponse().setBody("""{"versionTag":"$tag"}"""))

            val metadata = fetch(candidate("/valid-$tag"), candidate("/must-not-request")).getOrThrow()

            assertEquals(tag, metadata.versionTag)
            assertFalse(VersionComparator.isNewer(metadata.versionTag, "1.3.2"))
            assertEquals("/valid-$tag", server.takeRequest().path)
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun supportedVersionFormsAndGithubAliasRemainAccepted() {
        val tags = listOf("V2.0.0-rc1", "v1.3.3-beta.1+build.7", "1.3.2.1", "1.0")
        tags.forEach { tag ->
            server.enqueue(MockResponse().setBody("""{"versionTag":"","tag_name":" $tag "}"""))

            val metadata = fetch(candidate("/release")).getOrThrow()

            assertEquals(tag, metadata.versionTag)
            assertEquals("/release", server.takeRequest().path)
        }
    }

    @Test fun httpErrorDoesNotPreventTryingAValidBackup() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"versionTag":"v9.0.0"}"""))
        server.enqueue(MockResponse().setBody("""{"versionTag":"v1.3.3"}"""))

        val metadata = fetch(candidate("/unavailable"), candidate("/backup")).getOrThrow()

        assertEquals("/backup", metadata.sourceName)
        assertEquals("v1.3.3", metadata.versionTag)
        assertEquals(2, server.requestCount)
    }
}
