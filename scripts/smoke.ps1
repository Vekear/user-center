<#
.SYNOPSIS
    用户中心端到端冒烟脚本：覆盖认证、资料、头像、账号安全、通知、管理后台、审计与权限边界。

.DESCRIPTION
    兼容 Windows PowerShell 5.1（本机没有 PowerShell 7）。
    两个刻意的实现选择：
      1) 用 HttpWebRequest 而不是 Invoke-WebRequest —— 5.1 会按系统 ANSI 代码页解码响应体，
         中文/emoji 会变成乱码，这里一律按 UTF-8 显式解码；
      2) Cookie 由脚本自己从 Set-Cookie 里取并显式回传，不依赖 WebRequestSession，行为可预测。
    所有断言都看真实 HTTP 状态码与响应体；失败以非 0 退出码结束，可直接用于交付验收。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1
    powershell -File scripts\smoke.ps1 -BaseUrl http://localhost:8080 -AdminPassword Admin@12345
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUsername = 'admin',
    [string]$AdminPassword = 'Admin@12345'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$script:Passed = 0
$script:Failed = 0
$script:RefreshCookieName = 'uc_refresh'

function Write-Section([string]$Title) {
    Write-Host ""
    Write-Host "== $Title" -ForegroundColor Cyan
}

function Assert-That {
    param([bool]$Condition, [string]$Message, [string]$Detail = '')
    if ($Condition) {
        $script:Passed++
        Write-Host "  [PASS] $Message" -ForegroundColor Green
    }
    else {
        $script:Failed++
        Write-Host "  [FAIL] $Message $Detail" -ForegroundColor Red
    }
}

function Test-HasText {
    param($Value)
    return -not [string]::IsNullOrWhiteSpace([string]$Value)
}

function ConvertFrom-JsonSafe {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    try { return $Text | ConvertFrom-Json } catch { return $null }
}

function Get-CookieValue {
    param([string]$SetCookieHeader, [string]$Name)
    if (-not $SetCookieHeader) { return $null }
    $match = [regex]::Match($SetCookieHeader, "$Name=([^;]+)")
    if ($match.Success) { return $match.Groups[1].Value }
    return $null
}

# 唯一的 HTTP 出口：显式 UTF-8 解码 + 显式 Cookie，非 2xx 也能拿到 status/body 而不中断脚本。
function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = $null,
        [string]$Cookie = $null,
        [byte[]]$UploadBytes = $null,
        [string]$UploadName = 'avatar.png',
        [string]$UploadContentType = 'image/png',
        [int]$TimeoutSec = 30
    )

    $uri = "$BaseUrl$Path"
    $request = [System.Net.HttpWebRequest]::Create($uri)
    $request.Method = $Method
    $request.Timeout = $TimeoutSec * 1000
    $request.ReadWriteTimeout = $TimeoutSec * 1000
    $request.AllowAutoRedirect = $false
    $request.Proxy = $null
    $request.ServicePoint.Expect100Continue = $false
    $request.UserAgent = 'user-center-smoke/1.0'

    if ($Token) { $request.Headers.Add('Authorization', "Bearer $Token") }
    if ($Cookie) { $request.Headers.Add('Cookie', $Cookie) }

    $payload = $null
    $contentType = $null
    if ($UploadBytes) {
        $boundary = [Guid]::NewGuid().ToString('N')
        $crlf = "`r`n"
        $head = "--$boundary$crlf" +
                "Content-Disposition: form-data; name=`"file`"; filename=`"$UploadName`"$crlf" +
                "Content-Type: $UploadContentType$crlf$crlf"
        $tail = "$crlf--$boundary--$crlf"
        $headBytes = [Text.Encoding]::UTF8.GetBytes($head)
        $tailBytes = [Text.Encoding]::UTF8.GetBytes($tail)
        $ms = New-Object IO.MemoryStream
        $ms.Write($headBytes, 0, $headBytes.Length)
        $ms.Write($UploadBytes, 0, $UploadBytes.Length)
        $ms.Write($tailBytes, 0, $tailBytes.Length)
        $payload = $ms.ToArray()
        $ms.Dispose()
        $contentType = "multipart/form-data; boundary=$boundary"
    }
    elseif ($null -ne $Body) {
        $payload = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6 -Compress))
        $contentType = 'application/json; charset=utf-8'
    }

    if ($payload) {
        $request.ContentType = $contentType
        $request.ContentLength = $payload.Length
        $requestStream = $request.GetRequestStream()
        $requestStream.Write($payload, 0, $payload.Length)
        $requestStream.Dispose()
    }
    elseif ($Method -in @('POST', 'PUT', 'PATCH')) {
        $request.ContentLength = 0
    }

    $response = $null
    try {
        $response = $request.GetResponse()
    }
    catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
    }

    $status = [int]$response.StatusCode
    $text = ''
    $stream = $response.GetResponseStream()
    if ($stream) {
        $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)
        $text = $reader.ReadToEnd()
        $reader.Dispose()
        $stream.Dispose()
    }
    $setCookie = [string]$response.Headers['Set-Cookie']
    $response.Close()

    [pscustomobject]@{
        Status    = $status
        Text      = $text
        Json      = (ConvertFrom-JsonSafe -Text $text)
        SetCookie = $setCookie
        Cookie    = (Get-CookieValue -SetCookieHeader $setCookie -Name $script:RefreshCookieName)
    }
}

