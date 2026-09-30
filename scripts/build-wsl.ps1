param(
    [string]$Distribution = 'Ubuntu',
    [string[]]$Tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Get-Command wsl -ErrorAction SilentlyContinue)) {
    throw '未找到 WSL。请使用已安装 JDK 17 和 Android SDK 的 Windows 或 Linux 环境构建。'
}
# WSL accepts Windows paths directly. Pass arguments without composing shell code.
& wsl -d $Distribution --cd $projectRoot -- bash ./gradlew @Tasks --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "构建失败（退出码 $LASTEXITCODE），请查看上方输出。"
}
