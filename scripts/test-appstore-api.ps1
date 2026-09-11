# 产销品加载 AI 应用 · 接口联调测试脚本
# 用法: .\scripts\test-appstore-api.ps1 [-BaseUrl http://localhost:6174/api/v1/appstore]
# 说明: 按接口说明书逐个验证 11 个接口（含正向/反向/幂等/异步场景），全部免 Token 直连。
#       Mock 数据为内存态，服务重启即复位，可反复重放。

param(
    [string]$BaseUrl = "http://localhost:6174/api/v1/appstore"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$script:passed = 0
$script:failed = 0
$script:failures = @()

function Assert-Api {
    param([string]$Name, $Response, [int]$ExpectCode)
    $code = [int]$Response.code
    if ($code -eq $ExpectCode) {
        $script:passed++
        Write-Host "  PASS  $Name (code=$code)" -ForegroundColor Green
        return $true
    }
    else {
        $script:failed++
        Write-Host "  FAIL  $Name 期望 code=$ExpectCode 实际 code=$code msg=$($Response.msg)" -ForegroundColor Red
        return $false
    }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body)
    $uri = "$BaseUrl$Path"
    if ($Body) {
        $json = $Body | ConvertTo-Json -Depth 6
        return Invoke-RestMethod -Uri $uri -Method $Method -ContentType "application/json; charset=utf-8" -Body $json -TimeoutSec 30
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -TimeoutSec 30
}

Write-Host "=============================================="
Write-Host "产销品加载 AI 应用接口联调  BaseUrl: $BaseUrl"
Write-Host "=============================================="

# ---------- 接口1：产销品配置查询 ----------
Write-Host "`n[1] query_product_config"
$r = Invoke-Api GET "/products/config/query?keyword=%E6%B5%81%E9%87%8F&status=online"
if (Assert-Api "查询命中" $r 0) {
    if ($r.total -ge 1 -and $r.list.Count -ge 1) { $script:passed++; Write-Host "  PASS  total=$($r.total) list 非空" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  total/list 异常" -ForegroundColor Red }
}

# ---------- 接口2：CRM 配置数据生成 ----------
Write-Host "`n[2] gen_crm_config"
$crmName = "POC自动化测试包" + (Get-Date -Format "HHmmss")
$r = Invoke-Api POST "/crm/config/generate" @{
    product_name    = $crmName
    fee_json        = '{"monthly_fee":39}'
    sale_scope      = "anhui-all"
    effect_date     = "2026-10-01"
    idempotency_key = "test-crm-$crmName"
}
if (Assert-Api "正常生成" $r 0) {
    $script:crmConfigJson = $r.crm_config_json
    $script:productId = ($r.crm_config_json | ConvertFrom-Json).product_id
}
# 幂等：同 key 两次请求返回相同 crm_config_id
$r2 = Invoke-Api POST "/crm/config/generate" @{
    product_name    = $crmName
    fee_json        = '{"monthly_fee":39}'
    sale_scope      = "anhui-all"
    idempotency_key = "test-crm-$crmName"
}
if ($r2.crm_config_id -eq $r.crm_config_id) { $script:passed++; Write-Host "  PASS  幂等回放 ($($r.crm_config_id))" -ForegroundColor Green }
else { $script:failed++; Write-Host "  FAIL  幂等失效: $($r.crm_config_id) vs $($r2.crm_config_id)" -ForegroundColor Red }
# 缺参
$r = Invoke-Api POST "/crm/config/generate" @{ product_name = "x" }
Assert-Api "缺参拒绝" $r 1001

# ---------- 接口3：计费配置数据生成 ----------
Write-Host "`n[3] gen_billing_config"
$r = Invoke-Api POST "/billing/config/generate" @{
    product_id     = "P20260001"
    fee_json       = '{"monthly_fee":29}'
    discount_rules = @(
        @{ discount_type = "limited_time" },
        @{ discount_type = "long_term" }
    )
}
if (Assert-Api "正常生成" $r 0) { $script:billingConfigJson = $r.billing_config_json }
$r = Invoke-Api POST "/billing/config/generate" @{ product_id = "P20260001" }
Assert-Api "缺 fee_json 拒绝" $r 2001

# ---------- 接口4：计费规则校验 ----------
Write-Host "`n[4] check_billing_rule"
$r = Invoke-Api POST "/rules/verify" @{
    billing_config_json = $script:billingConfigJson
    check_scene         = "all"
}
if (Assert-Api "互斥优惠检出" $r 0) {
    $hasConflict = $r.risk_list | Where-Object { $_.risk_type -eq "overlap_conflict" }
    if ($r.pass -eq 1 -and $hasConflict) { $script:passed++; Write-Host "  PASS  pass=1 且含 overlap_conflict" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  风险未检出" -ForegroundColor Red }
}
$r = Invoke-Api POST "/rules/verify" @{ billing_config_json = "not-json" }
Assert-Api "非法 JSON 拒绝" $r 3001

# ---------- 接口5：配置规格稽核 ----------
Write-Host "`n[5] check_product_spec"
$goodCrm = '{"product_name":"合规测试包","product_desc":"测试","fee_json":"{}","sale_scope":"anhui-all","effect_date":"2026-10-01"}'
$goodBill = '{"product_id":"P20260001","effect_date":"2026-10-01"}'
$r = Invoke-Api POST "/spec/audit" @{ crm_config_json = $goodCrm; billing_config_json = $goodBill }
Assert-Api "合规配置通过" $r 0
if ($r.pass -eq 0) { $script:passed++; Write-Host "  PASS  pass=0" -ForegroundColor Green }
else { $script:failed++; Write-Host "  FAIL  合规配置被误判" -ForegroundColor Red }

$badCrm = '{"product_name":"非法!名称@超长超长超长","fee_json":"{}","sale_scope":"mars","effect_date":"2027-01-01","expire_date":"2026-01-01"}'
$r = Invoke-Api POST "/spec/audit" @{ crm_config_json = $badCrm; billing_config_json = $goodBill }
if (Assert-Api "违规配置检出" $r 0) {
    if ($r.pass -eq 1 -and $r.error_list.Count -ge 3) { $script:passed++; Write-Host "  PASS  pass=1, errors=$($r.error_list.Count)" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  错误检出不足" -ForegroundColor Red }
}

# ---------- 接口6：测试用例生成 ----------
Write-Host "`n[6] gen_test_cases"
$crmJson = '{"product_name":"畅享流量包","fee_json":"{\"monthly_fee\":29}","sale_scope":"anhui-all","effect_date":"2026-10-01"}'
$billJson = $script:billingConfigJson
$r = Invoke-Api POST "/cases/generate" @{ crm_config_json = $crmJson; billing_config_json = $billJson; case_type = "all" }
if (Assert-Api "生成用例" $r 0) {
    if ($r.case_count -eq $r.case_ids.Count -and $r.case_count -ge 4) { $script:passed++; Write-Host "  PASS  case_count=$($r.case_count)" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  case_count 与 case_ids 不一致" -ForegroundColor Red }
    $script:caseIds = @($r.case_ids)
}

# ---------- 接口7：测试用例执行 ----------
Write-Host "`n[7] run_test_cases"
$r = Invoke-Api POST "/cases/execute" @{
    case_ids     = $script:caseIds[0..2]
    env          = "sit"
    execute_mode = "sync"
}
if (Assert-Api "sync 执行" $r 0) {
    if ($r.total -eq 3) { $script:passed++; Write-Host "  PASS  total=3 passed=$($r.passed) failed=$($r.failed)" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  total 不符" -ForegroundColor Red }
}
$r = Invoke-Api POST "/cases/execute" @{ case_ids = $script:caseIds[0..1]; env = "prod"; execute_mode = "sync" }
Assert-Api "prod 环境拒绝" $r 4002

$r = Invoke-Api POST "/cases/execute" @{ case_ids = $script:caseIds[3..4]; env = "uat"; execute_mode = "async" }
if (Assert-Api "async 提交" $r 0) {
    $taskId = $r.task_id
    $finished = $false
    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep -Milliseconds 500
        $t = Invoke-Api GET "/tasks/$taskId"
        if ($t.status -eq "finished") { $finished = $true; break }
    }
    if ($finished) { $script:passed++; Write-Host "  PASS  任务回查 finished (task_id=$taskId)" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  任务超时未完成" -ForegroundColor Red }
}
$r = Invoke-Api GET "/tasks/NOT-EXIST"
Assert-Api "任务不存在" $r 4004

# ---------- 接口8：受理验证 ----------
Write-Host "`n[8] verify_acceptance"
$r = Invoke-Api POST "/order/verify" @{ product_id = "P20260001"; verify_type = "new" }
if (Assert-Api "new 受理" $r 0) {
    if ($r.pass -eq 0 -and $r.order_id) { $script:passed++; Write-Host "  PASS  order_id=$($r.order_id)" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  pass/order_id 异常" -ForegroundColor Red }
}
$r = Invoke-Api POST "/order/verify" @{ product_id = "P-NOPE"; verify_type = "new" }
Assert-Api "产品不存在" $r 8003
$r = Invoke-Api POST "/order/verify" @{ product_id = "P20260001"; verify_type = "delete" }
Assert-Api "非法 verify_type" $r 8002

# ---------- 接口9：上线审批推送 ----------
Write-Host "`n[9] submit_release_approval"
$r = Invoke-Api POST "/approval/submit" @{
    product_id     = "P20260001"
    report         = "测试与稽核报告：全部通过"
    approval_flow  = "standard"
    idempotency_key = "test-ap-$crmName"
}
if (Assert-Api "提交审批" $r 0) {
    if ($r.status -eq "submitted") { $script:passed++; Write-Host "  PASS  approval_id=$($r.approval_id)" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  status 异常" -ForegroundColor Red }
}
$r = Invoke-Api POST "/approval/submit" @{ product_id = "P20260001" }
Assert-Api "缺报告拒绝" $r 9001

# ---------- 接口11：异常告警推送（先于接口10，供监控回显） ----------
Write-Host "`n[11] send_alert"
$r = Invoke-Api POST "/alert/send" @{
    product_id  = "P20260001"
    alarm_level = "high"
    content     = "联调自动测试告警"
}
if (Assert-Api "推送告警" $r 0) {
    if ($r.status -eq "sent" -and $r.alert_id) { $script:passed++; Write-Host "  PASS  alert_id=$($r.alert_id)" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  status/alert_id 异常" -ForegroundColor Red }
    $script:lastAlertId = $r.alert_id
}
$r = Invoke-Api POST "/alert/send" @{ product_id = "P20260001"; alarm_level = "fatal"; content = "x" }
Assert-Api "非法告警级别" $r 11002

# ---------- 接口10：产销品监控查询 ----------
Write-Host "`n[10] query_product_monitor"
$r = Invoke-Api GET "/product/monitor?product_id=P20260001&date_range=2026-09-01~2026-09-10&metric=all"
if (Assert-Api "监控查询" $r 0) {
    $hasAlarm = $r.alarm_list | Where-Object { $_.alarm_id -eq $script:lastAlertId }
    if ($r.order_count -gt 0 -and $hasAlarm) { $script:passed++; Write-Host "  PASS  order=$($r.order_count) 告警回显 OK（11→10 闭环）" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  指标或告警回显异常" -ForegroundColor Red }
}
$r = Invoke-Api GET "/product/monitor?product_id=P-NOPE"
Assert-Api "产品不存在" $r 10002

# ---------- 汇总 ----------
Write-Host "`n=============================================="
Write-Host "联调完成: PASS=$($script:passed) FAIL=$($script:failed)" -ForegroundColor $(if ($script:failed -eq 0) { "Green" } else { "Red" })
Write-Host "=============================================="
if ($script:failed -gt 0) { exit 1 }