$stamp = (Get-Date -Format 'HHmmss') + (Get-Random -Maximum 9999)
$username = "smoke_$stamp"
$email = "$username@example.com"
$password = 'Passw0rd123'
$newPassword = 'NewPassw0rd456'

Write-Host "用户中心冒烟测试 -> $BaseUrl" -ForegroundColor Yellow
Write-Host "测试账号: $username"

# ---------------------------------------------------------------- 0. 服务可用性
Write-Section '0. 服务可用性'
$health = Invoke-Api -Method GET -Path '/actuator/health'
Assert-That ($health.Status -eq 200) 'GET /actuator/health 返回 200' "实际: $($health.Status)"
Assert-That ($health.Json.status -eq 'UP') '健康状态为 UP' "实际: $($health.Json.status)"

# ---------------------------------------------------------------- 1. 注册
Write-Section '1. 注册'
$register = Invoke-Api -Method POST -Path '/api/auth/register' -Body @{
    username = $username; email = $email; password = $password; nickname = '冒烟用户'
}
Assert-That ($register.Status -eq 201) '注册返回 201' "实际: $($register.Status) $($register.Text)"
Assert-That ($register.Json.code -eq 0) '业务码为 0'
Assert-That ($register.Json.data.roles -contains 'USER') '默认角色包含 USER'
Assert-That ($register.Json.data.nickname -eq '冒烟用户') '昵称写入正确（中文）' "实际: $($register.Json.data.nickname)"

$duplicate = Invoke-Api -Method POST -Path '/api/auth/register' -Body @{
    username = $username; email = "other-$email"; password = $password
}
Assert-That ($duplicate.Status -eq 409) '重复用户名返回 409' "实际: $($duplicate.Status)"
Assert-That ($duplicate.Json.code -eq 4001) '重复用户名业务码 4001'
Assert-That (Test-HasText $duplicate.Json.data.username) '返回字段级错误（username）'

$weak = Invoke-Api -Method POST -Path '/api/auth/register' -Body @{
    username = "weak_$stamp"; email = "weak_$email"; password = 'allletters'
}
Assert-That ($weak.Status -eq 400) '弱密码返回 400' "实际: $($weak.Status)"
Assert-That ($weak.Json.code -eq 4006) '弱密码业务码 4006'

# ---------------------------------------------------------------- 2. 登录 / 令牌
Write-Section '2. 登录与令牌'
$login = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{
    username = $username; password = $password; remember = $true
}
Assert-That ($login.Status -eq 200) '登录返回 200' "实际: $($login.Status) $($login.Text)"
$token = $login.Json.data.accessToken
Assert-That (Test-HasText $token) '拿到 access token'
Assert-That ($login.Json.data.expiresIn -gt 0) '返回 expiresIn'
$refreshCookie = $login.Cookie
Assert-That (Test-HasText $refreshCookie) 'refresh token 落在 Cookie 里'
Assert-That ($login.SetCookie -match 'HttpOnly') 'Cookie 带 HttpOnly（前端 JS 读不到）'
Assert-That ($login.SetCookie -match 'SameSite') 'Cookie 带 SameSite'

