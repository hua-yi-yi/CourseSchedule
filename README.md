# 课程表 CourseSchedule

一个本地优先的 Android 课程表应用:手动管理课程、从 **JSON/CSV 文件** 或 **正方教务系统** 导入课表,并支持桌面小组件(今日课程)。

## 功能

- **课程表视图**:周视图 / 日视图切换,单双周过滤,周末显示开关,自动推算当前周
- **课程管理**:增删改课程(教师/教室/节次/周次/周类型/颜色/备注)
- **导入**:
  - JSON 导入(支持结构化对象、扁平数组、宽松解析三种格式)
  - CSV 导入(支持中英文列名、带引号字段、`1-2` 节次/周次范围)
  - 正方教务系统自动导入(验证码 + GB2312 表单登录,支持 http/https)
- **学期与作息**:多学期管理、当前学期切换、45/40 分钟节次模板、自定义节次
- **数据管理**:备份(导出 JSON)、恢复、清空当前学期
- **桌面小组件**(设置 → 桌面 一键添加):
  - **今日课程 (4×1)**:今天上课列表,数据变更后自动刷新
  - **周课表 (4×4)**:整周课程网格(节次 × 周一~周日),支持跨节连排与单双周

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言/构建 | Kotlin 1.9.22、AGP 8.3.2、Gradle 8.5、KSP |
| UI | Jetpack Compose (BOM 2024.02.00)、Material 3、Navigation Compose |
| 架构 | MVVM + 单向数据流(StateFlow)、Hilt 依赖注入 |
| 数据 | Room 2.6.1、DataStore Preferences |
| 网络 | OkHttp 4.12、Jsoup 1.17.2 |
| 其他 | kotlinx.serialization、Glance 1.1.0(桌面小组件) |
| 环境 | minSdk 26 / target & compileSdk 34 / JVM 17 |

## 项目结构

```
app/src/main/java/com/chen/schedule/
├── domain/model/      # 领域模型(Course/Semester/TimeSlot/WeekType/CoursePalette)
├── data/
│   ├── local/         # Room 数据库、DAO、Entity
│   ├── repository/    # 仓储层(Entity ↔ Domain 映射)
│   └── scraper/       # 教务系统抓取(ZhengfangScraper / 纯解析器 ZhengfangPageParser)
├── ui/                # Compose 界面 + ViewModel
│   ├── timetable/     # 课程表(周/日视图)
│   ├── course/        # 课程编辑
│   ├── import_/       # 文件导入 & 教务导入(验证码)
│   ├── schedule/      # 学期与作息配置
│   └── settings/      # 设置(备份/恢复/清空)
├── widget/            # Glance 今日课程小组件
├── util/              # CSV/JSON 导入器、周次计算
└── di/                # Hilt 模块(+ 小组件用的 DatabaseEntryPoint)
```

## 教务导入说明

- 适配 **正方教务系统(经典版)**:登录页 `default2.aspx`、课表页 `xskbcx.aspx`、验证码 `CheckCode.aspx`。
- 使用方法:「导入 → 教务导入」,输入学校教务系统地址(如 `https://jwxt.example.edu.cn/jwxt`)、学号、密码,先获取验证码再登录。
- 解析器支持周次文本(`1-16周` / `1-8周(单)` / `2-16周（双）`)、`rowspan` 跨节连排、单元格内多门课程。
- 各校页面结构不同,如导入失败请改用 JSON/CSV 导入。

### JSON 格式示例

```json
{
  "semesterName": "2025-2026 第一学期",
  "courses": [
    { "name": "高等数学", "teacher": "张老师", "classroom": "教学楼101",
      "dayOfWeek": 1, "startSlot": 1, "endSlot": 2, "startWeek": 1, "endWeek": 16, "weekType": "all" }
  ]
}
```

### CSV 格式示例

```csv
name,teacher,classroom,dayOfWeek,startSlot,endSlot,startWeek,endWeek,weekType,note
高等数学,张老师,教学楼101,1,1,2,1,16,all,
大学英语,李老师,教学楼202,2,3,4,1,16,odd,
```

## 构建

```bash
# 需要 JDK 17 与 Android SDK(compileSdk 34)
./gradlew :app:assembleDebug        # 构建 Debug APK
./gradlew :app:testDebugUnitTest    # 运行单元测试
```

> `local.properties` 中的 `sdk.dir` 按本机 SDK 路径修改(该文件不应提交到版本库)。

## 测试

- `ZhengfangPageParserTest`:周次解析、rowspan 列映射、重复课程合并、表头跳过
- `ScheduleLogicTest`:周次计算、课程周匹配、颜色分配稳定性
