package com.chen.schedule.ui.import_

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * WebVPN 与统一身份认证是两套独立认证，同一导入会话内需要登录两次。
 * [HaustLoginRelay] 把第一次登录输入的账号密码只在内存里中转一次，
 * 用于自动填充第二个登录页；不写磁盘、不持久化，导入页销毁即随对象回收。
 */
class HaustLoginRelay {
    data class CapturedLogin(val username: String, val password: String)

    @Volatile
    private var captured: CapturedLogin? = null

    fun capture(username: String, password: String) {
        if (username.isNotBlank() && password.isNotBlank()) captured = CapturedLogin(username, password)
    }

    fun peek(): CapturedLogin? = captured

    /** 主动清除(导入页销毁时调用);凭据仅存活于本次导入会话的内存。 */
    fun clear() { captured = null }
}

/**
 * 注入学校登录页的 JS 桥：只接收凭据（页面侧无读取接口），填充提示回主线程。
 * WebView 仅允许 *.haust.edu.cn 的 HTTPS 页面，桥的暴露范围被 URL 白名单限制在学校域名内。
 */
class HaustLoginRelayBridge(
    private val relay: HaustLoginRelay,
    private val onHint: (String) -> Unit
) {
    @JavascriptInterface
    fun onCaptured(username: String?, password: String?) {
        if (username != null && password != null) relay.capture(username, password)
    }

    @JavascriptInterface
    fun onFilled() {
        Handler(Looper.getMainLooper()).post {
            onHint("已自动填充第一次登录输入的账号密码，请确认后登录；关闭导入页面即自动清除")
        }
    }
}

/**
 * 登录页注入脚本：
 * - 捕获：监听表单 submit 与“登录”类按钮点击，读取可见的用户名/密码框提交给桥；
 * - 填充：fill(用户名, 密码) 等待 SPA 渲染出登录框（最多 15 秒），只填充空字段，
 *   走原生 value setter 并派发 input/change 事件以兼容 Vue/React 绑定，成功后立即释放凭据且不自动提交。
 */
internal const val HAUST_LOGIN_RELAY_JS = """
(function(){
  if (window.__haustRelay) return;
  var R = window.__haustRelay = {};
  function vis(el){
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }
  function pwdInputs(){
    return Array.prototype.slice.call(document.querySelectorAll('input[type=password]')).filter(vis);
  }
  function userFor(pwd){
    var scope = pwd.form || document;
    var cands = scope.querySelectorAll('input:not([type=password]):not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]):not([type=file])');
    var list = Array.prototype.slice.call(cands).filter(vis).filter(function(i){
      var k = ((i.name||'') + ' ' + (i.id||'') + ' ' + (i.placeholder||'') + ' ' + (i.autocomplete||'')).toLowerCase();
      return k.indexOf('user')>=0 || k.indexOf('account')>=0 || k.indexOf('name')>=0 ||
             k.indexOf('mobile')>=0 || k.indexOf('phone')>=0 || i.type==='text' || i.type==='tel' || i.type==='';
    });
    var all = Array.prototype.slice.call(document.querySelectorAll('input'));
    var idx = all.indexOf(pwd);
    list.sort(function(a,b){ return Math.abs(all.indexOf(a)-idx) - Math.abs(all.indexOf(b)-idx); });
    return list[0] || null;
  }
  function harvest(){
    var p = pwdInputs()[0];
    if(!p || !p.value) return;
    var u = userFor(p);
    if(!u || !u.value) return;
    try { HaustLoginRelay.onCaptured(u.value, p.value); } catch(e) {}
  }
  document.addEventListener('submit', harvest, true);
  document.addEventListener('click', function(e){
    var t = e.target;
    if(!t || !t.closest || !pwdInputs().length) return;
    var b = t.closest('button, input[type=submit], input[type=button], a, [role=button], div, span');
    if(!b) return;
    var txt = ((b.value||'') + ' ' + (b.textContent||'') + ' ' + (b.getAttribute('aria-label')||'')).trim();
    if(b.type === 'submit' || (txt.length < 10 && /登\s*录|login|sign\s*in/i.test(txt))) harvest();
  }, true);
  function set(el, v){
    var proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v);
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
  }
  R.fill = function(user, pass){
    var tries = 0;
    function attempt(){
      tries++;
      var filled = false;
      pwdInputs().forEach(function(pw){
        if (pw.value) return;
        var uu = userFor(pw);
        if (uu && !uu.value) { set(uu, user); set(pw, pass); filled = true; }
      });
      if (filled){
        user = null; pass = null;
        try { HaustLoginRelay.onFilled(); } catch(e) {}
      } else if (tries < 30) {
        setTimeout(attempt, 500);
      }
    }
    setTimeout(attempt, 300);
  };
})()
"""
