<#
.SYNOPSIS
    Failure drills on the running local platform: stop one piece, check that no event is lost, start it again.

.DESCRIPTION
    Prints PASS or FAIL for every check and the totals at the end (exit code 1 when a check failed).

    connect     Kafka Connect (Debezium) is stopped. customer1 places an order: it stays PENDING and its OrderCreated
                waits in the outbox table. Kafka Connect starts again: the event is published, the saga confirms the order.
    kafka       The Kafka broker is stopped. A new customer can still register: the UserRegistered event waits in the
                outbox table. Kafka starts again: customer-service creates the customer profile from the event, and
                every Debezium connector runs again.
    deadletter  A message nobody can read is sent to grocery.platform.events (only cart-service reads that topic): it
                lands in grocery.platform.events.DLT. The super admin replays it through the API: it is marked as
                replayed, goes back to its topic and, unreadable on purpose, fails into the DLT again. That proves the
                whole round trip; a second replay of the same message is refused.

    dbpool      (Phase 19e) A MySQL session locks customer1's profile row for 20 s, then 12 profile updates arrive at
                customer-service (pool of 5 connections) at once. 5 of them hold every connection while they wait for
                the lock; the other 7 must get 503 "busy" after about 5 s (hikari.connection-timeout) instead of
                hanging. After the lock is gone the 5 finish (one saves the profile, the other 4 get 409 because it
                changed meanwhile: optimistic locking), the pool works again and the timeouts were counted
                (alert DatabaseConnectionTimeouts). customer1's name is restored at the end.
    redis       (Phase 19e) Redis is stopped for a moment. The gateway still answers at once (rate limiting lets
                requests through), "go online" of a delivery partner answers 503, and after Redis is back the rate
                limit counts again and the partner can go online. Stops Redis for everyone using it for about 15 s.
    Needs the whole platform running (.\scripts\start-infra.ps1, the connectors and the 16 services) and takes about
    5 minutes. Results of the last run: docs/resilience.md, "Failure drills".

    Leaves local test data behind: one cash-on-delivery order of customer1 at Fresh Mart (connect), a customer account
    drill.<time>@example.com (kafka), the unreadable message in grocery.platform.events and two copies of it in
    grocery.platform.events.DLT (deadletter). The alert KafkaEventsMovedToDeadLetterTopic may send an email.

.EXAMPLE
    .\scripts\failure-drills.ps1                       # all three
    .\scripts\failure-drills.ps1 -Drill deadletter     # only one: connect, kafka, deadletter, dbpool or redis
#>
param(
    [ValidateSet("all", "connect", "kafka", "deadletter", "dbpool", "redis")] [string] $Drill = "all",
    [string] $Gateway = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$script:passed = 0
$script:failed = 0
$envFile = Join-Path $PSScriptRoot "..\infrastructure\.env"
$mysqlRootPassword = ((Get-Content $envFile) -match '^MYSQL_ROOT_PASSWORD=') -replace '^MYSQL_ROOT_PASSWORD=', ''

function Invoke-Api([string] $Method, [string] $Path, [string] $Token, $Body = $null) {
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $arguments = @{ Method = $Method; Uri = "$Gateway$Path"; Headers = $headers; ContentType = "application/json" }
    if ($null -ne $Body) { $arguments["Body"] = ($Body | ConvertTo-Json -Depth 5) }
    try {
        return (Invoke-RestMethod @arguments).data
    } catch {
        $message = if ($_.ErrorDetails.Message) { ($_.ErrorDetails.Message | ConvertFrom-Json).message } else { $_.Exception.Message }
        throw "$Method $Path failed: $message"
    }
}

function Get-Token([string] $Email) {
    return (Invoke-Api POST "/api/v1/auth/login" $null @{ email = $Email; password = "Password@123" }).accessToken
}

function Check([string] $Name, [bool] $Ok, [string] $Detail = "") {
    if ($Ok) { $script:passed++; Write-Host "  PASS  $Name" -ForegroundColor Green -NoNewline }
    else { $script:failed++; Write-Host "  FAIL  $Name" -ForegroundColor Red -NoNewline }
    if ($Detail) { Write-Host "  ($Detail)" } else { Write-Host "" }
}

# Runs $Condition every 2 s until it returns something (errors count as "not yet");
# returns the seconds it took, or $null after $Seconds.
function Wait-Seconds([scriptblock] $Condition, [int] $Seconds) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $Seconds) {
        try { if (& $Condition) { return [int] $watch.Elapsed.TotalSeconds } } catch { }
        Start-Sleep -Seconds 2
    }
    return $null
}

function Invoke-MySql([string] $Query) {
    return ($Query | docker exec -i -e MYSQL_PWD=$mysqlRootPassword grocery-mysql mysql -uroot -N -B)
}

