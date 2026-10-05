# 课程表 CourseSchedule

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

一个本地优先的 Android 课程表应用:手动管理课程、从 **JSON/CSV 文件** 或 **正方教务系统** 导入课表,并支持桌面小组件。

## 功能

- **课程表视图**:周视图 / 日视图切换,单双周过滤,周末显示开关,自动推算当前周
- **课程管理**:增删改课程(教师/教室/节次/周次/周类型/颜色/备注)；课表空白格先点一下显示加号，再点同格添加，减少误触
- **导入**:
  - JSON 导入(支持结构化对象、扁平数组、宽松解析三种格式)
  - CSV 导入(支持中英文列名、带引号字段、`1-2` 节次/周次范围)
  - 正方教务系统自动导入(验证码 + GB2312 表单登录,支持 http/https)
- **学期与作息**:多学期管理、当前学期切换、夏季/冬季作息模板（手动套用，替换前确认）、自定义节次
- **数据管理**:完整备份全部学期、课程及作息方案（JSON）；确认后可完整替换恢复，兼容旧课程 JSON（仅替换当前学期课程）；清空当前学期课程
- **桌面小组件**(设置 → 桌面 一键添加):
  - **今日课程看板 (3×3)**:方形今日课程看板
  - **今日课程 (4×1)**:今天上课列表,数据变更后自动刷新
  - **周课表 (4×4)**:同一课程的连续节次合并为一个课程块；地点自动换行并扩展高度，支持整表滚动、单双周及组件尺寸调整
  - 点击任一小组件的标题、课程或空白区域可打开应用

## 使用流程

1. 首次设置：确认第 1 教学周的周一、实际总周数；中途安装可选当前教学周校准。核对下午和晚间作息，不同学校可直接修改模板；季节作息需手动切换。
2. 导入：选择学校或截图／文件方式，读取后统一进入核对页。检查教室、周次、目标学期，逐条修改或取消勾选，再确认。截图识别使用外部 AI，可同时分享图片与格式说明，本应用不内置识图。
3. 更新：勾选「更新本入口以前导入的安排」，核对建议替换，并手动选择需要删除的未匹配旧安排。默认追加／去重，旧安排默认保留。手动修改后该安排受保护；旧版本没有来源标记的安排也保留，避免误删。
4. 调课：课程详情 → 编辑，选择仅本周、从本周开始或整条安排；本周停课只取消本次。点击空白格新增课程时可勾选仅本周补课。
5. 日常：主页使用紧凑单行导航，主体为课程表；上滑时顶栏随课表一起滚动。「本周」返回当前教学周并保留日／周视图，点击学期名进入学期选择。
6. 提醒：设置中检查通知和精确闹钟授权，查看预计提醒并发送测试通知。系统拒绝权限或后台限制可能导致提醒不可用或延迟，界面明确显示状态。
7. 恢复：修改、导入、删除、清空和恢复前保存最近 10 个完整恢复点，位于设置 → 自动恢复点；单条删除还可立即撤销。恢复先预览覆盖范围，再确认。恢复点保存在应用私有空间，卸载／清除应用数据会一起删除，换机仍需导出备份。

数据库 3 → 4 仅为课程增加 `importSource` 列，旧课程、学期和作息全部保留；完整备份包含来源信息，旧备份仍兼容。

## 课程实时卡片与厂商原生接入准备

设置 → 胶囊灵动岛可选择系统通知（推荐）或兼容悬浮胶囊。新安装默认关闭桌面实时展示；应用内胶囊默认开启。实时展示与课前声音提醒分别设置，默认提前量分别为 30 分钟和 20 分钟。

系统模式使用本地课程通知，不要求悬浮窗权限或常驻悬浮服务。已准备小米 HyperOS 2 焦点通知／HyperOS 3 超级岛的公开本地协议；厂商资格、课程场景批准、安装包配置及系统权限全部满足后才发送原生扩展。当前交付包尚无厂商批准，使用普通通知；OPPO／一加、荣耀、vivo 及华为 Android 原生入口明确显示待接入。未启用官方定义尚未确认的系统计时参数或自动进度。

悬浮模式需用户主动选择并授权；一键模拟预览运行最多一分钟，结束后恢复真实设置。实时状态始终按实际日期及有效教学周计算，浏览其他课表周不影响。通知、精确闹钟与厂商后台限制可能影响展示时间，未授权精确闹钟时不承诺固定延迟。

