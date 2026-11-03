<#
.SYNOPSIS
    The test scenarios of the Kubernetes deployment (Phase 23) against the kind clusters: PASS or FAIL per check.

.DESCRIPTION
    access      The shop's page, a product search, a login, the customer's cart and a product photo answer through -Base.
    pod         One api-gateway pod is deleted while requests keep coming: none fails, a new pod replaces it.
    rolling     api-gateway is restarted pod by pod (a "new version"): no request fails during the rollout.
    downstream  cart-service is scaled to 0: the cart answers 5xx quickly, search still works; then it comes back.
    hpa         A load pod calls product-read-service: its HorizontalPodAutoscaler goes above 3 pods (at most 5).
    redis       Redis stops for a moment (scripts\failure-drills.ps1 -Drill redis through -Base).
    cluster     Cluster A's nodes stop: the entry NGINX (http://localhost) keeps answering through cluster B.
                Then cluster A starts again and answers on http://localhost:8281.
    backup      Every database is backed up, order_db is restored into order_db_restored and compared (then dropped).

    Needs the infrastructure, the cluster(s) deployed (.\scripts\k8s-deploy.ps1) and, for "access" through
    http://localhost and for "cluster", the entry NGINX (.\scripts\k8s-entry-nginx.ps1). "cluster" also needs cluster B.
    Results of the last run: docs/kubernetes-deployment-plan.md, "Progress".

.EXAMPLE
    .\scripts\k8s-drills.ps1                                        # every drill, through http://localhost
    .\scripts\k8s-drills.ps1 -Drill rolling -Base http://localhost:8281
    .\scripts\k8s-drills.ps1 -Drill cluster -FailoverPath /          # cluster B runs only the gateway and the frontend
#>
param(
    [ValidateSet("all", "access", "pod", "rolling", "downstream", "hpa", "redis", "cluster", "backup")]
    [string] $Drill = "all",
    [string] $Base = "http://localhost",
    [string] $Context = "kind-grocery-cluster-a",     # the cluster that the pod, rolling, downstream and hpa drills change
    [string] $FailoverPath = "/api/v1/products?q=milk"
)

$script:passed = 0
$script:failed = 0
$searchPath = "/api/v1/products?q=milk"

function Check([string] $Name, [bool] $Ok, [string] $Detail = "") {
    if ($Ok) { $script:passed++; Write-Host "  PASS  $Name $Detail" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red }
}

function Kube { kubectl --context $Context -n grocery @args }

# The HTTP status of one GET (0 = no answer at all)
function Get-Status([string] $Url, [hashtable] $Headers = @{}) {
    try { return (Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 20 -Headers $Headers).StatusCode }
    catch { if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode } else { return 0 } }
}

# Sends GET requests while $Running returns true (at most $MaxSeconds) and counts the answers that were not 200
function Measure-Requests([string] $Url, [scriptblock] $Running, [int] $MaxSeconds) {
    $result = @{ Total = 0; Failed = 0; Codes = @() }
    $deadline = (Get-Date).AddSeconds($MaxSeconds)
    while ((Get-Date) -lt $deadline -and (& $Running)) {
        $code = Get-Status $Url
        $result.Total++
        if ($code -ne 200) { $result.Failed++; $result.Codes += $code }
        Start-Sleep -Milliseconds 300
    }
    return $result
}

function Get-Token([string] $Email) {
    $body = @{ email = $Email; password = "Password@123" } | ConvertTo-Json
    return (Invoke-RestMethod -Method Post -Uri "$Base/api/v1/auth/login" -ContentType "application/json" -Body $body).data.accessToken
}

function Wait-Available([string] $Deployment, [int] $Seconds = 300) {
    Kube wait --for=condition=available "deployment/$Deployment" "--timeout=${Seconds}s" | Out-Null
    return $LASTEXITCODE -eq 0
}