$me = Invoke-Api -Method GET -Path '/api/auth/me' -Token $token
Assert-That ($me.Json.data.username -eq $username) 'GET /auth/me 返回本人'

$noToken = Invoke-Api -Method GET -Path '/api/auth/me'
Assert-That ($noToken.Status -eq 401) '无令牌访问返回 401' "实际: $($noToken.Status)"
$badToken = Invoke-Api -Method GET -Path '/api/auth/me' -Token 'not.a.real.token'
Assert-That ($badToken.Status -eq 401) '伪造令牌返回 401' "实际: $($badToken.Status)"

$refresh = Invoke-Api -Method POST -Path '/api/auth/refresh' -Cookie "$($script:RefreshCookieName)=$refreshCookie"
Assert-That ($refresh.Status -eq 200) 'refresh 返回 200' "实际: $($refresh.Status) $($refresh.Text)"
Assert-That ($refresh.Json.data.accessToken -ne $token) 'refresh 轮换出新的 access token'
if (Test-HasText $refresh.Json.data.accessToken) { $token = $refresh.Json.data.accessToken }

$reuse = Invoke-Api -Method POST -Path '/api/auth/refresh' -Cookie "$($script:RefreshCookieName)=$refreshCookie"
Assert-That ($reuse.Status -eq 401) '旧 refresh token 已作废（轮换）' "实际: $($reuse.Status)"
if (Test-HasText $refresh.Cookie) { $refreshCookie = $refresh.Cookie }

# ---------------------------------------------------------------- 3. 个人资料
Write-Section '3. 个人资料'
$profile = Invoke-Api -Method PUT -Path '/api/profile' -Token $token -Body @{
    nickname = '冒烟用户🚀'; email = $email; phone = '13700003333'
    gender = 'MALE'; birthday = '1995-05-20'; bio = '这里是简介 ✨'
}
Assert-That ($profile.Status -eq 200) '更新资料返回 200' "实际: $($profile.Status)"
Assert-That ($profile.Json.data.nickname -eq '冒烟用户🚀') 'emoji 昵称原样返回' "实际: $($profile.Json.data.nickname)"
Assert-That ($profile.Json.data.phone -eq '13700003333') '手机号已保存'
Assert-That ($profile.Json.data.bio -eq '这里是简介 ✨') '简介（中英混排 + emoji）已保存' "实际: $($profile.Json.data.bio)"

$badPhone = Invoke-Api -Method PUT -Path '/api/profile' -Token $token -Body @{
    nickname = 'x'; email = $email; phone = '123'
}
Assert-That ($badPhone.Status -eq 400) '非法手机号返回 400' "实际: $($badPhone.Status)"

$clearProfile = Invoke-Api -Method PUT -Path '/api/profile' -Token $token -Body @{
    nickname = '冒烟用户'; email = $email; phone = ''; gender = ''; bio = ''
}
Assert-That ($clearProfile.Status -eq 200) '清空字段的更新返回 200' "实际: $($clearProfile.Status)"
Assert-That ([string]::IsNullOrEmpty([string]$clearProfile.Json.data.phone)) '空字符串可清空手机号'
Assert-That ([string]::IsNullOrEmpty([string]$clearProfile.Json.data.bio)) '空字符串可清空简介'

# ---------------------------------------------------------------- 4. 头像
Write-Section '4. 头像上传'
$pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAF3SURBVHhe7dA7bgMxDAVA3ygnSpPz5M4OVBgIBlpbq8+uBLGYhgX5+B5f3z/PnT0c7CYKcLCbKMDBbqIAB7uJAhyM8vx9nOaOEYYW4EMt3N3LkAIM35O3WnUtwLAjebtWtwIMeAUz1OhSgMGuZJazmgsw0B3MdEZTAQa5k9lKVRdggBmYsURVAR6eiVk/OV2AB2dk5neiAAfveGhmZj8SBTg44oEV+ENOFODgiMtX4A85RQW4eCX+oijAQY5LV+IvigIc5Lh0Jf6iKMCBXLga/9HHAhKXrsRfFAU4yHHpSvxFUYCDHJeuxF8UBTg44uIV+ENOFODgiMtX4A85xQUkHpiZ2Y9EAQ4+8dCMzPxOFOCghAdnYtZPqgpIPDwDM5aoLiAxwJ3MVqqpgMQgdzDTGc0FJAa6klnO6lJAYrArmKFGtwISA47k7VpdC3gxbE/eajWkgBfDt3B3L0ML+M+HSrhjhMsKmFUU4GA3UYCD3UQBDnazfQF/pZsb1OAOsIQAAAAASUVORK5CYII='
$pngBytes = [Convert]::FromBase64String($pngBase64)