function Test-Healthy([string] $Container) {
    return (docker inspect -f "{{.State.Health.Status}}" $Container) -eq "healthy"
}

function Test-ConnectorsRunning {
    $connectors = Invoke-RestMethod -Uri "http://localhost:8083/connectors?expand=status" -TimeoutSec 5
    $names = @($connectors.PSObject.Properties.Name)
    if ($names.Count -eq 0) { return $false }
    foreach ($name in $names) {
        $status = $connectors.$name.status
        if ($status.connector.state -ne "RUNNING" -or @($status.tasks | Where-Object { $_.state -ne "RUNNING" }).Count -gt 0) { return $false }
    }
    return $true
}

function Seconds($Took) { if ($null -eq $Took) { "not within the time limit" } else { "after $Took s" } }

# ------------------------------------------------------------------------------------------------------------------
function Invoke-ConnectDrill {
    Write-Host "`nDrill 1: Kafka Connect (Debezium) is down" -ForegroundColor Cyan
    $customer = Get-Token "customer1@grocery.com"
    docker stop grocery-kafka-connect | Out-Null
    try {
        Invoke-Api POST "/api/v1/cart/items?replaceCart=true" $customer @{ productId = 201; quantity = 2 } | Out-Null
        $order = Invoke-Api POST "/api/v1/orders" $customer @{ addressId = 1; paymentMethod = "COD" }
        Write-Host "  order $($order.id) placed; waiting 20 s"
        Start-Sleep -Seconds 20
        $status = (Invoke-Api GET "/api/v1/orders/$($order.id)" $customer).status
        Check "the order waits while nothing is published" ($status -eq "PENDING") "status $status"
        $waiting = Invoke-MySql "SELECT COUNT(*) FROM order_db.outbox_event WHERE aggregate_id = '$($order.id)' AND event_type = 'OrderCreated';"
        Check "its OrderCreated waits in the outbox table" ([int] $waiting -eq 1)
    } finally {
        docker start grocery-kafka-connect | Out-Null
        Write-Host "  Kafka Connect started again"
    }
    $took = Wait-Seconds { (Invoke-Api GET "/api/v1/orders/$($order.id)" $customer).status -eq "CONFIRMED" } 240
    Check "the event is published and the saga confirms the order" ($null -ne $took) (Seconds $took)
}

function Invoke-KafkaDrill {
    Write-Host "`nDrill 2: the Kafka broker is down" -ForegroundColor Cyan
    $stamp = Get-Date -Format "MMddHHmmss"
    $email = "drill.$stamp@example.com"
    docker stop grocery-kafka | Out-Null
    try {
        $registered = Invoke-Api POST "/api/v1/auth/register" $null @{ fullName = "Failure Drill"; email = $email
            phone = "9" + $stamp.Substring(1); password = "Password@123"; role = "CUSTOMER" }
        Check "a new customer can register" ($null -ne $registered.accessToken) $email
        $waiting = Invoke-MySql ("SELECT COUNT(*) FROM auth_db.outbox_event e JOIN auth_db.users u ON e.aggregate_id = u.id " +
                "WHERE u.email = '$email' AND e.event_type = 'UserRegistered';")
        Check "the UserRegistered event waits in the outbox table" ([int] $waiting -eq 1)
    } finally {
        docker start grocery-kafka | Out-Null
        Write-Host "  Kafka started again"
    }
    $took = Wait-Seconds { Test-Healthy "grocery-kafka" } 120
    Check "Kafka is healthy again" ($null -ne $took) (Seconds $took)
    $took = Wait-Seconds { $null -ne (Invoke-Api GET "/api/v1/customers/me" $registered.accessToken) } 240
    Check "customer-service created the profile from the event" ($null -ne $took) (Seconds $took)
    $took = Wait-Seconds { Test-ConnectorsRunning } 180
    Check "every Debezium connector runs again" ($null -ne $took) (Seconds $took)
}

