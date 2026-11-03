<#
.SYNOPSIS
    Asks the shopping assistant the golden questions and checks every answer (hallucination prevention, layer 5).

.DESCRIPTION
    Reads mcp-client-service\evaluation\golden-questions.json, sends each conversation to the chat API (through the
    API gateway by default) and checks the last answer: which tools were used, which texts must or must not appear,
    and the "grounded" flag. Prints one line per case and a summary, and fails (exit code 1) when fewer cases pass
    than -MinPassRate.

    Needs the whole assistant running: Ollama (start-infra.ps1 -Profiles ai), mcp-server-service,
    mcp-client-service, the API gateway and the catalog services with the local seed data; for the cases with
    "login": true also auth-service, cart-service and order-service (they chat as customer1). The panel cases log in
    as the seed account named in "login" (vendor1, delivery1, admin) and also need store-, delivery- and
    report-service. The cart cases change a cart: they chat as customer2, and "emptyCartFirst": true empties that
    cart before the conversation (DELETE /api/v1/cart through the gateway), so every run starts the same.
    "[Confirm]" in a conversation presses Confirm on the card of the answer before it, as the website does: this
    script calls order-service or cart-service itself (place the order, cancel it, add the products, take them out of
    the cart, set a new quantity, or empty the cart) with the account's login, and sends a confirmed order card ({kind,
    orderId}) with the next message, like the website. "{order}" in a later
    message becomes the number of the order placed that way (the website shows it: "Order #200 placed ..."). Orders a
    case placed are cancelled again at its end, if they are still open.
    Run it after every change to the system prompt, the tools or the model: a language model can get worse in
    ways no unit test sees.

.EXAMPLE
    .\scripts\evaluate-assistant.ps1
    .\scripts\evaluate-assistant.ps1 -Only price,coupons
    .\scripts\evaluate-assistant.ps1 -BaseUrl http://localhost:8114     # mcp-client-service directly
#>
param(
    [string] $BaseUrl = "http://localhost:8080",
    [string] $QuestionsFile = (Join-Path $PSScriptRoot "..\mcp-client-service\evaluation\golden-questions.json"),
    [string[]] $Only = @(),
    [double] $MinPassRate = 0.9,
    # Cases with "login": true chat as this customer (seed data); "login": "<email>" as that seed account (same
    # password). The login always goes through the gateway.
    [string] $GatewayUrl = "http://localhost:8080",
    [string] $CustomerEmail = "customer1@grocery.com",
    [string] $CustomerPassword = "Password@123"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$script:logins = @{}

# Logs in once per account and again after 10 minutes (an access token lives 15 minutes, a run can take longer);
# returns the headers that account's chat request carries
function Get-LoginHeaders([string] $Email) {
    $cached = $script:logins[$Email]
    if ($null -eq $cached -or ((Get-Date) - $cached.obtainedAt).TotalMinutes -gt 10) {
        $body = @{ email = $Email; password = $CustomerPassword } | ConvertTo-Json -Compress
        $data = (Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json").data
        $script:logins[$Email] = @{ data = $data; obtainedAt = Get-Date }
    }
    $login = $script:logins[$Email].data
    $headers = @{ Authorization = "Bearer $($login.accessToken)" }
    if ($BaseUrl -ne $GatewayUrl) {
        # Directly at mcp-client-service: send the user headers the gateway would add
        $headers["X-User-Id"] = [string] $login.user.id
        $headers["X-User-Role"] = $login.user.role
    }
    return $headers
}

# $Confirmed: the cards confirmed since the last message, sent like the website does ({kind, orderId})
function Send-ChatMessage([string] $Message, [string] $ConversationId, [hashtable] $Headers, [object[]] $Confirmed = @()) {
    $body = @{ message = $Message }
    if ($ConversationId) { $body.conversationId = $ConversationId }
    if ($Confirmed.Count -gt 0) { $body.confirmed = $Confirmed }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress -Depth 5))

    for ($attempt = 1; ; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/chat/messages" -Method Post -Body $bytes -Headers $Headers `
                -ContentType "application/json; charset=utf-8" -UseBasicParsing -TimeoutSec 300
            # Windows PowerShell 5.1 guesses the wrong text encoding for JSON: decode the bytes as UTF-8 ourselves (₹)
            $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
            return ($text | ConvertFrom-Json).data
        }
        catch {
            $status = if ($_.Exception.Response) { [int] $_.Exception.Response.StatusCode } else { 0 }
            if ($status -eq 429 -and $attempt -lt 4) {
                Start-Sleep -Seconds 10        # the gateway's chat limit: wait for new tokens, then ask again
                continue
            }
            throw
        }
    }
}