function Invoke-AccessDrill {
    Write-Host "`nDrill 1: normal access through $Base" -ForegroundColor Cyan
    $page = Invoke-WebRequest -Uri "$Base/" -UseBasicParsing -TimeoutSec 20
    Check "the shop's page" ($page.StatusCode -eq 200 -and $page.Content -match "<title>Fresh Grocery</title>")
    $search = Invoke-RestMethod -Uri "$Base$searchPath" -TimeoutSec 20
    Check "product search" ($search.data.totalElements -gt 0) "($($search.data.totalElements) products for 'milk')"
    $token = Get-Token "customer1@grocery.com"
    Check "login of customer1" ([bool]$token)
    Check "customer1's cart" ((Get-Status "$Base/api/v1/cart" @{ Authorization = "Bearer $token" }) -eq 200)
    # A photo in product-service\uploads (the folder the clusters mount) must come back through /media
    $uploads = Join-Path $PSScriptRoot "..\product-service\uploads"
    $file = Get-ChildItem -Recurse -File $uploads -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($file) {
        $url = "$Base/media/" + $file.FullName.Substring((Resolve-Path $uploads).Path.Length + 1).Replace('\', '/')
        $photo = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 20
        Check "a product photo from the shared folder" ($photo.StatusCode -eq 200 -and $photo.Headers["Content-Type"] -like "image/*") $url
    } else {
        Check "a product photo from the shared folder" $false "(product-service\uploads is empty)"
    }
}

function Invoke-PodDrill {
    Write-Host "`nDrill 2: an api-gateway pod disappears" -ForegroundColor Cyan
    $pod = (Kube get pods -l app=api-gateway -o name | Select-Object -First 1)
    Kube delete $pod --wait=false | Out-Null
    $result = Measure-Requests "$Base$searchPath" { $true } 40
    Check "no failed request while the pod stops" ($result.Failed -eq 0) "($($result.Total) requests, $($result.Failed) failed $($result.Codes -join ','))"
    Check "a new pod replaces it" (Wait-Available "api-gateway") "($(Kube get deployment api-gateway -o jsonpath='{.status.readyReplicas}') ready)"
}

function Invoke-RollingDrill {
    Write-Host "`nDrill 3: rolling update of api-gateway" -ForegroundColor Cyan
    Kube rollout restart deployment/api-gateway | Out-Null
    Start-Sleep -Seconds 2
    $rolling = { (Kube rollout status deployment/api-gateway --timeout=1s 2>&1 | Out-String) -notmatch "successfully rolled out" }
    $started = Get-Date
    $result = Measure-Requests "$Base$searchPath" $rolling 600
    $took = [int]((Get-Date) - $started).TotalSeconds
    Check "the rollout finished" (-not (& $rolling)) "(after $took s)"
    Check "no failed request during the rollout" ($result.Failed -eq 0) "($($result.Total) requests, $($result.Failed) failed $($result.Codes -join ','))"
}

function Invoke-DownstreamDrill {
    Write-Host "`nDrill 4: cart-service is unavailable" -ForegroundColor Cyan
    $token = Get-Token "customer1@grocery.com"
    $auth = @{ Authorization = "Bearer $token" }
    Kube scale deployment/cart-service --replicas=0 | Out-Null
    Kube wait --for=delete pod -l app=cart-service --timeout=90s | Out-Null
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $cart = Get-Status "$Base/api/v1/cart" $auth
    $seconds = [math]::Round($watch.Elapsed.TotalSeconds, 1)
    Check "the cart answers with an error, quickly" ($cart -ge 500 -and $seconds -lt 15) "(HTTP $cart after $seconds s)"
    Check "search still works" ((Get-Status "$Base$searchPath") -eq 200)
    Kube scale deployment/cart-service --replicas=1 | Out-Null
    Check "cart-service is back" (Wait-Available "cart-service")
    $deadline = (Get-Date).AddSeconds(60)
    do { $cart = Get-Status "$Base/api/v1/cart" $auth; if ($cart -eq 200) { break }; Start-Sleep -Seconds 3 } while ((Get-Date) -lt $deadline)
    Check "the cart works again" ($cart -eq 200)
}

function Invoke-HpaDrill {
    Write-Host "`nDrill 5: CPU load scales product-read-service up" -ForegroundColor Cyan
    # 8 endless request loops inside the cluster, straight to product-read-service (the gateway's rate limit is not in the way)
    $loop = "for i in 1 2 3 4 5 6 7 8; do (while true; do wget -q -O /dev/null 'http://product-read-service/api/v1/products?q=milk'; done) & done; wait"
    Kube run hpa-load --image=busybox:1.37 --restart=Never --command -- /bin/sh -c $loop | Out-Null
    $most = 3
    $deadline = (Get-Date).AddMinutes(5)
    do {
        Start-Sleep -Seconds 15
        $replicas = [int](Kube get hpa product-read-service -o jsonpath='{.status.desiredReplicas}')
        $cpu = Kube get hpa product-read-service -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}'
        Write-Host "        CPU $cpu % of the request, $replicas pods wanted"
        if ($replicas -gt $most) { $most = $replicas }
    } while ($most -lt 5 -and (Get-Date) -lt $deadline)
    Kube delete pod hpa-load --wait=false | Out-Null
    Check "the autoscaler added pods" ($most -gt 3) "(up to $most of at most 5; it goes back to 3 about 5 minutes after the load)"
}