function Invoke-DeadLetterDrill {
    Write-Host "`nDrill 3: an event that cannot be handled, replayed from the dead-letter topic" -ForegroundColor Cyan
    $superAdmin = Get-Token "superadmin@grocery.com"
    $deadLetters = "/api/v1/super-admin/dead-letters/grocery.platform.events.DLT"
    $key = "failure-drill-" + (Get-Date -Format "yyyyMMddHHmmss")
    # Written by bash inside the container: text piped from Windows PowerShell to docker gets a byte-order mark in front
    docker exec grocery-kafka bash -c ("echo '$($key):this is not an event' | kafka-console-producer --bootstrap-server " +
        "localhost:9092 --topic grocery.platform.events --property parse.key=true --property key.separator=:") | Out-Null
    Write-Host "  sent an unreadable message with key $key to grocery.platform.events"

    $script:first = $null
    $took = Wait-Seconds {
        $script:first = Invoke-Api GET "$($deadLetters)?limit=50" $superAdmin | Where-Object { $_.key -eq $key } | Select-Object -First 1
        $script:first
    } 60
    Check "cart-service moved it to grocery.platform.events.DLT" ($null -ne $took) "$(Seconds $took), $($script:first.exceptionClass)"
    if ($null -eq $script:first) { return }

    $replay = Invoke-Api POST "$deadLetters/replay" $superAdmin @{ partition = $script:first.partition; offset = $script:first.offset }
    Check "the super admin replays it" ($replay.replayed -eq 1) "partition $($script:first.partition), offset $($script:first.offset)"

    $took = Wait-Seconds {
        Invoke-Api GET "$($deadLetters)?limit=50" $superAdmin | Where-Object { $_.key -eq $key -and $_.offset -ne $script:first.offset }
    } 60
    Check "it went back to its topic and cart-service read it again (still unreadable: in the DLT again)" ($null -ne $took) (Seconds $took)

    $marked = Invoke-Api GET "$($deadLetters)?limit=50" $superAdmin | Where-Object { $_.offset -eq $script:first.offset -and $_.key -eq $key }
    Check "the replayed message is marked as replayed" ($null -ne $marked.replayedAt) "at $($marked.replayedAt) by user $($marked.replayedByUserId)"

    $refused = $false
    try { Invoke-Api POST "$deadLetters/replay" $superAdmin @{ partition = $script:first.partition; offset = $script:first.offset } | Out-Null }
    catch { $refused = $_.Exception.Message -match "409|already replayed" }   # Windows PowerShell may show only "(409) Conflict"
    Check "a second replay of the same message is refused" $refused
}

function Invoke-DatabasePoolDrill {
    Write-Host "`nDrill 4: every database connection of a service is in use" -ForegroundColor Cyan
    $service = "http://localhost:8102"   # customer-service: at most 5 connections (hikari.maximum-pool-size)
    $customer = @{ "X-User-Id" = "4"; "X-User-Role" = "CUSTOMER" }
    $customerProfile = (Invoke-RestMethod -Uri "$service/api/v1/customers/me" -Headers $customer).data
    $timeoutsBefore = Get-ConnectionTimeouts $service

    # A MySQL session holds a lock on customer1's row for 20 s (a slow transaction somewhere else)
    $lock = Start-Job -ArgumentList $mysqlRootPassword {
        param($password)
        docker exec -e MYSQL_PWD=$password grocery-mysql mysql -uroot -e "BEGIN; SELECT id FROM customer_db.customers WHERE user_id = 4 FOR UPDATE; DO SLEEP(20); ROLLBACK;"
    }
    Start-Sleep -Seconds 3

    # 12 profile updates at the same time (each changes the name, so each one writes and waits for the lock)
    Add-Type -AssemblyName System.Net.Http
    $http = New-Object System.Net.Http.HttpClient
    $http.Timeout = [TimeSpan]::FromSeconds(60)
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $calls = foreach ($i in 1..12) {
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Put, "$service/api/v1/customers/me")
        foreach ($header in $customer.GetEnumerator()) { $request.Headers.Add($header.Key, $header.Value) }
        $body = @{ fullName = "Priya Sharma (drill $i)"; phone = $customerProfile.phone } | ConvertTo-Json
        $request.Content = New-Object System.Net.Http.StringContent($body, [System.Text.Encoding]::UTF8, "application/json")
        [pscustomobject]@{ Task = $http.SendAsync($request); Seconds = $null }
    }
    while (@($calls | Where-Object { $null -eq $_.Seconds }).Count -gt 0 -and $watch.Elapsed.TotalSeconds -lt 60) {
        foreach ($call in $calls | Where-Object { $null -eq $_.Seconds -and $_.Task.IsCompleted }) { $call.Seconds = $watch.Elapsed.TotalSeconds }
        Start-Sleep -Milliseconds 100
    }
    $busy = @($calls | Where-Object { [int]$_.Task.Result.StatusCode -eq 503 })
    $done = @($calls | Where-Object { [int]$_.Task.Result.StatusCode -eq 200 })
    $conflicts = @($calls | Where-Object { [int]$_.Task.Result.StatusCode -eq 409 })
    $busyMessage = if ($busy.Count -gt 0) { ($busy[0].Task.Result.Content.ReadAsStringAsync().Result | ConvertFrom-Json).message } else { "" }
    $busySeconds = ($busy | Measure-Object Seconds -Maximum).Maximum
    Check "the 7 requests that get no connection are answered 503 'busy' within about 5 s" `
        ($busy.Count -eq 7 -and $busySeconds -lt 8 -and $busyMessage -match "busy") `
        ("{0} x 503 after {1:0.0} s at most: {2}" -f $busy.Count, $busySeconds, $busyMessage)
    # The 5 changed the same profile: the first one saves it, the other 4 notice the newer version (optimistic
    # locking) and answer 409 instead of silently overwriting it
    Check "the 5 requests holding a connection finish when the lock is released (1 saved, 4 refused: 409)" `
        ($done.Count -eq 1 -and $conflicts.Count -eq 4) `
        ("{0} x 200 and {1} x 409 after {2:0.0} s" -f $done.Count, $conflicts.Count, ($done | Measure-Object Seconds -Maximum).Maximum)
    Receive-Job -Wait -AutoRemoveJob $lock | Out-Null

    $started = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-RestMethod -Uri "$service/api/v1/customers/me" -Headers $customer | Out-Null
    Check "the pool works again by itself" ($started.Elapsed.TotalSeconds -lt 2) ("{0:0.00} s for the next request" -f $started.Elapsed.TotalSeconds)
    $timeoutsAfter = Get-ConnectionTimeouts $service
    Check "the timeouts were counted (hikaricp_connections_timeout_total, alert DatabaseConnectionTimeouts)" `
        (($timeoutsAfter - $timeoutsBefore) -eq 7) ("+{0}" -f ($timeoutsAfter - $timeoutsBefore))

    $restore = @{ fullName = $customerProfile.fullName; phone = $customerProfile.phone; dateOfBirth = $customerProfile.dateOfBirth; gender = $customerProfile.gender } | ConvertTo-Json
    Invoke-RestMethod -Method Put -Uri "$service/api/v1/customers/me" -Headers $customer -ContentType "application/json" -Body $restore | Out-Null
}

# hikaricp_connections_timeout_total of a service (requests that got no connection within the time limit)
function Get-ConnectionTimeouts([string] $service) {
    $line = (Invoke-WebRequest -UseBasicParsing -Uri "$service/actuator/prometheus").Content -split "`n" |
            Where-Object { $_ -like "hikaricp_connections_timeout_total*" } | Select-Object -First 1
    return [int][double]($line -split " ")[-1]
}