# Empties the cart of the logged-in account (always through the gateway, with its token only)
function Clear-Cart([hashtable] $Headers) {
    Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart" -Method Delete -Headers @{ Authorization = $Headers.Authorization } | Out-Null
}

# "[Confirm]": what the Confirm button under an answer does (ChatActionCard.tsx). The chat never does it; the
# website calls order-service or cart-service itself, with the customer's login. Returns the confirmed order card
# ({kind, orderId}), which the website then sends with the next question; $null for a change of the cart (the chat
# reads the cart again for every question about it).
function Confirm-Card($Action, [hashtable] $Headers) {
    $auth = @{ Authorization = $Headers.Authorization }
    if ($Action.kind -eq "PLACE_ORDER") {
        $auth["Idempotency-Key"] = [guid]::NewGuid().ToString()
        $body = @{ addressId = $Action.addressId; paymentMethod = $Action.paymentMethod
            expectedCheckoutFingerprint = $Action.checkoutFingerprint } | ConvertTo-Json -Compress
        $order = (Invoke-RestMethod -Uri "$GatewayUrl/api/v1/orders" -Method Post -Body $body -ContentType "application/json" -Headers $auth).data
        return @{ kind = "PLACE_ORDER"; orderId = $order.id }
    }
    if ($Action.kind -eq "CANCEL_ORDER") {
        $body = @{ reason = $Action.reason } | ConvertTo-Json -Compress
        Invoke-RestMethod -Uri "$GatewayUrl/api/v1/orders/$($Action.orderId)/cancel" -Method Post -Body $body `
            -ContentType "application/json" -Headers $auth | Out-Null
        return @{ kind = "CANCEL_ORDER"; orderId = $Action.orderId }
    }
    if ($Action.kind -eq "ADD_TO_CART") {
        # as the website: each product's packs in the cart become the line's newQuantity (PUT when it is in the cart,
        # POST when not), so the card never adds twice
        $cart = (Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart" -Headers $auth).data
        foreach ($line in @($Action.items)) {
            if (@($cart.items | Where-Object { $_.productId -eq $line.productId }).Count -gt 0) {
                $body = @{ quantity = $line.newQuantity } | ConvertTo-Json -Compress
                Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart/items/$($line.productId)" -Method Put -Body $body `
                    -ContentType "application/json" -Headers $auth | Out-Null
            }
            else {
                $body = @{ productId = $line.productId; quantity = $line.newQuantity } | ConvertTo-Json -Compress
                Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart/items" -Method Post -Body $body `
                    -ContentType "application/json" -Headers $auth | Out-Null
            }
        }
        return $null
    }
    if ($Action.kind -eq "REMOVE_FROM_CART") {
        foreach ($line in @($Action.items)) {
            Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart/items/$($line.productId)" -Method Delete -Headers $auth | Out-Null
        }
        return $null
    }
    if ($Action.kind -eq "SET_CART_QUANTITY") {
        $body = @{ quantity = $Action.newQuantity } | ConvertTo-Json -Compress
        Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart/items/$($Action.items[0].productId)" -Method Put -Body $body `
            -ContentType "application/json" -Headers $auth | Out-Null
        return $null
    }
    if ($Action.kind -eq "CLEAR_CART") {
        Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart" -Method Delete -Headers $auth | Out-Null
        return $null
    }
    throw "[Confirm]: the answer before it has no card"
}