function Invoke-RedisDrill {
    Write-Host "`nDrill 6: Redis is down (failure-drills.ps1 through $Base)" -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "failure-drills.ps1") -Drill redis -Gateway $Base
    Check "the Redis drill passed" ($LASTEXITCODE -eq 0)
}

function Invoke-ClusterDrill {
    Write-Host "`nDrill 7: cluster A stops, the entry NGINX uses cluster B" -ForegroundColor Cyan
    $nodes = @("grocery-cluster-a-control-plane", "grocery-cluster-a-worker", "grocery-cluster-a-worker2")
    docker stop $nodes | Out-Null
    $result = Measure-Requests "$Base$FailoverPath" { $true } 30
    Check "the entry NGINX answers while cluster A is down" ($result.Failed -eq 0) "($($result.Total) requests to $FailoverPath, $($result.Failed) failed $($result.Codes -join ','))"
    docker start $nodes | Out-Null
    $started = Get-Date
    do { Start-Sleep -Seconds 10; $code = Get-Status "http://localhost:8281$searchPath" } while ($code -ne 200 -and ((Get-Date) - $started).TotalMinutes -lt 10)
    Check "cluster A answers again after its restart" ($code -eq 200) "(after $([int]((Get-Date) - $started).TotalSeconds) s)"
}

function Invoke-BackupDrill {
    Write-Host "`nDrill 8: back up, restore order_db into a new database, compare" -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "backup-databases.ps1")
    Check "backup of every database" ($LASTEXITCODE -eq 0)
    & (Join-Path $PSScriptRoot "restore-database.ps1") -Database order_db
    Check "restore of order_db, row counts equal" ($LASTEXITCODE -eq 0)
    $envFile = Join-Path $PSScriptRoot "..\infrastructure\.env"
    $rootPassword = ((Get-Content $envFile) -match '^MYSQL_ROOT_PASSWORD=') -replace '^MYSQL_ROOT_PASSWORD=', ''
    docker exec grocery-mysql mysql -uroot "-p$rootPassword" -e "DROP DATABASE IF EXISTS order_db_restored" 2>$null
}

if ($Drill -in "all", "access") { Invoke-AccessDrill }
if ($Drill -in "all", "pod") { Invoke-PodDrill }
if ($Drill -in "all", "rolling") { Invoke-RollingDrill }
if ($Drill -in "all", "downstream") { Invoke-DownstreamDrill }
if ($Drill -in "all", "hpa") { Invoke-HpaDrill }
if ($Drill -in "all", "redis") { Invoke-RedisDrill }
if ($Drill -in "all", "cluster") { Invoke-ClusterDrill }
if ($Drill -in "all", "backup") { Invoke-BackupDrill }

Write-Host "`n$script:passed passed, $script:failed failed" -ForegroundColor $(if ($script:failed -eq 0) { "Green" } else { "Red" })
if ($script:failed -gt 0) { exit 1 }
