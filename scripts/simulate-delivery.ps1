<#
.SYNOPSIS
    Simulates a delivery partner on the road, to test the live tracking map (docs/delivery-tracking.md).

.DESCRIPTION
    1. Creates an order to follow (unless -OrderId is given): customer1 orders 2 x bananas from Fresh Mart (cash on
       delivery) to their home address; vendor1 accepts, packs and hands it over; delivery1 (online) gets the delivery.
    2. delivery1 accepts it and "drives": from a start point to the store, picks the order up, then to the customer,
       sending a position every few seconds - exactly what the partner's phone sends
       (PUT /api/v1/delivery/deliveries/{id}/location).
    3. Delivers it and takes delivery1 offline again (unless -NoDeliver: then delivery1 stays online and busy).

    Watch it meanwhile as the customer: log in to the website as customer1@grocery.com (Password@123) and open
    My orders -> the order -> "Track on the map", or follow the raw stream:
      curl.exe -N -H "Authorization: Bearer <customer token>" http://localhost:8080/api/v1/delivery-tracking/orders/<id>/stream

    The positions are on a straight line (no road network): fine for testing the map, the live updates and the
    statuses. Needs the services, the gateway, Kafka + the connectors, and the seed data (stores and addresses with
    coordinates).

.EXAMPLE
    .\scripts\simulate-delivery.ps1                              # new order, drive, deliver
    .\scripts\simulate-delivery.ps1 -NoDeliver                   # stop at the customer's door ("out for delivery")
    .\scripts\simulate-delivery.ps1 -OrderId 42                  # drive an order that is already assigned to delivery1
    .\scripts\simulate-delivery.ps1 -Steps 30 -SecondsBetweenUpdates 2
#>
param(
    [long] $OrderId = 0,
    [int] $Steps = 12,                        # positions per leg (start -> store, store -> customer)
    [double] $SecondsBetweenUpdates = 3,      # at least 2: faster updates are skipped by delivery-service
    [switch] $NoDeliver,
    [string] $Gateway = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$invariant = [System.Globalization.CultureInfo]::InvariantCulture

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

function Wait-Until([scriptblock] $Condition, [string] $What, [int] $Seconds = 60) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        $result = & $Condition
        if ($result) { return $result }
        Start-Sleep -Seconds 1
    }
    throw "Timed out after $Seconds s waiting for: $What"
}

function Send-Position([long] $DeliveryId, [double] $Latitude, [double] $Longitude, [string] $Label) {
    $body = @{ latitude = [math]::Round($Latitude, 6); longitude = [math]::Round($Longitude, 6); accuracyMeters = 8
               recordedAt = (Get-Date).ToUniversalTime().ToString("o") }
    $answer = Invoke-Api PUT "/api/v1/delivery/deliveries/$DeliveryId/location" $script:partner $body
    $eta = if ($answer.route) { "  route " + [math]::Round($answer.route.distanceMeters) + " m, arrives " + ([datetime]$answer.route.estimatedArrivalAt).ToLocalTime().ToString("HH:mm:ss") } else { "" }
    Write-Host ([string]::Format($invariant, "  {0,-22} {1:0.000000}, {2:0.000000}  {3}{4}", $Label, $Latitude, $Longitude,
            $(if ($answer.stored) { "stored" } else { "skipped" }), $eta))
}

# Drives in a straight line from one point to another, one position every $SecondsBetweenUpdates
function Move-Along([long] $DeliveryId, $From, $To, [string] $Leg) {
    for ($step = 1; $step -le $Steps; $step++) {
        $fraction = $step / $Steps
        $latitude = $From.latitude + ($To.latitude - $From.latitude) * $fraction
        $longitude = $From.longitude + ($To.longitude - $From.longitude) * $fraction
        Send-Position $DeliveryId $latitude $longitude "$Leg $step/$Steps"
        Start-Sleep -Milliseconds ([int]($SecondsBetweenUpdates * 1000))
    }
}

Write-Host "Logging in (customer1, vendor1, delivery1)..." -ForegroundColor Cyan
$customer = Get-Token "customer1@grocery.com"
$vendor = Get-Token "vendor1@grocery.com"
$script:partner = Get-Token "delivery1@grocery.com"
Invoke-Api POST "/api/v1/delivery/availability/online" $script:partner | Out-Null

