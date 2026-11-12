package com.chen.schedule.util.log

import com.chen.schedule.util.DiagnosticCourseItem
import com.chen.schedule.util.DiagnosticPayload
import com.chen.schedule.util.DiagnosticReporter
import com.chen.schedule.util.ImportSnapshot
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sanitizer masks credentials and PII while keeping timestamps intact`() {
        val raw = """
            POST /default2.aspx?xh=2024150899&TextBox2=MySecretPwd123&password=abc987
            Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
            Cookie: ASP.NET_SessionId=xyz123456
            学生信息: 姓名=王小明 手机号=13987654321 身份证=41030520040506123X 学号=202514510844
            正常时间戳: 2026-11-12 14:30:05.123 epoch=1762934400123 date=20261112
        """.trimIndent()

        val cleaned = LogSanitizer.sanitize(raw)
        assertFalse(cleaned.contains("MySecretPwd123"))
        assertFalse(cleaned.contains("abc987"))
        assertFalse(cleaned.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(cleaned.contains("xyz123456"))
        assertFalse(cleaned.contains("王小明"))
        assertFalse(cleaned.contains("13987654321"))
        assertFalse(cleaned.contains("41030520040506123X"))
        assertFalse(cleaned.contains("2024150899"))
        assertFalse(cleaned.contains("202514510844"))

        // 正常日期与毫秒时间戳不应被误伤
        assertTrue(cleaned.contains("2026-11-12 14:30:05.123"))
        assertTrue(cleaned.contains("1762934400123"))
        assertTrue(cleaned.contains("20261112"))
    }

    @Test
    fun `codec formats and parses multiline entries and stack traces round-trip`() {
        val t1 = 1762934405123L
        val e1 = LogEntry(
            timestamp = t1,
            formattedTime = LogCodec.formatTimestamp(t1),
            level = LogLevel.INFO,
            tag = "AppStartup",
            message = "应用初始化完成\n多行说明第二行"
        )
        val e2 = LogEntry(
            timestamp = t1 + 500,
            formattedTime = LogCodec.formatTimestamp(t1 + 500),
            level = LogLevel.ERROR,
            tag = "ZhengfangScraper",
            message = "获取课表失败: HTTP 502",
            stackTrace = "java.io.IOException: Bad Gateway\n\tat com.chen.schedule.Scraper.fetch(Scraper.kt:42)"
        )

        val exported = LogCodec.buildExportDocument(
            entries = listOf(e1, e2),
            metadata = linkedMapOf("App版本" to "1.3.15 (64)", "设备型号" to "Xiaomi 14")
        )

        val importedDoc = LogCodec.parseExternalLog("exported.log", exported)
        assertEquals("应用标准日志", importedDoc.formatLabel)
        assertEquals("1.3.15 (64)", importedDoc.metadata["App版本"])
        assertEquals(2, importedDoc.entries.size)
        assertEquals(1, importedDoc.errorAndCrashCount)

        assertEquals("应用初始化完成\n多行说明第二行", importedDoc.entries[0].message)
        assertNull(importedDoc.entries[0].stackTrace)

        assertEquals(LogLevel.ERROR, importedDoc.entries[1].level)
        assertEquals("ZhengfangScraper", importedDoc.entries[1].tag)
        assertNotNull(importedDoc.entries[1].stackTrace)
        assertTrue(importedDoc.entries[1].stackTrace!!.contains("Bad Gateway"))
    }

    @Test
    fun `codec parses external logcat, JSON diagnostic reports, and markdown reports`() {
        // 1. Android Logcat threadtime
        val logcatSample = """
            11-12 14:30:05.123  2048  2048 I MainActivity: Schedule rendered
            11-12 14:30:06.456  2048  2100 E AndroidRuntime: FATAL EXCEPTION: main
            11-12 14:30:06.456  2048  2100 E AndroidRuntime:   at com.chen.schedule.MainActivity.onCreate(MainActivity.kt:30)
        """.trimIndent()
        val logcatDoc = LogCodec.parseExternalLog("logcat.txt", logcatSample)
        assertEquals("Android Logcat 日志", logcatDoc.formatLabel)
        assertEquals(2, logcatDoc.entries.size)
        assertEquals(LogLevel.ERROR, logcatDoc.entries[1].level)
        assertTrue(logcatDoc.entries[1].stackTrace.orEmpty().contains("MainActivity.onCreate"))

        // 2. ImportSnapshot JSON
        val snapshot = ImportSnapshot(
            source = "河科大教务",
            semesterName = "2026-2027 第一学期",
            totalWeeks = 18,
            courseCount = 1,
            error = "部分周次未识别",
            rawSnippet = "<table><tr><td>操作系统</td></tr></table>",
            courses = listOf(
                DiagnosticCourseItem("操作系统", "陈老师", "西苑201", 3, 1, 2, 1, 16, "ALL")
            )
        )
        val snapJson = kotlinx.serialization.json.Json.encodeToString(ImportSnapshot.serializer(), snapshot)
        val snapDoc = LogCodec.parseExternalLog("snapshot.json", snapJson)
        assertEquals("课表导入快照 (JSON)", snapDoc.formatLabel)
        assertEquals(1, snapDoc.errorAndCrashCount)
        assertTrue(snapDoc.entries.any { it.message.contains("操作系统") })

        // 3. Markdown Diagnostic Report with embedded JSON
        val diagPayload = DiagnosticPayload(
            appVersion = "1.3.15",
            androidVersion = 34,
            deviceModel = "Pixel 8",
            source = "正方经典版",
            semesterName = "2026春季",
            totalWeeks = 16,
            incomingCount = 0,
            courses = emptyList(),
            error = "未定位到 Table1",
            extraNote = "点击读取后空白"
        )
        val mdReport = DiagnosticReporter.buildMarkdownReport(diagPayload)
        val mdDoc = LogCodec.parseExternalLog("feedback.md", mdReport)
        assertTrue(mdDoc.formatLabel.contains("提取诊断报告"))
        assertEquals(1, mdDoc.errorAndCrashCount)
        assertEquals("点击读取后空白", mdDoc.metadata["用户说明"])
    }

    @Test
    fun `file store rotates runtime logs, retains crash logs, and manages imported logs`() {
        val baseDir = tempFolder.newFolder("logs_test")
        val store = LogFileStore(
            baseDir = baseDir,
            maxSingleFileBytes = 300L,
            maxRuntimeFiles = 3,
            maxCrashFiles = 2,
            maxImportedFiles = 2
        )

        val baseTime = 1762934400000L
        val crashEntry = LogEntry(
            timestamp = baseTime,
            formattedTime = LogCodec.formatTimestamp(baseTime),
            level = LogLevel.CRASH,
            tag = "UncaughtCrash",
            message = "NullPointerException: test crash",
            stackTrace = "java.lang.NullPointerException: test crash\n\tat App.kt:10"
        )
        store.recordCrashSync(crashEntry, mapOf("App版本" to "1.3.15"))

        // 写入足够多条运行日志以触发多次单文件轮转并淘汰旧 runtime 文件
        for (i in 1..25) {
            val ts = baseTime + i * 1000L
            store.appendEntry(
                LogEntry(
                    timestamp = ts,
                    formattedTime = LogCodec.formatTimestamp(ts),
                    level = LogLevel.INFO,
                    tag = "Worker",
                    message = "第 $i 次后台任务执行完成，填充足够长度触发文件按大小滚动切分 ABCDEFGHIJKLMN"
                )
            )
            Thread.sleep(2)
        }

        val stats = store.getStats()
        assertTrue("Runtime files should be capped at 3", stats.runtimeFileCount <= 3)
        assertEquals(1, stats.crashFileCount)

        // 即使旧 runtime 文件被轮转清理，crash 目录中的崩溃记录依然应当能够被合并读取出来
        val recent = store.readRecentEntries(limit = 100)
        assertTrue(recent.any { it.level == LogLevel.CRASH && it.message.contains("test crash") })
        // 最新一条排在最前
        assertTrue(recent.first().message.contains("第 25 次"))

        // 测试外部导入日志保存、上限淘汰与删除
        val (meta1, _) = store.saveImportedLog("user_a.log", "2026-11-12 10:00:00.000 [E/Net] Timeout 1")
        Thread.sleep(5)
        val (meta2, _) = store.saveImportedLog("user_b.log", "2026-11-12 10:01:00.000 [I/Net] Connected")
        Thread.sleep(5)
        val (meta3, _) = store.saveImportedLog("user_c.log", "2026-11-12 10:02:00.000 [E/DB] SQLITE_BUSY")

        val importedList = store.listImportedLogs()
        assertEquals(2, importedList.size) // maxImportedFiles = 2
        assertEquals("user_c.log", importedList[0].fileName)
        assertEquals("user_b.log", importedList[1].fileName)
        assertNull(store.loadImportedLog(meta1.id))

        val loadedC = store.loadImportedLog(meta3.id)
        assertNotNull(loadedC)
        assertEquals(1, loadedC!!.errorAndCrashCount)

        store.deleteImportedLog(meta2.id)
        assertEquals(1, store.listImportedLogs().size)

        store.clearRuntimeAndCrashLogs()
        assertEquals(0, store.getStats().runtimeFileCount)
        assertEquals(0, store.getStats().crashFileCount)
        // 清空本机日志不应误删外部导入日志
        assertEquals(1, store.listImportedLogs().size)
    }

    @Test
    fun `uploader posts sanitized JSON payload and handles HTTP responses`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"message":"日志已成功入库 #9527"}""")
            )

            val entries = listOf(
                LogEntry(
                    timestamp = 1762934400000L,
                    formattedTime = "2026-11-12 14:40:00.000",
                    level = LogLevel.ERROR,
                    tag = "Import",
                    message = "登录请求失败 password=topSecret123"
                )
            )
            val payload = LogUploader.buildPayload(
                entries = entries,
                appVersion = "1.3.15",
                versionCode = 64,
                androidSdk = 34,
                deviceModel = "Xiaomi 14",
                userNote = "我的手机号是 13800138000，导入报错",
                importSnapshots = listOf(
                    ImportSnapshotBrief("2026-11-12 14:39:00", "正方教务", "2026秋", 12, null)
                )
            )

            val endpointUrl = server.url("/api/report").toString()
            val result = LogUploader.upload(endpointUrl, payload)
            assertTrue(result.isSuccess)
            assertEquals("日志已成功入库 #9527", result.getOrNull())

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/report", recorded.path)
            assertEquals("1.3.15", recorded.getHeader("X-App-Version"))

            val sentBody = recorded.body.readUtf8()
            assertFalse("Password must be sanitized before upload", sentBody.contains("topSecret123"))
            assertFalse("Phone number in userNote must be sanitized", sentBody.contains("13800138000"))
            assertTrue(sentBody.contains("正方教务"))

            // 验证服务端 500 错误处理
            server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
            val failResult = LogUploader.upload(endpointUrl, payload)
            assertTrue(failResult.isFailure)
            assertTrue(failResult.exceptionOrNull()?.message.orEmpty().contains("HTTP 500"))

            // 验证非法地址拦截
            val badUrlResult = LogUploader.upload("   ", payload)
            assertTrue(badUrlResult.isFailure)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `SettingsViewModel initializes all MutableState delegates before calling member methods in init`() {
        val vmClass = com.chen.schedule.ui.settings.SettingsViewModel::class.java
        val classBytes = vmClass.getResourceAsStream("SettingsViewModel.class")?.use { it.readBytes() }
        assertNotNull("SettingsViewModel.class must be loadable", classBytes)
        val dis = java.io.DataInputStream(java.io.ByteArrayInputStream(classBytes!!))
        assertEquals(0xCAFEBABE.toInt(), dis.readInt())
        dis.readUnsignedShort() // minor
        dis.readUnsignedShort() // major

        val cpCount = dis.readUnsignedShort()
        val utf8Pool = HashMap<Int, String>()
        val classRefPool = HashMap<Int, Int>()
        val nameAndTypePool = HashMap<Int, Pair<Int, Int>>()
        val memberRefPool = HashMap<Int, Pair<Int, Int>>() // tag 9 (Fieldref) or 10 (Methodref) -> (classIdx, natIdx)

        var idx = 1
        while (idx < cpCount) {
            when (val tag = dis.readUnsignedByte()) {
                1 -> utf8Pool[idx] = dis.readUTF()
                7 -> classRefPool[idx] = dis.readUnsignedShort()
                8, 16, 19, 20 -> dis.skipBytes(2)
                3, 4 -> dis.skipBytes(4)
                5, 6 -> { dis.skipBytes(8); idx++ }
                9, 10, 11 -> memberRefPool[idx] = dis.readUnsignedShort() to dis.readUnsignedShort()
                12 -> nameAndTypePool[idx] = dis.readUnsignedShort() to dis.readUnsignedShort()
                15 -> dis.skipBytes(3)
                17, 18 -> dis.skipBytes(4)
                else -> error("Unexpected constant pool tag: $tag")
            }
            idx++
        }

        dis.skipBytes(6) // access_flags, this_class, super_class
        val interfacesCount = dis.readUnsignedShort()
        dis.skipBytes(interfacesCount * 2)

        val fieldsCount = dis.readUnsignedShort()
        val delegateFieldNames = HashSet<String>()
        repeat(fieldsCount) {
            dis.skipBytes(2) // access
            val name = utf8Pool[dis.readUnsignedShort()].orEmpty()
            dis.skipBytes(2) // descriptor
            if (name.endsWith("\$delegate")) delegateFieldNames.add(name)
            val attrCount = dis.readUnsignedShort()
            repeat(attrCount) {
                dis.skipBytes(2)
                dis.skipBytes(dis.readInt())
            }
        }
        assertTrue("Expected MutableState delegate fields in SettingsViewModel", delegateFieldNames.size >= 15)

        val methodsCount = dis.readUnsignedShort()
        var initCode: ByteArray? = null
        repeat(methodsCount) {
            dis.skipBytes(2)
            val mName = utf8Pool[dis.readUnsignedShort()].orEmpty()
            dis.skipBytes(2)
            val attrCount = dis.readUnsignedShort()
            repeat(attrCount) {
                val attrName = utf8Pool[dis.readUnsignedShort()].orEmpty()
                val attrLen = dis.readInt()
                if (mName == "<init>" && attrName == "Code") {
                    val codeAttr = ByteArray(attrLen)
                    dis.readFully(codeAttr)
                    val cDis = java.io.DataInputStream(java.io.ByteArrayInputStream(codeAttr))
                    cDis.skipBytes(4) // max_stack, max_locals
                    val codeLen = cDis.readInt()
                    initCode = ByteArray(codeLen).also { cDis.readFully(it) }
                } else {
                    dis.skipBytes(attrLen)
                }
            }
        }
        assertNotNull("Expected <init> Code attribute", initCode)
        val code = initCode!!

        val ownerInternalName = vmClass.name.replace('.', '/')
        val initializedDelegatesBeforeSelfCall = HashSet<String>()
        var sawSelfMethodCall = false

        var pc = 0
        while (pc < code.size) {
            val opcode = code[pc].toInt() and 0xFF
            when (opcode) {
                0xB5 /* putfield */, 0xB6 /* invokevirtual */, 0xB7 /* invokespecial */ -> {
                    val cpIdx = ((code[pc + 1].toInt() and 0xFF) shl 8) or (code[pc + 2].toInt() and 0xFF)
                    val ref = memberRefPool[cpIdx]
                    val owner = ref?.let { utf8Pool[classRefPool[it.first]] }.orEmpty()
                    val memberName = ref?.let { nameAndTypePool[it.second]?.first }?.let { utf8Pool[it] }.orEmpty()
                    if (owner == ownerInternalName) {
                        if (opcode == 0xB5 && memberName in delegateFieldNames && !sawSelfMethodCall) {
                            initializedDelegatesBeforeSelfCall.add(memberName)
                        } else if ((opcode == 0xB6 || opcode == 0xB7) && memberName != "<init>" && !memberName.startsWith("get")) {
                            sawSelfMethodCall = true
                        }
                    }
                    pc += 3
                }
                // 常见定长字节码步进
                0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3A, 0xBC -> pc += 2
                0x11, 0x13, 0x14, 0x84, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xA0,
                0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xB2, 0xB3, 0xB4, 0xB8, 0xBB, 0xBD, 0xC0, 0xC1, 0xC6, 0xC7 -> pc += 3
                0xB9, 0xBA, 0xC8 -> pc += 5
                else -> pc += 1
            }
        }

        assertEquals(
            "All MutableState delegates must be initialized before SettingsViewModel.<init> invokes any member method",
            delegateFieldNames,
            initializedDelegatesBeforeSelfCall
        )
    }
}
