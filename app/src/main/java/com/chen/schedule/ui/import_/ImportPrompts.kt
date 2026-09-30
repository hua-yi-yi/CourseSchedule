package com.chen.schedule.ui.import_


internal fun buildJsonPrompt(): String {
    return """请根据我提供的课程表截图，识别其中的课程信息，生成以下格式的JSON代码。

格式要求（严格遵守）：
{
  "semesterName": "<学期名称，从截图推断或留空>",
  "courses": [
    {
      "name": "<课程名称>",
      "teacher": "<教师姓名，没有则留空>",
      "classroom": "<教室/地点，没有则留空>",
      "dayOfWeek": <星期几，1=周一 2=周二 ... 7=周日>,
      "startSlot": <开始节次，数字如1>,
      "endSlot": <结束节次，数字如2>,
      "startWeek": <起始周，数字如1>,
      "endWeek": <结束周，数字如16>,
      "weekType": "<all=每周 odd=单周 even=双周>",
      "note": "<备注/课程号等，没有则留空>"
    }
  ]
}

重要规则：
- dayOfWeek: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日
- startSlot和endSlot必须分别为两个独立的数字，例如第1-2节课应写为 "startSlot": 1, "endSlot": 2。严禁写成 "startSlot": "1-2" 这种合并格式
- startWeek和endWeek同样是两个独立的数字，不要合并
- weekType: 如果截图没标注单双周，默认用 "all"
- 只输出JSON代码，不要输出任何解释文字""".trimIndent()
}

internal fun buildCsvPrompt(): String {
    return """请根据我提供的课程表截图，识别其中的课程信息，生成CSV内容。

第一行必须是表头（不要省略）：
name,teacher,classroom,dayOfWeek,startSlot,endSlot,startWeek,endWeek,weekType,note

字段说明：
- name: 课程名称（必填）
- teacher: 教师姓名（没有则留空）
- classroom: 教室/地点（没有则留空）
- dayOfWeek: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日
- startSlot: 开始节次数字，如 1
- endSlot: 结束节次数字，如 2
- startWeek: 起始周数字，如 1
- endWeek: 结束周数字，如 16
- weekType: all=每周, odd=单周, even=双周（不确定就填all）
- note: 备注/课程号（没有则留空）

重要规则：
- 每个课程一行
- 字段之间用英文逗号分隔
- startSlot和endSlot必须分别为两个独立数字，例如第1-2节课应写为 1,2。严禁写成 "1-2" 合并格式
- startWeek和endWeek同理，必须分开
- 如果字段内容包含逗号，用英文双引号包裹
- 只输出CSV内容，不要输出任何解释文字""".trimIndent()
}