if ($OrderId -eq 0) {
    $busy = (Invoke-Api GET "/api/v1/delivery/deliveries?active=true" $script:partner).content
    if ($busy) {
        throw "delivery1 is busy with order $($busy[0].orderId) ($($busy[0].status)). Run with -OrderId $($busy[0].orderId), or finish it first."
    }
    Write-Host "Creating an order to follow: customer1, 2 x bananas from Fresh Mart, cash on delivery..." -ForegroundColor Cyan
    Invoke-Api DELETE "/api/v1/cart" $customer | Out-Null
    Invoke-Api POST "/api/v1/cart/items" $customer @{ productId = 4; quantity = 2 } | Out-Null
    $OrderId = (Invoke-Api POST "/api/v1/orders" $customer @{ addressId = 1; paymentMethod = "COD" }).id
    Write-Host "  order $OrderId placed; waiting until it is CONFIRMED (stock reserved)..."
    Wait-Until { (Invoke-Api GET "/api/v1/orders/$OrderId" $customer).status -eq "CONFIRMED" } "order $OrderId CONFIRMED" | Out-Null
    foreach ($step in "accept", "pack", "ready-for-pickup") {
        Invoke-Api POST "/api/v1/vendor/orders/$OrderId/$step" $vendor | Out-Null
        Write-Host "  vendor1: $step"
    }
}

Write-Host "Waiting until delivery1 has the delivery of order $OrderId..." -ForegroundColor Cyan
$delivery = Wait-Until {
    (Invoke-Api GET "/api/v1/delivery/deliveries?active=true" $script:partner).content | Where-Object { $_.orderId -eq $OrderId }
} "the delivery of order $OrderId assigned to delivery1"
$deliveryId = $delivery.id
if (-not $delivery.storeLocation -or -not $delivery.customerLocation) {
    throw "Delivery $deliveryId has no coordinates (an order from before coordinates existed, or a store / address without them). Run the script without -OrderId to create a new order."
}
Write-Host "  delivery $deliveryId, status $($delivery.status)"
Write-Host "  customer's tracking page: http://localhost:5173/orders/$OrderId/track (customer1@grocery.com)" -ForegroundColor Green

if ($delivery.status -eq "ASSIGNED") {
    Invoke-Api POST "/api/v1/delivery/deliveries/$deliveryId/accept" $script:partner | Out-Null
    Write-Host "  delivery1 accepted it: location sharing starts"
}

$store = $delivery.storeLocation
$customerPlace = $delivery.customerLocation
# The partner starts about 1 km north-west of the store
$start = @{ latitude = $store.latitude + 0.0065; longitude = $store.longitude - 0.0055 }

if ((Invoke-Api GET "/api/v1/delivery/deliveries/$deliveryId" $script:partner).status -eq "ACCEPTED") {
    Write-Host "On the way to the store:" -ForegroundColor Cyan
    Move-Along $deliveryId $start $store "to the store"
    Invoke-Api POST "/api/v1/delivery/deliveries/$deliveryId/pick-up" $script:partner | Out-Null
    Write-Host "  picked up"
}
if ((Invoke-Api GET "/api/v1/delivery/deliveries/$deliveryId" $script:partner).status -eq "PICKED_UP") {
    Invoke-Api POST "/api/v1/delivery/deliveries/$deliveryId/start" $script:partner | Out-Null
    Write-Host "  out for delivery"
}
Write-Host "On the way to the customer:" -ForegroundColor Cyan
Move-Along $deliveryId $store $customerPlace "to the customer"

if ($NoDeliver) {
    Write-Host "Stopped at the customer's door (-NoDeliver): the delivery stays OUT_FOR_DELIVERY." -ForegroundColor Green
} else {
    Invoke-Api POST "/api/v1/delivery/deliveries/$deliveryId/deliver" $script:partner | Out-Null
    # Offline again, so the next order waits for a partner as usual instead of going to delivery1 at once
    Invoke-Api POST "/api/v1/delivery/availability/offline" $script:partner | Out-Null
    Write-Host "Delivered: order $OrderId. The tracking page shows the final state; the partner's position is deleted." -ForegroundColor Green
    Write-Host "delivery1 is offline again."
}
