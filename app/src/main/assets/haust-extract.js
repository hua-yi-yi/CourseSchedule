(function () {
  function find(doc, depth) {
    if (depth > 5) return null;
    var table = doc.getElementById('manualArrangeCourseTable');
    if (table) {
      var week = doc.getElementById('startWeek');
      if (week && week.selectedIndex >= 0 && week.options[week.selectedIndex].text.trim() !== '全部')
        return {error:'请将「选择教学周」改为「全部」再读取课表'};
      var semester = doc.querySelector('input[id$="Semester"]');
      return {html:doc.documentElement.outerHTML, semester:semester ? semester.value : ''};
    }
    var frames = doc.querySelectorAll('iframe');
    for (var i=0;i<frames.length;i++) {
      try { var result=find(frames[i].contentDocument,depth+1); if(result) return result; } catch(e) {}
    }
    return null;
  }
  if (!location.hostname.endsWith('.haust.edu.cn') || location.protocol !== 'https:')
    return {error:'请在学校 HTTPS 教务页面读取课表'};
  return find(document,0) || {error:'尚未找到课表，请登录后进入「教学信息 → 我的课表」'};
})()
