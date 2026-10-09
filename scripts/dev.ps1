<#
.SYNOPSIS
    一键起本地开发环境：后端 8080 + 前端 5173（各自一个新窗口）。

.DESCRIPTION
    兼容 Windows PowerShell 5.1 与 PowerShell 7：用当前可用的 shell 拉起子窗口。
    首次运行前请确认：
      1) MySQL 已启动，且已执行 scripts/db-init.sql；
      2) backend/src/main/resources/application-local.yml 存在（可从 .example 复制）。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\dev.ps1
    powershell -File scripts\dev.ps1 -BackendOnly
    powershell -File scripts\dev.ps1 -FrontendOnly
#>
[CmdletBinding()]
param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [string]$JavaHome = 'D:\Code_Environment\Java\jdk-21'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'

# 优先用 PowerShell 7（若有），否则回退到 Windows PowerShell 5.1
$shell = (Get-Command pwsh -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $shell) {
    $shell = (Get-Command powershell -ErrorAction SilentlyContinue | Select-Object -First 1).Source
}
if (-not $shell) { throw '找不到可用的 PowerShell 解释器' }

if (-not (Test-Path (Join-Path $backend 'src\main\resources\application-local.yml'))) {
    Write-Warning "缺少 backend/src/main/resources/application-local.yml，可从 application-local.yml.example 复制后填入数据库密码与 JWT 密钥"
}

if (-not $FrontendOnly) {
    Write-Host "启动后端 -> http://localhost:8080 (swagger: /swagger-ui.html)" -ForegroundColor Cyan
    Start-Process $shell -ArgumentList @(
        '-NoExit', '-Command',
        "`$env:JAVA_HOME='$JavaHome'; Set-Location '$backend'; mvn -B spring-boot:run"
    )
}

if (-not $BackendOnly) {
    Write-Host "启动前端 -> http://localhost:5173" -ForegroundColor Cyan
    Start-Process $shell -ArgumentList @(
        '-NoExit', '-Command',
        "Set-Location '$frontend'; if (-not (Test-Path node_modules)) { pnpm install }; pnpm dev"
    )
}

Write-Host ""
Write-Host "已用 $shell 拉起窗口。后端就绪后访问 http://localhost:5173 即可。" -ForegroundColor Green
Write-Host "默认管理员：admin / Admin@12345（首次登录后请改密码）" -ForegroundColor Yellow