参见 [厂商支持与审核材料](docs/island-vendor-integration.md)。本次不增加服务器、云推送或课程数据上传。

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言/构建 | Kotlin 2.0.21、AGP 8.7.3、Gradle 8.9、KSP |
| UI | Jetpack Compose (BOM 2024.09.03)、Material 3、Navigation Compose |
| 架构 | MVVM + 单向数据流(StateFlow)、Hilt 依赖注入 |
| 数据 | Room 2.6.1、DataStore Preferences |
| 网络 | OkHttp 4.12、Jsoup 1.18.3 |
| 其他 | kotlinx.serialization、Glance 1.1.1(桌面小组件) |
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

Windows 上也可复用 WSL 中已有的 JDK 17 和 Android SDK：

```powershell
.\scripts\build-wsl.ps1
# 可指定发行版和任务
.\scripts\build-wsl.ps1 -Distribution Ubuntu -Tasks ':app:testDebugUnitTest', ':app:assembleDebugAndroidTest'
```

此方式在 WSL 内构建，`local.properties` 的 `sdk.dir` 应指向该发行版中的 Android SDK。无需修改 Windows 全局 Java 环境。

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
> 普通分支和 PR 构建在未配置签名时会生成 debug APK；版本标签发布必须配置全部签名密钥，否则构建失败且不会创建 Release。

## 测试

- `JsonImporterTest` / `CsvImporterTest`：错误记录整体拒绝、记录位置提示、数字与范围校验、BOM、中英文／带引号表头、转义引号和多行字段。
- `CourseImportRulesTest` / 设备导入回归：作息覆盖、学期周数上限、批内与重复导入去重、学期切换保护、失败时不写入任何课程。
- `SafeCookieJarTest`：分步合并、同名替换、路径／域名／HTTPS 匹配、过期删除和退出清理。
- `ActiveSemesterWeekTest`：开学前与学期结束后无有效教学周；提醒与桌面组件不重复显示第一周／末周课程。手动浏览仍可查看任意学期周。

所有追加导入入口（文件、正方、河科大）共用课程校验与事务去重。导入失败会说明原因并保持原数据；成功显示新增及重复数量。请先配置目标学期与能覆盖课程的作息方案。

- `ZhengfangPageParserTest`:周次解析、rowspan 列映射、重复课程合并、表头跳过
- `ScheduleLogicTest`:周次计算、课程周匹配、颜色分配稳定性
- `WeekGridBuilderTest`:跨节合并、错开节次、长地点高度、周次过滤与边界数据。
- `AppUpdateDownloaderTest`:真实 HTTP 错误/超时/取消重试、安装包内容校验。
- `RegressionInstrumentation`:在模拟器或真机的隔离数据库验证 1/2 → 3 升级、多学期重复恢复和失败回滚，并检查安装包缓存。执行 `./gradlew :app:connectedDebugAndroidTest`；测试不会操作用户的课表数据库。

## 数据升级与恢复

- 数据库 1 → 2 为作息节次增加季节套别字段（season）；2 → 3 引入作息方案表（time_schemes），并为节次与学期增加方案关联字段（schemeId）。升级仅加列/建表，从任意旧版本升级均保留原有数据。
- 完整备份支持空课程学期；恢复会替换全部学期、课程与作息方案，并恢复备份时的当前学期。
- 旧课程 JSON 恢复仅替换当前学期课程；需要追加导入时使用「导入」页面。
- 夏冬季按钮为手动模板替换，不会按日期自动切换。套用前请备份自定义作息。

## 河南科技大学教务导入

「导入 → 教务系统导入 → 河南科技大学 · VPN / 校内导入」打开专用页面。校外使用学校 VPN，校内可选择直连。完成学校认证后进入「教学信息 → 我的课表」，选择正确学期和「全部」教学周，点击「读取课表」，检查预览及目标学期后确认。

- 解析已渲染的 EAMS 课表，支持合并节次、同一课程换教室、单双周及离散周次。
- 重复导入会跳过相同课程安排；不会覆盖既有课程。没有具体星期和节次的课程不导入。
- 学期起始日期和作息仍由设置管理，不根据网页学期名称自动推算。
- 登录在学校 HTTPS 页面内完成；应用不读取或保存密码。关闭专用页面清除应用内认证 Cookie。
- 专用 WebView 强制直连、绕过系统 HTTP 代理；设备若有 VPN/TUN，底层路由仍由系统控制。不支持代理覆盖的旧 WebView 会提示更新，不会静默使用系统代理。

## 开源协议

本项目采用 [GNU General Public License v3.0 (GPL-3.0)](LICENSE) 开源协议。

- **自由使用与修改**：你可以自由使用、修改及学习本项目源码；
- **强制传染开源**：任何基于本项目代码的修改、衍生或整合作品，**必须同样以 GPL-3.0 协议完整开源**，严禁闭源商业化或闭源分发；
- **保留版权与署名**：二次分发必须完整保留原作者版权声明及原始许可证。