# Cancels an order a case placed, unless it is cancelled already (then order-service says 400, which is fine here)
function Undo-Order([long] $OrderId, [hashtable] $Headers) {
    try {
        $body = @{ reason = "Golden question run" } | ConvertTo-Json -Compress
        Invoke-RestMethod -Uri "$GatewayUrl/api/v1/orders/$OrderId/cancel" -Method Post -Body $body `
            -ContentType "application/json" -Headers @{ Authorization = $Headers.Authorization } | Out-Null
    }
    catch { }
}

function Find-Text([string] $Answer, [string] $Text) {
    return $Answer.IndexOf($Text, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

# Returns the problems of one answer (an empty list when the answer passes)
function Test-Answer($Case, $Reply) {
    $problems = @()
    $answer = [string] $Reply.answer
    $tools = @($Reply.toolsUsed)

    if ($Case.tools -and -not ($tools | Where-Object { $Case.tools -contains $_ })) {
        $problems += "expected one of the tools $($Case.tools -join ', ')"
    }
    if ($Case.noTools -and $tools.Count -gt 0) {
        $problems += "expected no tool, but used $($tools -join ', ')"
    }
    foreach ($text in @($Case.contains)) {
        if ($text -and -not (Find-Text $answer $text)) { $problems += "missing '$text'" }
    }
    if ($Case.containsAny -and -not (@($Case.containsAny) | Where-Object { Find-Text $answer $_ })) {
        $problems += "missing one of '$($Case.containsAny -join "', '")'"
    }
    foreach ($text in @($Case.notContains)) {
        if ($text -and (Find-Text $answer $text)) { $problems += "must not contain '$text'" }
    }
    if ($null -ne $Case.grounded -and [bool] $Reply.grounded -ne [bool] $Case.grounded) {
        $problems += "grounded should be $($Case.grounded)"
    }
    # The card with a Confirm button under the answer (e.g. PLACE_ORDER), made from a tool result
    if ($Case.action -and [string] $Reply.action.kind -ne $Case.action) {
        $problems += "expected the card $($Case.action)"
    }
    if ($Case.noAction -and $Reply.action) {
        $problems += "expected no card, but got $($Reply.action.kind)"
    }
    return $problems
}

$cases = (Get-Content -Raw -Encoding UTF8 $QuestionsFile | ConvertFrom-Json).cases
if ($Only.Count -gt 0) { $cases = @($cases | Where-Object { $Only -contains $_.id }) }
Write-Host "Asking $($cases.Count) golden question(s) at $BaseUrl ..." -ForegroundColor Cyan

$results = foreach ($case in $cases) {
    $started = Get-Date
    $reply = $null
    $placedOrders = @()
    $unsentConfirmations = @()
    $headers = @{}
    try {
        $conversationId = $null
        if ($case.login -is [string]) { $headers = Get-LoginHeaders $case.login }
        elseif ($case.login) { $headers = Get-LoginHeaders $CustomerEmail }
        if ($case.emptyCartFirst) { Clear-Cart $headers }
        foreach ($message in $case.conversation) {
            if ($message -eq "[Confirm]") {
                $confirmed = Confirm-Card $reply.action $headers
                if ($confirmed) { $unsentConfirmations += $confirmed }
                if ($confirmed -and $confirmed.kind -eq "PLACE_ORDER") { $placedOrders += $confirmed.orderId }
                continue
            }
            if ($placedOrders.Count -gt 0) { $message = $message.Replace("{order}", [string] $placedOrders[-1]) }
            $reply = Send-ChatMessage $message $conversationId $headers $unsentConfirmations
            $unsentConfirmations = @()
            $conversationId = $reply.conversationId
        }
        $problems = @(Test-Answer $case $reply)
    }
    catch {
        $problems = @("request failed: $($_.Exception.Message)")
    }
    finally {
        foreach ($orderId in $placedOrders) { Undo-Order $orderId $headers }
    }
    $seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
    $passed = $problems.Count -eq 0
    $answerText = if ($reply) { ([string] $reply.answer) -replace '\s+', ' ' } else { "" }
    $toolsText = if ($reply) { (@($reply.toolsUsed) -join ', ') } else { "" }

    $label = if ($passed) { "PASS" } else { "FAIL" }
    $color = if ($passed) { "Green" } else { "Red" }
    Write-Host ("{0} {1,-32} {2,6} s  [{3}]" -f $label, $case.id, $seconds, $toolsText) -ForegroundColor $color
    Write-Host "     $($answerText.Substring(0, [math]::Min(160, $answerText.Length)))"
    foreach ($problem in $problems) { Write-Host "     -> $problem" -ForegroundColor Yellow }

    [pscustomobject]@{ Id = $case.id; Passed = $passed; Seconds = $seconds }
}

$results = @($results)
$passedCount = @($results | Where-Object { $_.Passed }).Count
$passRate = if ($results.Count -gt 0) { $passedCount / $results.Count } else { 0 }
$times = $results | Measure-Object -Property Seconds -Average -Maximum
Write-Host ""
Write-Host ("{0} of {1} passed ({2:P0}); seconds per case: average {3:N1}, slowest {4:N1}" -f `
        $passedCount, $results.Count, $passRate, $times.Average, $times.Maximum) -ForegroundColor Cyan

if ($passRate -lt $MinPassRate) {
    Write-Host ("Below the required pass rate of {0:P0}." -f $MinPassRate) -ForegroundColor Red
    exit 1
}
