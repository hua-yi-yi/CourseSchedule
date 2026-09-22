# 4×4 小组件与稳定性修复

## 改动

- 周课表按课程身份合并连续节次，跨两节或更多节次只绘制一个课程块。相邻但不同的课程保持分开。
- 地点使用完整文本自动换行，按组件宽度和系统字体测量所需高度；七列与左侧节次保持对齐，内容超出组件时整体滚动。
- 修复浅色课程背景在深色主题下的文字对比度，忽略完全越界的节次数据。
- 下载超时显示失败；取消使用独立任务身份、临时文件和同步保护，旧任务不会覆盖新任务进度或文件。
- HTTP 响应与临时文件及时清理；复用或提交 APK 前检查 ZIP 长度/CRC、包名、版本、版本号及签名信息。最终签名有效性由 Android 安装器验证。
- 设置界面、ViewModel 与备份服务分离；统一完整恢复替换全部学期的提示。数据库写入错误向上传播并回滚事务。

## 验证

- 单元测试：175 项通过，包含 7 项下载器/传输测试。
- Android 14 模拟器回归：1 → 3 与 2 → 3 数据库升级、完整备份重复恢复与故障回滚、APK 缓存身份及损坏校验，共 4 项通过。
- 小组件使用真实 AppWidgetHost 渲染：350×400dp 常规显示，250×400dp、1.3 倍字体、深色模式，以及滚动查看长地点。
- Lint：0 个错误，46 个警告及 5 条提示。警告未作为全部修复目标。
- Debug 和签名 Release 构建验证；签名证书与项目原有 v1.2.32 APK 一致。

## 重现命令

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

需要 JDK 17、Android SDK 34；设备回归需要连接模拟器或测试设备。回归入口使用独立数据库与临时缓存，不操作用户课表。

本机离线执行 `connectedDebugAndroidTest` 时缺少 UTP 调度插件缓存，因此本次通过 ADB 安装测试 APK 并直接运行同一个回归入口，四项均通过。离线环境可使用：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.chen.schedule.test/com.chen.schedule.RegressionInstrumentation
```

## 范围

本次未增加跨进程断点续传。切换页面时下载继续；进程被系统终止后，未完成下载需重新点击下载。已经完成且通过校验的安装包可复用。

本次发布版本为 1.2.33（versionCode 39）。模拟器检查不能代替所有厂商桌面的兼容性验证。