$avatar = Invoke-Api -Method POST -Path '/api/profile/avatar' -Token $token -UploadBytes $pngBytes
Assert-That ($avatar.Status -eq 200) '上传头像返回 200' "实际: $($avatar.Status) $($avatar.Text)"
$avatarUrl = $avatar.Json.data.avatarUrl
Assert-That ($avatarUrl -like '/api/files/avatars/*') '返回头像访问路径' "实际: $avatarUrl"
if (Test-HasText $avatarUrl) {
    $avatarFetch = Invoke-Api -Method GET -Path $avatarUrl
    Assert-That ($avatarFetch.Status -eq 200) '头像可公开访问' "实际: $($avatarFetch.Status)"
}

$fakeBytes = [Text.Encoding]::UTF8.GetBytes("<?php echo 'not an image'; ?>")
$badUpload = Invoke-Api -Method POST -Path '/api/profile/avatar' -Token $token -UploadBytes $fakeBytes
Assert-That ($badUpload.Status -eq 400) '伪装成 png 的文本文件被拒（400）' "实际: $($badUpload.Status)"
Assert-That ($badUpload.Json.code -eq 5001) '返回"文件不合法"业务码 5001'

$removeAvatar = Invoke-Api -Method DELETE -Path '/api/profile/avatar' -Token $token
Assert-That ($removeAvatar.Status -eq 200) '删除头像返回 200'

# ---------------------------------------------------------------- 5. 账号安全
Write-Section '5. 账号安全'
$sessions = Invoke-Api -Method GET -Path '/api/security/sessions' -Token $token
Assert-That ($sessions.Status -eq 200) '会话列表返回 200' "实际: $($sessions.Status)"
$sessionList = @($sessions.Json.data)
Assert-That ($sessionList.Count -ge 1) '至少有一个会话' "实际: $($sessionList.Count)"
Assert-That (@($sessionList | Where-Object { $_.current }).Count -eq 1) '恰好标记一个当前会话'
Assert-That (Test-HasText $sessionList[0].deviceName) '识别出设备名' "实际: $($sessionList[0].deviceName)"

$revokeOthers = Invoke-Api -Method DELETE -Path '/api/security/sessions/others' -Token $token
Assert-That ($revokeOthers.Status -eq 200) '退出其他设备返回 200'

$changePwd = Invoke-Api -Method PUT -Path '/api/profile/password' -Token $token -Body @{
    oldPassword = $password; newPassword = $newPassword
}
Assert-That ($changePwd.Status -eq 200) '修改密码返回 200' "实际: $($changePwd.Status) $($changePwd.Text)"
$oldLogin = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ username = $username; password = $password }
Assert-That ($oldLogin.Status -eq 401) '旧密码无法登录' "实际: $($oldLogin.Status)"
$newLogin = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ username = $username; password = $newPassword }
Assert-That ($newLogin.Status -eq 200) '新密码可以登录'
if ($newLogin.Status -eq 200) { $token = $newLogin.Json.data.accessToken }

# ---------------------------------------------------------------- 6. 通知中心
Write-Section '6. 站内通知'
$notifications = Invoke-Api -Method GET -Path '/api/notifications?page=1&size=10' -Token $token
Assert-That ($notifications.Status -eq 200) '通知列表返回 200'
Assert-That ($notifications.Json.data.total -ge 1) '注册后至少有一条欢迎通知'
$unread = Invoke-Api -Method GET -Path '/api/notifications/unread-count' -Token $token
Assert-That ($unread.Json.data.count -ge 1) '未读数 >= 1'
$readAll = Invoke-Api -Method POST -Path '/api/notifications/read-all' -Token $token
Assert-That ($readAll.Status -eq 200) '全部已读返回 200'
$unreadAfter = Invoke-Api -Method GET -Path '/api/notifications/unread-count' -Token $token
Assert-That ($unreadAfter.Json.data.count -eq 0) '全部已读后未读数为 0'

