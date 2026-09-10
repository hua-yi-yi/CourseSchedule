package com.chen.schedule.ui.import_

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.chen.schedule.data.local.AppDatabase
import androidx.room.withTransaction
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.scraper.HaustPageParser
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.widget.WidgetUpdater
import com.chen.schedule.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executor
import javax.inject.Inject

private fun adaptHaustPage(view: WebView, url: String) {
    val host = Uri.parse(url).host.orEmpty()
    val css = when {
        host == "cas.haust.edu.cn" -> """
            html,body{max-width:100%!important;overflow-x:hidden!important}
            input,button,select{font-size:16px!important}
            img{max-width:100%!important;height:auto!important}
        """.trimIndent()
        host.startsWith("jwgl-") || host == "jwgl.haust.edu.cn" -> """
            html,body{max-width:100%!important;min-width:0!important}
            iframe{max-width:100%!important}
            #manualArrangeCourseTable{min-width:900px!important}
            .gridtable,.grid{overflow-x:auto!important;-webkit-overflow-scrolling:touch!important}
        """.trimIndent()
        else -> ""
    }
    val escaped = JSONObject.quote(css)
    view.evaluateJavascript(
        """
        (function(){
          if(location.hostname.indexOf('jwgl-')===0){
            document.querySelectorAll('a[href]').forEach(function(a){
              var href=a.getAttribute('href')||'';
              if(href.indexOf('https://jwgl.haust.edu.cn/')===0){
                a.setAttribute('href',href.replace('https://jwgl.haust.edu.cn/','https://jwgl-haust-edu-cn-s.haust.edu.cn/'));
              }
            });
            if(location.pathname.endsWith('/courseTableForStd.action') && !window.__courseScheduleTableSubmitted){
              window.__courseScheduleTableSubmitted=true;
              var source=Array.from(document.scripts).map(function(s){return s.text||'';}).join('\n');
              var semester=source.match(/semesterCalendar\(\{[^}]*value:\s*"(\d+)"/);
              var student=source.match(/addInput\(form,\s*"ids",\s*"(\d+)"\)/);
              if(semester && student){
                var form=document.createElement('form');
                form.method='post';
                form.action='/eams/courseTableForStd!courseTable.action';
                [['setting.kind','std'],['startWeek',''],['semester.id',semester[1]],['ids',student[1]],['ignoreHead','1']].forEach(function(pair){
                  var input=document.createElement('input');input.name=pair[0];input.value=pair[1];form.appendChild(input);
                });
                document.body.appendChild(form);
                setTimeout(function(){form.submit();},100);
              }
            }
          }
          document.querySelectorAll('a[target]').forEach(function(a){a.removeAttribute('target');});
          if(!window.__courseScheduleOpenPatched){
            window.__courseScheduleOpenPatched=true;
            window.open=function(url){if(url){window.location.href=url;}return window;};
          }
          var meta=document.querySelector('meta[name="viewport"]');
          if(!meta){meta=document.createElement('meta');meta.name='viewport';document.head.appendChild(meta);}
          meta.content='width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes';
          var style=document.getElementById('course-schedule-mobile-style');
          if(!style){style=document.createElement('style');style.id='course-schedule-mobile-style';document.head.appendChild(style);}
          style.textContent=$escaped;
        })()
        """.trimIndent(), null
    )
}