function Invoke-RedisDrill {
    Write-Host "`nDrill 5: Redis is down" -ForegroundColor Cyan
    $partner = Get-Token "delivery1@grocery.com"
    docker stop grocery-redis | Out-Null
    try {
        $started = [System.Diagnostics.Stopwatch]::StartNew()
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$Gateway/api/v1/stores"
        Check "the gateway still answers at once (rate limiting lets requests through)" `
            ($response.StatusCode -eq 200 -and $started.Elapsed.TotalSeconds -lt 2) ("{0:0.00} s" -f $started.Elapsed.TotalSeconds)

        $started = [System.Diagnostics.Stopwatch]::StartNew()
        $status = 0; $message = ""
        try { Invoke-Api POST "/api/v1/delivery/availability/online" $partner | Out-Null; $status = 200 }
        catch { $message = $_.Exception.Message; $status = if ($message -match "not available|503") { 503 } else { -1 } }
        Check "going online answers 503 at once (it needs Redis)" ($status -eq 503 -and $started.Elapsed.TotalSeconds -lt 3) `
            ("{0:0.00} s: {1}" -f $started.Elapsed.TotalSeconds, $message)
    } finally {
        docker start grocery-redis | Out-Null
    }
    $back = Wait-Seconds { Test-Healthy "grocery-redis" } 60
    $counted = Wait-Seconds {
        $remaining = (Invoke-WebRequest -UseBasicParsing -Uri "$Gateway/api/v1/stores").Headers["X-RateLimit-Remaining"]
        [int]$remaining -ge 0
    } 30
    Check "after Redis is back the rate limit counts again" ($null -ne $counted) "Redis healthy $(Seconds $back), counting $(Seconds $counted)"
    $online = Wait-Seconds { Invoke-Api POST "/api/v1/delivery/availability/online" $partner } 30
    Check "the partner can go online again" ($null -ne $online) (Seconds $online)
    Invoke-Api POST "/api/v1/delivery/availability/offline" $partner | Out-Null
}

# ------------------------------------------------------------------------------------------------------------------
try { Get-Token "customer1@grocery.com" | Out-Null }
catch { Write-Host "The platform does not answer at $Gateway - start the infrastructure and the services first." -ForegroundColor Red; exit 1 }

if ($Drill -in "all", "connect") { Invoke-ConnectDrill }
if ($Drill -in "all", "kafka") { Invoke-KafkaDrill }
if ($Drill -in "all", "deadletter") { Invoke-DeadLetterDrill }
if ($Drill -in "all", "dbpool") { Invoke-DatabasePoolDrill }
if ($Drill -in "all", "redis") { Invoke-RedisDrill }

Write-Host ""
$color = if ($script:failed -eq 0) { "Green" } else { "Red" }
Write-Host "$($script:passed) passed, $($script:failed) failed" -ForegroundColor $color
if ($script:failed -gt 0) { exit 1 }