# ---------------------------------------------------------------- 7. 管理后台
Write-Section '7. 管理后台'
$forbidden = Invoke-Api -Method GET -Path '/api/admin/users' -Token $token
Assert-That ($forbidden.Status -eq 403) '普通用户访问管理接口返回 403' "实际: $($forbidden.Status)"

$adminLogin = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{
    username = $AdminUsername; password = $AdminPassword
}
Assert-That ($adminLogin.Status -eq 200) "管理员 $AdminUsername 登录成功" "实际: $($adminLogin.Status) $($adminLogin.Text)"
if ($adminLogin.Status -ne 200) {
    Write-Host "  跳过管理后台用例（管理员登录失败，请检查 uc.admin.seed-password）" -ForegroundColor Yellow
}
else {
    $adminToken = $adminLogin.Json.data.accessToken
    $stats = Invoke-Api -Method GET -Path '/api/admin/stats' -Token $adminToken
    Assert-That ($stats.Status -eq 200) '统计接口返回 200'
    Assert-That ($stats.Json.data.userTotal -ge 1) '统计包含用户总数'

    $userPage = Invoke-Api -Method GET -Path "/api/admin/users?keyword=$username&page=1&size=10" -Token $adminToken
    Assert-That ($userPage.Json.data.total -eq 1) '按关键字精确命中 1 个用户' "实际: $($userPage.Json.data.total)"
    $targetId = $userPage.Json.data.list[0].id

    $disable = Invoke-Api -Method PATCH -Path "/api/admin/users/$targetId/status" -Token $adminToken -Body @{ status = 'DISABLED' }
    Assert-That ($disable.Status -eq 200) '停用用户成功' "实际: $($disable.Status)"
    $disabledLogin = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ username = $username; password = $newPassword }
    Assert-That ($disabledLogin.Status -eq 403) '被停用用户无法登录' "实际: $($disabledLogin.Status)"
    Assert-That ($disabledLogin.Json.code -eq 3002) '返回"账号已禁用"业务码 3002'

    $enable = Invoke-Api -Method PATCH -Path "/api/admin/users/$targetId/status" -Token $adminToken -Body @{ status = 'ACTIVE' }
    Assert-That ($enable.Status -eq 200) '恢复用户成功'

    $selfGuard = Invoke-Api -Method PATCH -Path "/api/admin/users/$($adminLogin.Json.data.user.id)/status" -Token $adminToken -Body @{ status = 'DISABLED' }
    Assert-That ($selfGuard.Status -eq 409) '管理员不能停用自己（409）' "实际: $($selfGuard.Status)"

    $notify = Invoke-Api -Method POST -Path '/api/admin/notifications' -Token $adminToken -Body @{
        userIds = @($targetId); title = '系统维护通知'; content = '今晚 23:00 起维护，预计 30 分钟 🚧'
    }
    Assert-That ($notify.Status -eq 200) '群发通知返回 200' "实际: $($notify.Status)"
    Assert-That ($notify.Json.data -ge 1) '至少有 1 个收件人'

    $audits = Invoke-Api -Method GET -Path '/api/admin/audit-logs?page=1&size=20' -Token $adminToken
    Assert-That ($audits.Status -eq 200) '审计日志可查'
    Assert-That ($audits.Json.data.total -ge 1) '审计日志有内容'
    $actions = (@($audits.Json.data.list) | ForEach-Object { $_.action }) -join ','
    Assert-That ($actions -match 'AUTH_|PROFILE_|PASSWORD_|ADMIN_|SESSION_') '审计动作码可读' "实际: $actions"

    $adminLogout = Invoke-Api -Method POST -Path '/api/auth/logout' -Cookie "$($script:RefreshCookieName)=$($adminLogin.Cookie)"
    Assert-That ($adminLogout.Status -eq 200) '管理员登出返回 200'
}

Write-Host ""
Write-Host "----------------------------------------"
if ($script:Failed -eq 0) {
    Write-Host ("通过 {0} 项，失败 0 项" -f $script:Passed) -ForegroundColor Green
}
else {
    Write-Host ("通过 {0} 项，失败 {1} 项" -f $script:Passed, $script:Failed) -ForegroundColor Red
}
Write-Host "----------------------------------------"

if ($script:Failed -gt 0) { exit 1 } else { exit 0 }
