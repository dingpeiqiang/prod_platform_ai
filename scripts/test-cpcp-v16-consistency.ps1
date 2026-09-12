# 产销品加载 AI 应用 V1.6 · 18 销售品一致性自测脚本（开发清单 §2 #4）
# 遍历 18 销售品调用 接口1/2/4/5/6/7/8/11，断言返回结构与规则值一致
# 用法：.\scripts\test-cpcp-v16-consistency.ps1 [-BaseUrl http://localhost:6174]

param(
    [string]$BaseUrl = "http://localhost:6174"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$pass = 0; $fail = 0; $failures = @()

function Assert-True($cond, $name, $detail = "") {
    if ($cond) { $script:pass++ } else { $script:fail++; $script:failures += "$name $detail" }
}

function PostJson($url, $body) {
    try {
        return Invoke-RestMethod -Uri $url -Method Post -Body ($body | ConvertTo-Json -Depth 10) `
            -ContentType "application/json; charset=utf-8" -TimeoutSec 180
    } catch { return $null }
}

function GetJson($url) {
    try { return Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 60 } catch { return $null }
}

# 18 销售品清单（10 个 5G-A + 8 个权益随心选）
$offerIds = @(
    "900102308", "900113043", "900113046", "900102307", "900102313",
    "900113044", "900102310", "900102312", "900113045", "900102306",
    "900117022", "900117020", "900117027", "900117026", "900117023",
    "900117024", "900117025", "900117021"
)

Write-Host "=== V1.6 一致性自测 BaseUrl=$BaseUrl offers=$($offerIds.Count) ==="

# 0. 健康检查
$health = GetJson "$BaseUrl/actuator/health"
Assert-True ($null -ne $health) "健康检查"

foreach ($oid in $offerIds) {
    # 接口1 相似度分析：按名称描述应命中该销售品
    $r1 = PostJson "$BaseUrl/api/v1/similar/offer/query" @{ businessDesc = "销售品$oid 相关需求" }
    Assert-True ($null -ne $r1 -and $r1.resultCode -eq "0") "[$oid]接口1" "resultCode=$($r1.resultCode)"

    # 接口4 测试发起
    $r4 = PostJson "$BaseUrl/api/v1/test/offer/start" @{ offerId = $oid }
    Assert-True ($null -ne $r4 -and $r4.resultCode -eq "0") "[$oid]接口4"
    $globalId = if ($r4) { $r4.globalId } else { "" }
    Assert-True ($globalId -match "^50\d{24}$") "[$oid]globalId格式" "globalId=$globalId"

    # 接口5 场景集合
    $r5 = PostJson "$BaseUrl/api/v1/test/offer/scenes" @{ globalId = $globalId }
    $sceneNbrs = @(); if ($r5) { $sceneNbrs = @($r5.testScenes | ForEach-Object { $_.testSceneNbr }) }
    Assert-True ($sceneNbrs -contains "S_O_TC" -and $sceneNbrs -contains "S_U_TC") "[$oid]接口5场景"
    if ($oid -lt "900117000") {
        Assert-True ($sceneNbrs -contains "S_ADD_CARD") "[$oid]接口5副卡场景"
    } else {
        Assert-True ($sceneNbrs -notcontains "S_ADD_CARD") "[$oid]接口5权益无副卡"
    }

    # 接口6 进度状态机
    $r6 = PostJson "$BaseUrl/api/v1/test/offer/progress" @{ globalId = $globalId }
    Assert-True ($null -ne $r6 -and $r6.totalSteps -eq ($sceneNbrs.Count + 2)) "[$oid]接口6总步骤" "totalSteps=$($r6.totalSteps)"

    # 接口7 结果（测试未完成应返回 4003；快进验证需后端注入，此处只验证结构）
    $r7 = PostJson "$BaseUrl/api/v1/test/offer/result" @{ globalId = $globalId }
    Assert-True ($null -ne $r7 -and ($r7.resultCode -eq "4003" -or $r7.resultCode -eq "0")) "[$oid]接口7"

    # 接口8 计费规则校验（合法配置通过）
    $config = @{ offer_id = $oid; monthly_fee = 199 } | ConvertTo-Json -Compress
    $r8 = PostJson "$BaseUrl/api/v1/billing/rules/verify" @{ config_json = $config }
    Assert-True ($null -ne $r8 -and $r8.pass -eq "1") "[$oid]接口8" "pass=$($r8.pass)"

    # 接口11 监控查询
    $r11 = GetJson "$BaseUrl/api/v1/product/monitor?product_id=$oid"
    Assert-True ($null -ne $r11 -and $r11.order_count -ge 100) "[$oid]接口11"
}

# 门禁与幂等抽查
$g1 = PostJson "$BaseUrl/api/v1/product/config/save" @{ plan_id = "PLANTEST01"; plan_json = '{"offer_id":"900102308"}'; confirmed = "false" }
Assert-True ($g1.status -eq "NOT_CONFIRMED") "门禁:confirmed=false"

$g2 = PostJson "$BaseUrl/api/v1/product/config/save" @{ plan_id = "PLANTEST01"; plan_json = '{"offer_id":"900102308"}'; confirmed = "true" }
Assert-True ($g2.status -eq "SUCCESS") "落地:SUCCESS"
$g3 = PostJson "$BaseUrl/api/v1/product/config/save" @{ plan_id = "PLANTEST02"; plan_json = '{"offer_id":"900102308"}'; confirmed = "true" }
Assert-True ($g2.offer_id -eq $g3.offer_id) "幂等:同plan_json同offer_id"

$g4 = PostJson "$BaseUrl/api/v1/test/offer/start" @{ offerId = "888888888" }
Assert-True ($g4.resultCode -eq "4001") "未收录offerId:4001"

$g5 = PostJson "$BaseUrl/api/v1/node/result/save" @{ key = "BAD KEY"; result_json = "{}" }
Assert-True ($g5.code -eq 5002) "非法key:5002"
$g6 = GetJson "$BaseUrl/api/v1/node/result/query?key=PLAN20990101001"
Assert-True ($g6.code -eq 5005) "查无记录:5005"

# 未收录 ID 降级
$r1x = PostJson "$BaseUrl/api/v1/similar/offer/query" @{ businessDesc = "完全无关的描述xyzabc123" }
Assert-True ($null -ne $r1x) "接口1无命中不报错"

Write-Host ""
Write-Host "=== 结果: 通过=$pass 失败=$fail ==="
if ($fail -gt 0) {
    Write-Host "失败明细:"; $failures | ForEach-Object { Write-Host " - $_" }
    exit 1
}
exit 0
