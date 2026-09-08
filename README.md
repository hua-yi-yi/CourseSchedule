# 课程表 CourseSchedule

一个本地优先的 Android 课程表应用:手动管理课程、从 **JSON/CSV 文件** 或 **正方教务系统** 导入课表,并支持桌面小组件(今日课程)。

## 功能

- **课程表视图**:周视图 / 日视图切换,单双周过滤,周末显示开关,自动推算当前周
- **课程管理**:增删改课程(教师/教室/节次/周次/周类型/颜色/备注)
- **导入**:
  - JSON 导入(支持结构化对象、扁平数组、宽松解析三种格式)
  - CSV 导入(支持中英文列名、带引号字段、`1-2` 节次/周次范围)
  - 正方教务系统自动导入(验证码 + GB2312 表单登录,支持 http/https)
- **学期与作息**:多学期管理、当前学期切换、夏季/冬季作息模板（手动套用，替换前确认）、自定义节次
- **数据管理**:备份当前学期信息、课程及作息（JSON）；确认后替换恢复，兼容旧课程 JSON；清空当前学期课程
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
./gradlew :app:assembleRelease      # 构建签名正式版(R8 混淆+资源压缩,~2.3MB)
```

> `local.properties` 中的 `sdk.dir` 按本机 SDK 路径修改(该文件不应提交到版本库)。
> Release 签名密钥位于本机 `~/.android/keystores/`(不入库),密码通过 `local.properties` 或 CI Secrets 提供。

## 发布版本(GitHub Actions 自动)

1. 本地提交并推送代码
2. 打 tag 并推送:

```bash
git tag -a v1.0.2 -m "v1.0.2"
git push origin v1.0.2
```

3. CI 自动:跑单元测试 → 构建**签名正式版** → 创建 Release 并挂载 APK

> 首次需在仓库 **Settings → Secrets and variables → Actions** 配置:
> `RELEASE_KEYSTORE_B64`(keystore 的 base64)、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_PASSWORD`、`RELEASE_KEY_ALIAS`。
> 未配置时 CI 降级构建 debug APK。

## 测试

- `ZhengfangPageParserTest`:周次解析、rowspan 列映射、重复课程合并、表头跳过
- `ScheduleLogicTest`:周次计算、课程周匹配、颜色分配稳定性

## 数据升级与恢复

- 数据库版本 2 为作息增加套别字段，从版本 1 升级保留原有记录。
- 完整备份支持空课程学期；恢复替换当前学期的课程、学期信息及全局作息，不影响其他学期课程。
- 旧课程 JSON 恢复仅替换当前学期课程；需要追加导入时使用「导入」页面。
- 夏冬季按钮为手动模板替换，不会按日期自动切换。套用前请备份自定义作息。

## 河南科技大学教务导入

「导入 → 教务系统导入 → 河南科技大学 · VPN / 校内导入」打开专用页面。校外使用学校 VPN，校内可选择直连。完成学校认证后进入「教学信息 → 我的课表」，选择正确学期和「全部」教学周，点击「读取课表」，检查预览及目标学期后确认。

- 解析已渲染的 EAMS 课表，支持合并节次、同一课程换教室、单双周及离散周次。
- 重复导入会跳过相同课程安排；不会覆盖既有课程。没有具体星期和节次的课程不导入。
- 学期起始日期和作息仍由设置管理，不根据网页学期名称自动推算。
- 登录在学校 HTTPS 页面内完成；应用不读取或保存密码。关闭专用页面清除应用内认证 Cookie。
- 专用 WebView 强制直连、绕过系统 HTTP 代理；设备若有 VPN/TUN，底层路由仍由系统控制。不支持代理覆盖的旧 WebView 会提示更新，不会静默使用系统代理。