@HiltViewModel
class HaustImportViewModel @Inject constructor(
    private val courses: CourseRepository,
    private val semesters: SemesterRepository,
    private val db: AppDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {
    var preview by mutableStateOf<List<Course>>(emptyList()); private set
    var destination by mutableStateOf<Semester?>(null); private set
    var sourceSemester by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    fun clearPreview() { if (!busy) preview = emptyList() }
    fun report(text: String) { message = text }
    fun parse(raw: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val payload = JSONObject(raw)
                    require(!payload.has("error")) { payload.optString("error") }
                    payload.optString("semester") to HaustPageParser.parse(payload.getString("html"))
                }
                destination = semesters.getCurrentSemester() ?: error("请先在设置中创建学期，再返回导入")
                sourceSemester = result.first
                preview = CoursePalette.assignColors(result.second)
                message = ""
            } catch (e: Exception) { message = e.message ?: "读取失败" }
            finally { busy = false }
        }
    }
    fun confirm() {
        val sem = destination ?: return
        if (busy || preview.isEmpty()) return
        val incoming = preview
        busy = true
        viewModelScope.launch {
            try {
                val added = db.withTransaction {
                    require(semesters.getCurrentSemester()?.id == sem.id) { "当前学期已切换，请重新读取课表" }
                    fun key(c: Course) = c.copy(id = 0, semesterId = 0, color = 0, note = "")
                    val existing = courses.getCoursesBySemester(sem.id).first().map { key(it) }.toSet()
                    val fresh = incoming.filter { key(it) !in existing }.map { it.copy(id = 0, semesterId = sem.id) }
                    courses.insertAll(fresh)
                    fresh.size
                }
                preview = emptyList()
                message = "已导入 $added 条安排，跳过 ${incoming.size - added} 条重复安排"
                WidgetUpdater.refreshAll(context)
            } catch (e: Exception) { message = e.message ?: "导入失败" }
            finally { busy = false }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaustImportScreen(onNavigateBack: () -> Unit, viewModel: HaustImportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var browser by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    val executor = remember { Executor { command -> android.os.Handler(android.os.Looper.getMainLooper()).post(command) } }
    val script = remember { context.assets.open("haust-extract.js").bufferedReader().use { it.readText() } }

    DisposableEffect(Unit) {
        onDispose {
            ready = false
            browser?.stopLoading()
            browser?.destroy()
            browser = null
            // This screen is the app's only WebView; do not retain school authentication cookies.
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(executor) { }
            }
        }
    }
    BackHandler(enabled = browser?.canGoBack() == true && viewModel.preview.isEmpty()) { browser?.goBack() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("河南科技大学课表") }, navigationIcon = {
            TextButton(onClick = onNavigateBack) { Text("关闭") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("先登录学校 VPN。登录后可点“我的课表”直达，选择全部教学周，再读取。",
                modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(enabled = ready && !reading && !viewModel.busy, onClick = { browser?.loadUrl("https://vpn.haust.edu.cn/portal/") }) { Text("学校 VPN") }
                TextButton(enabled = ready && !reading && !viewModel.busy, onClick = { browser?.loadUrl("https://jwgl-haust-edu-cn-s.haust.edu.cn/eams/homeExt.action") }) { Text("VPN 教务") }
                TextButton(enabled = ready && !reading && !viewModel.busy, onClick = { browser?.loadUrl("https://jwgl-haust-edu-cn-s.haust.edu.cn/eams/courseTableForStd.action") }) { Text("我的课表") }
                TextButton(enabled = ready && !reading && !viewModel.busy, onClick = { browser?.loadUrl("https://jwgl.haust.edu.cn/eams/homeExt.action") }) { Text("校内直连") }
                Button(enabled = ready && !viewModel.busy && !reading, onClick = {
                    val host = Uri.parse(browser?.url).host.orEmpty()
                    if (!host.endsWith(".haust.edu.cn")) viewModel.report("请先打开学校教务页面")
                    else {
                        reading = true
                        browser?.evaluateJavascript(script) { result -> reading = false; viewModel.parse(result) }
                    }
                }) { Text(if (reading || viewModel.busy) "处理中" else "读取课表") }
            }
            if (address.isNotBlank()) Text(address, Modifier.padding(horizontal = 12.dp), maxLines = 1, style = MaterialTheme.typography.labelSmall)
            if (viewModel.message.isNotBlank()) Text(viewModel.message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
                WebView(ctx).apply {
                    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                    browser = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            val allowed = u.scheme == "https" && u.host.orEmpty().endsWith(".haust.edu.cn")
                            if (!allowed && request.isForMainFrame) viewModel.report("已阻止离开学校 HTTPS 网站的跳转")
                            return !allowed
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            address = Uri.parse(url).host.orEmpty()
                            adaptHaustPage(view, url)
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) viewModel.report("页面加载失败：${error.description}")
                        }
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                        val web = this
                        ProxyController.getInstance().setProxyOverride(ProxyConfig.Builder().addDirect().build(), executor) {
                            if (browser === web) {
                                ready = true
                                web.loadUrl("https://vpn.haust.edu.cn/portal/")
                            }
                        }
                    } else viewModel.report("请更新 Android System WebView 后重试：当前版本无法确保绕过系统代理")
                }
            })
        }
    }
    if (viewModel.preview.isNotEmpty()) AlertDialog(
        onDismissRequest = viewModel::clearPreview,
        title = { Text("确认导入 ${viewModel.preview.size} 条安排") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("网页学期：${viewModel.sourceSemester.ifBlank { "未识别" }}\n导入到：${viewModel.destination?.name}\n同一课程的不同周次或教室分别保留；不会删除已有课程。")
                Spacer(Modifier.height(8.dp))
                viewModel.preview.forEach { c -> Text("${c.name} · 周${c.dayOfWeek} 第${c.startSlot}-${c.endSlot}节 · ${c.startWeek}-${c.endWeek}周 ${c.weekType.label}\n${c.classroom}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
            }
        },
        confirmButton = { TextButton(enabled = !viewModel.busy, onClick = viewModel::confirm) { Text("确认导入") } },
        dismissButton = { TextButton(enabled = !viewModel.busy, onClick = viewModel::clearPreview) { Text("取消") } }
    )
}
