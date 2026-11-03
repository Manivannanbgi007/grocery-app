<#
.SYNOPSIS
    Creates sample DELIVERED orders in the local development environment, to test every panel by hand.

.DESCRIPTION
    Development data only: the script refuses a gateway that is not on this computer.

    Every order goes through the real flow, through the API gateway, the way a customer, a vendor and a delivery
    partner do it on the website. So each service stores it in its own way (order, payment, stock, delivery,
    notifications, reports) and nothing is written to a database by hand:
      1. the customer fills the cart with products of one store from different categories and orders it, cash on
         delivery or online (paid with the built-in MOCK gateway: no real payment provider is called);
      2. the store's vendor accepts the order, packs it and hands it over;
      3. only the chosen delivery partner of the store's zone is online: delivery-service gives them the delivery,
         and they pick it up and deliver it.
    Prices, coupon discounts, the delivery fee and the totals are calculated by the services, never by this script.

    Accounts: customer1 and customer2, 4 sample customers and 2 sample delivery partners. The script registers the
    sample accounts the first time (emails @example.com, password Password@123); the admin approves the partners' KYC.

    Running it again is safe: every sample order has the note "Sample order NN (test data)" and the Idempotency-Key
    "sample-order-NN". An order that exists is never placed again; one that was interrupted is finished.

    Side effects, as with real orders: the customers, vendors and partners get in-app notifications (emails go to
    the local Mailpit only), and the stock goes down (a product is never taken below 10 units). customer1's and
    customer2's carts and every partner's online status are put back at the end.

    At the end the script checks every sample order: DELIVERED and paid, the totals add up, and it is in the admin's
    orders and deliveries, the customer's history, the vendor's orders and the partner's completed deliveries.

.EXAMPLE
    .\scripts\create-sample-orders.ps1                # the 24 sample orders, then the checks
    .\scripts\create-sample-orders.ps1 -Count 3       # only sample orders 1-3 (a quick try)
    .\scripts\create-sample-orders.ps1 -VerifyOnly    # only the checks, nothing is created
#>
param(
    [ValidateRange(1, 60)] [int] $Count = 24,
    [switch] $VerifyOnly,
    [string] $Gateway = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

if (([uri] $Gateway).Host -notin @("localhost", "127.0.0.1")) {
    throw "Sample data is for local development only: the gateway must be on this computer, not $Gateway."
}

$Password = "Password@123"
$Admin = "admin@grocery.com"
$MinimumStockLeft = 10      # a product is never taken below this, so the shop still shows it in stock
$Seed = 20261030            # the same order number gets the same partner, payment and coupon in every run

# The two seed stores (docs/seed-data.md). A delivery belongs to the zone of the store's PIN code.
$Stores = @{
    1 = @{ Name = "Fresh Mart"; Vendor = "vendor1@grocery.com"; Zone = 1; Pincodes = @("560038", "560008", "560071")
           Coupon = "FRESH20"; CouponMinimum = 249 }
    2 = @{ Name = "Green Basket"; Vendor = "vendor2@grocery.com"; Zone = 2; Pincodes = @("560034", "560095", "560047")
           Coupon = "GREEN30"; CouponMinimum = 199 }
}
# The categories of the sample orders: Vegetables, Fruits, Dairy & Eggs, Snacks & Beverages, Household
$CategoryIds = 1..5
$HouseholdCategoryId = 5

# The customers. The seed customers keep their addresses; the sample customers get theirs the first time.
$Customers = @(
    @{ Email = "customer1@grocery.com"; Existing = $true }
    @{ Email = "customer2@grocery.com"; Existing = $true }
    @{ Email = "sample.kavya@example.com"; FullName = "Kavya Nair"; Phone = "9876501001"; Addresses = @(
        @{ label = "HOME"; addressLine1 = "12, 4th Cross, Domlur Layout"; addressLine2 = "Domlur"
           landmark = "Near Domlur Bus Stop"; pincode = "560071"; latitude = 12.9609; longitude = 77.6387 }
        @{ label = "WORK"; addressLine1 = "3rd Floor, Maruthi Plaza, 80 Feet Road"; addressLine2 = "Koramangala 6th Block"
           landmark = $null; pincode = "560095"; latitude = 12.9375; longitude = 77.6245 }) }
    @{ Email = "sample.vikram@example.com"; FullName = "Vikram Joshi"; Phone = "9876501002"; Addresses = @(
        @{ label = "HOME"; addressLine1 = "Flat 5B, Sobha Apartments, 17th Main"; addressLine2 = "Koramangala 6th Block"
           landmark = "Near Jyoti Nivas College"; pincode = "560095"; latitude = 12.9342; longitude = 77.6198 }
        @{ label = "WORK"; addressLine1 = "2nd Floor, 12th Main Road"; addressLine2 = "HAL 2nd Stage, Indiranagar"
           landmark = $null; pincode = "560038"; latitude = 12.9698; longitude = 77.6380 }) }
    @{ Email = "sample.sneha@example.com"; FullName = "Sneha Rao"; Phone = "9876501003"; Addresses = @(
        @{ label = "HOME"; addressLine1 = "Flat 104, Brigade Residency, Ejipura Main Road"; addressLine2 = "Viveknagar"
           landmark = "Near Ejipura Signal"; pincode = "560047"; latitude = 12.9446; longitude = 77.6263 }) }
    @{ Email = "sample.irfan@example.com"; FullName = "Mohammed Irfan"; Phone = "9876501004"; Addresses = @(
        @{ label = "HOME"; addressLine1 = "7, Murphy Road"; addressLine2 = "Halasuru"
           landmark = "Near Ulsoor Lake"; pincode = "560008"; latitude = 12.9800; longitude = 77.6250 }) }
)

# The delivery partners, by zone. The sample partners register, send their KYC and are approved the first time.
$Partners = @(
    @{ Email = "delivery1@grocery.com"; Zone = 1 }
    @{ Email = "delivery2@grocery.com"; Zone = 2 }
    @{ Email = "sample.deepak@example.com"; Zone = 1; FullName = "Deepak Gowda"; Phone = "9876502001"
       Kyc = @{ vehicleType = "MOTORBIKE"; vehicleNumber = "KA01HX4521"; drivingLicenseNumber = "KA0120180004521"; zoneId = 1 } }
    @{ Email = "sample.lakshmi@example.com"; Zone = 2; FullName = "Lakshmi Prasad"; Phone = "9876502002"
       Kyc = @{ vehicleType = "SCOOTER"; vehicleNumber = "KA05MJ7788"; drivingLicenseNumber = "KA0520200007788"; zoneId = 2 } }
)

# ----- Calling the API -------------------------------------------------------------------------------------------

$script:tokens = @{}

# Calls the gateway as the given user (or as a guest) and returns the "data" of the answer.
# A failed call throws "<METHOD> <path> failed (<HTTP status>): <the service's message>".
function Invoke-Api([string] $Method, [string] $Path, [string] $Email = $null, $Body = $null, [hashtable] $Headers = @{}) {
    $allHeaders = @{}
    foreach ($key in $Headers.Keys) { $allHeaders[$key] = $Headers[$key] }
    if ($Email) { $allHeaders["Authorization"] = "Bearer " + (Get-Token $Email) }
    $arguments = @{ Method = $Method; Uri = "$Gateway$Path"; Headers = $allHeaders; ContentType = "application/json" }
    if ($null -ne $Body) { $arguments["Body"] = ($Body | ConvertTo-Json -Depth 5) }
    try {
        return (Invoke-RestMethod @arguments).data
    } catch {
        $failure = $_
        $status = if ($failure.Exception.Response) { [int] $failure.Exception.Response.StatusCode } else { 0 }
        $message = $failure.Exception.Message
        if ($failure.ErrorDetails.Message) {
            try { $message = ($failure.ErrorDetails.Message | ConvertFrom-Json).message } catch { $message = $failure.ErrorDetails.Message }
        }
        throw "$Method $Path failed ($status): $message"
    }
}

# Logs in once per user. Access tokens last 15 minutes, so a token older than 10 minutes is renewed.
function Get-Token([string] $Email) {
    $saved = $script:tokens[$Email]
    if ($saved -and $saved.At -gt (Get-Date).AddMinutes(-10)) { return $saved.Token }
    $token = (Invoke-Api POST "/api/v1/auth/login" $null @{ email = $Email; password = $Password }).accessToken
    $script:tokens[$Email] = @{ Token = $token; At = Get-Date }
    return $token
}

# Repeats the check every second until it returns something (the services work through Kafka events, so a step
# shows up a moment later)
function Wait-Until([scriptblock] $Condition, [string] $What, [int] $Seconds = 60) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        $result = & $Condition
        if ($result) { return $result }
        Start-Sleep -Seconds 1
    }
    throw "Timed out after $Seconds s waiting for: $What"
}

# Two amounts of money are the same (to the paisa)
function Test-Amount($First, $Second) {
    return [math]::Abs([double] $First - [double] $Second) -lt 0.005
}

# ----- Accounts ----------------------------------------------------------------------------------------------------

# Logs in, or registers the account the first time (a login with an unknown email gets 401)
function Initialize-Account($Account, [string] $Role) {
    try {
        Get-Token $Account.Email | Out-Null
        return
    } catch {
        if ("$_" -notmatch '\(401\)' -or -not $Account.FullName) { throw }
    }
    Invoke-Api POST "/api/v1/auth/register" $null @{ fullName = $Account.FullName; email = $Account.Email
        phone = $Account.Phone; password = $Password; role = $Role } | Out-Null
    Write-Host "  registered $($Account.FullName) ($($Account.Email), $Role)"
}

function Initialize-Customer($Customer) {
    Initialize-Account $Customer "CUSTOMER"
    # customer-service creates the profile from the UserRegistered event: until then the addresses answer 404
    $addresses = $null
    for ($i = 0; $i -lt 30 -and $null -eq $addresses; $i++) {
        try {
            $addresses = @(Invoke-Api GET "/api/v1/customers/me/addresses" $Customer.Email)
        } catch {
            if ("$_" -notmatch '\(404\)') { throw }
            Start-Sleep -Seconds 1
        }
    }
    if ($null -eq $addresses) { throw "No customer profile for $($Customer.Email) after 30 s (are customer-service and Kafka running?)" }
    foreach ($address in $Customer.Addresses) {
        if (-not ($addresses | Where-Object { $_.label -eq $address.label -and $_.pincode -eq $address.pincode })) {
            $body = @{ contactName = $Customer.FullName; contactPhone = $Customer.Phone; city = "Bengaluru"; state = "Karnataka" } + $address
            $addresses += Invoke-Api POST "/api/v1/customers/me/addresses" $Customer.Email $body
            Write-Host "  added the $($address.label) address ($($address.pincode)) of $($Customer.FullName)"
        }
    }
    # For each store: the customer's first address that the store delivers to (none: no orders from that store)
    $Customer.AddressIds = @{}
    foreach ($storeId in $Stores.Keys) {
        $match = $addresses | Where-Object { $Stores[$storeId].Pincodes -contains $_.pincode } | Select-Object -First 1
        if ($match) { $Customer.AddressIds[$storeId] = $match.id }
    }
}

function Initialize-Partner($Partner) {
    Initialize-Account $Partner "DELIVERY_PARTNER"
    # delivery-service creates the profile from the UserRegistered event, with the KYC NOT_SUBMITTED
    $partnerProfile = Wait-Until {
        try { Invoke-Api GET "/api/v1/delivery/profile" $Partner.Email } catch { if ("$_" -notmatch '\(404\)') { throw } }
    } "the delivery profile of $($Partner.Email)" 30
    if ($partnerProfile.kycStatus -in @("NOT_SUBMITTED", "REJECTED")) {
        Invoke-Api PUT "/api/v1/delivery/profile/kyc" $Partner.Email $Partner.Kyc | Out-Null
        $partnerProfile.kycStatus = "PENDING"
    }
    if ($partnerProfile.kycStatus -eq "PENDING") {
        Invoke-Api POST "/api/v1/admin/delivery-partners/$($partnerProfile.userId)/approve" $Admin | Out-Null
        Write-Host "  the admin approved the KYC of $($partnerProfile.fullName) (zone $($Partner.Zone))"
    }
    $Partner.UserId = $partnerProfile.userId
    $Partner.Name = $partnerProfile.fullName
    $Partner.WasOnline = [bool] $partnerProfile.online
}

# Only the chosen partner of the zone is online, so delivery-service gives the next delivery of the zone to them
function Set-OnlyOnline($Partner) {
    foreach ($other in $Partners | Where-Object { $_.Zone -eq $Partner.Zone -and $_.Email -ne $Partner.Email }) {
        Invoke-Api POST "/api/v1/delivery/availability/offline" $other.Email | Out-Null
    }
    Invoke-Api POST "/api/v1/delivery/availability/online" $Partner.Email | Out-Null
}

# ----- The sample orders -------------------------------------------------------------------------------------------

# The sample orders that exist already, by number (from the note "Sample order NN (test data)").
# A customer who cannot log in yet (not registered) has none.
function Find-SampleOrders {
    $found = @{}
    foreach ($customer in $Customers) {
        try { $orders = (Invoke-Api GET "/api/v1/orders?size=200" $customer.Email).content }
        catch { if ("$_" -match '\(401\)') { continue } else { throw } }
        foreach ($summary in $orders) {
            $order = Invoke-Api GET "/api/v1/orders/$($summary.id)" $customer.Email
            if ($order.customerNote -match '^Sample order (\d+) ') {
                $found[[int] $Matches[1]] = @{ Order = $order; Customer = $customer }
            }
        }
    }
    return $found
}

# The store's active products of the five categories
function Get-StoreProducts([int] $StoreId) {
    $products = @()
    foreach ($categoryId in $CategoryIds) {
        $products += @((Invoke-Api GET "/api/v1/products?storeId=$StoreId&categoryId=$categoryId&size=100").content |
            Where-Object { $_.status -eq "ACTIVE" })
    }
    return $products
}

function Get-ItemsTotal($Lines) {
    $total = 0.0
    foreach ($line in $Lines) { $total += $line.Product.sellingPrice * $line.Quantity }
    return $total
}

# The random choices of every possible order number, all from one generator with a fixed seed, so a second run makes
# the same choices. (One generator per order with the seeds 1, 2, 3 ... would not do: .NET's Random starts with nearly
# the same numbers for neighbouring seeds, and the first 13 orders all went to the same partners.)
$generator = New-Object System.Random $Seed
$Choices = @{}
for ($n = 1; $n -le 60; $n++) {
    $Choices[$n] = @{ Partner = $generator.NextDouble(); Payment = $generator.NextDouble(); Coupon = $generator.NextDouble()
                      ProductSeed = $generator.Next() }
}

# Sample order number N: odd numbers are Fresh Mart orders, even numbers Green Basket orders, and the store's
# customers take turns. The partner (one of the zone's), the payment, the coupon and the products are drawn at random.
function New-OrderPlan([int] $Number) {
    $choice = $Choices[$Number]
    $random = New-Object System.Random $choice.ProductSeed
    $storeId = if ($Number % 2 -eq 1) { 1 } else { 2 }
    $buyers = @($Customers | Where-Object { $_.AddressIds.ContainsKey($storeId) })
    $customer = $buyers[[int] [math]::Floor(($Number - 1) / 2) % $buyers.Count]
    $zonePartners = @($Partners | Where-Object { $_.Zone -eq $Stores[$storeId].Zone })
    return @{
        Number    = $Number
        StoreId   = $storeId
        Customer  = $customer
        AddressId = $customer.AddressIds[$storeId]
        Partner   = $zonePartners[[int] [math]::Floor($choice.Partner * $zonePartners.Count)]
        Payment   = if ($choice.Payment -lt 0.4) { "ONLINE" } else { "COD" }
        UseCoupon = $choice.Coupon -lt 0.3
        Random    = $random
        Lines     = @()
    }
}

# The products of a new order: one product from each of 2-5 different categories, 1-3 packs each (household: 1),
# never below the stock limit; then more packs until the items reach the minimum order of 99.00
function Add-Products($Plan) {
    $random = $Plan.Random
    $wanted = $random.Next(2, 6)
    foreach ($categoryId in ($CategoryIds | Sort-Object { $random.Next() })) {
        if ($Plan.Lines.Count -ge $wanted) { break }
        $candidates = @($StoreProducts[$Plan.StoreId] |
            Where-Object { $_.categoryId -eq $categoryId -and $StockLeft[$_.id] -gt $MinimumStockLeft })
        if ($candidates.Count -eq 0) { continue }
        $product = $candidates[$random.Next($candidates.Count)]
        $most = if ($categoryId -eq $HouseholdCategoryId) { 1 } else { 3 }
        $most = [math]::Min($most, $StockLeft[$product.id] - $MinimumStockLeft)
        $quantity = $random.Next(1, $most + 1)
        $Plan.Lines += @{ Product = $product; Quantity = $quantity }
        $StockLeft[$product.id] -= $quantity
    }
    while ((Get-ItemsTotal $Plan.Lines) -lt 99) {
        $line = $Plan.Lines | Where-Object { $_.Quantity -lt 20 -and $StockLeft[$_.Product.id] -gt $MinimumStockLeft } |
            Select-Object -First 1
        if (-not $line) { throw "Not enough stock left in $($Stores[$Plan.StoreId].Name) for sample order $($Plan.Number)." }
        $line.Quantity++
        $StockLeft[$line.Product.id]--
    }
    # A coupon of the store, when the items reach its minimum (cart-service checks it, and its per-customer limit)
    if ($Plan.UseCoupon -and (Get-ItemsTotal $Plan.Lines) -ge $Stores[$Plan.StoreId].CouponMinimum) {
        $Plan.Coupon = $Stores[$Plan.StoreId].Coupon
    }
}

# Fills the customer's cart and places the order, as the customer does on the website
function New-SampleOrder($Plan) {
    $email = $Plan.Customer.Email
    $number = "{0:D2}" -f $Plan.Number
    Invoke-Api DELETE "/api/v1/cart" $email | Out-Null
    foreach ($line in $Plan.Lines) {
        Invoke-Api POST "/api/v1/cart/items" $email @{ productId = $line.Product.id; quantity = $line.Quantity } | Out-Null
    }
    if ($Plan.Coupon) {
        try { Invoke-Api PUT "/api/v1/cart/coupon" $email @{ code = $Plan.Coupon } | Out-Null }
        catch { Write-Host "  coupon $($Plan.Coupon) not used: $_" -ForegroundColor Yellow }
    }
    return Invoke-Api POST "/api/v1/orders" $email @{ addressId = $Plan.AddressId; paymentMethod = $Plan.Payment
        note = "Sample order $number (test data)" } @{ "Idempotency-Key" = "sample-order-$number" }
}

# Takes the order from where it is to DELIVERED: payment, the vendor's steps, the partner's steps
function Complete-SampleOrder($Order, $Plan) {
    $id = $Order.id
    $email = $Plan.Customer.Email
    $vendor = $Stores[$Order.storeId].Vendor

    # 1. Payment: cash on delivery is confirmed once the stock is reserved. An online payment waits for the payment
    #    page (AWAITING_PAYMENT) and is paid with the mock gateway.
    if ($Order.paymentMethod -eq "ONLINE") {
        $payment = Wait-Until {
            try { $p = Invoke-Api GET "/api/v1/payments/orders/$id" $email; if ($p.status -ne "PENDING") { $p } }
            catch { if ("$_" -notmatch '\(404\)') { throw } }
        } "the payment of order $id" 60
        if ($payment.status -eq "AWAITING_PAYMENT") {
            Invoke-Api POST "/api/v1/payments/orders/$id/mock-pay" $email @{ success = $true } | Out-Null
        }
    }
    $Order = Wait-Until {
        $o = Invoke-Api GET "/api/v1/orders/$id" $email
        if ($o.status -ne "PENDING") { $o }
    } "order $id to be confirmed" 60
    if ($Order.status -in @("CANCELLED", "REJECTED", "DELIVERY_FAILED")) {
        throw "Order $id is $($Order.status): $($Order.statusReason)"
    }

    # 2. The vendor: accept, pack, hand over. The delivery is created at the hand-over, so the partner goes online first.
    $vendorSteps = [ordered]@{ CONFIRMED = "accept"; ACCEPTED = "pack"; PACKED = "ready-for-pickup" }
    while ($vendorSteps.Contains($Order.status)) {
        if ($Order.status -eq "PACKED") { Set-OnlyOnline $Plan.Partner }
        Invoke-Api POST "/api/v1/vendor/orders/$id/$($vendorSteps[$Order.status])" $vendor | Out-Null
        $Order = Invoke-Api GET "/api/v1/orders/$id" $email
    }

    # 3. The partner: accept, pick up (the order is OUT_FOR_DELIVERY), start the ride, deliver
    if ($Order.status -in @("READY_FOR_PICKUP", "OUT_FOR_DELIVERY")) {
        $partner = $Plan.Partner
        if ($Order.status -eq "READY_FOR_PICKUP") { Set-OnlyOnline $partner }
        $delivery = Wait-Until {
            (Invoke-Api GET "/api/v1/delivery/deliveries?active=true" $partner.Email).content | Where-Object { $_.orderId -eq $id }
        } "the delivery of order $id to be given to $($partner.Name)" 60
        $partnerSteps = [ordered]@{ ASSIGNED = "accept"; ACCEPTED = "pick-up"; PICKED_UP = "start"; OUT_FOR_DELIVERY = "deliver" }
        while ($partnerSteps.Contains($delivery.status)) {
            Invoke-Api POST "/api/v1/delivery/deliveries/$($delivery.id)/$($partnerSteps[$delivery.status])" $partner.Email | Out-Null
            $delivery = Invoke-Api GET "/api/v1/delivery/deliveries/$($delivery.id)" $partner.Email
        }
    }

    # order-service marks it DELIVERED (and a cash payment PAID) when the DeliveryCompleted event arrives
    return Wait-Until {
        $o = Invoke-Api GET "/api/v1/orders/$id" $email
        if ($o.status -eq "DELIVERED" -and $o.paymentStatus -eq "PAID") { $o }
    } "order $id to be delivered and paid" 60
}

# Puts back a customer's cart as it was before the run (products and coupon)
function Restore-Cart([string] $Email, $Cart) {
    Invoke-Api DELETE "/api/v1/cart" $Email | Out-Null
    foreach ($item in $Cart.items) {
        try { Invoke-Api POST "/api/v1/cart/items" $Email @{ productId = $item.productId; quantity = $item.quantity } | Out-Null }
        catch { Write-Host "  could not put $($item.productName) back into the cart of ${Email}: $_" -ForegroundColor Yellow }
    }
    if ($Cart.couponCode) {
        try { Invoke-Api PUT "/api/v1/cart/coupon" $Email @{ code = $Cart.couponCode } | Out-Null }
        catch { Write-Host "  could not put the coupon $($Cart.couponCode) back into the cart of ${Email}: $_" -ForegroundColor Yellow }
    }
}

# ----- The checks --------------------------------------------------------------------------------------------------

# Checks each sample order the way the panels show it. Returns the number of problems.
function Test-SampleOrders($Samples) {
    $problems = New-Object System.Collections.Generic.List[string]
    $size = "size=200"

    # What each panel lists (the same endpoints the website uses)
    $adminOrders = @((Invoke-Api GET "/api/v1/admin/orders?status=DELIVERED&$size" $Admin).content | ForEach-Object { $_.id })
    $adminDeliveries = @{}
    foreach ($delivery in (Invoke-Api GET "/api/v1/admin/deliveries?status=DELIVERED&$size" $Admin).content) {
        $adminDeliveries[$delivery.orderId] = $delivery
    }
    $vendorOrders = @{}
    foreach ($storeId in $Stores.Keys) {
        $vendorOrders[$storeId] = @((Invoke-Api GET "/api/v1/vendor/orders?status=DELIVERED&$size" $Stores[$storeId].Vendor).content |
            ForEach-Object { $_.id })
    }
    $partnerDeliveries = @{}
    foreach ($partner in $Partners) {
        try { $userId = (Invoke-Api GET "/api/v1/delivery/profile" $partner.Email).userId }
        catch { if ("$_" -match '\(401\)') { continue } else { throw } }
        $partnerDeliveries[$userId] = @((Invoke-Api GET "/api/v1/delivery/deliveries?active=false&$size" $partner.Email).content |
            ForEach-Object { $_.orderId })
    }
    $customerOrders = @{}

    $rows = @()
    foreach ($number in ($Samples.Keys | Sort-Object)) {
        $customer = $Samples[$number].Customer
        $id = $Samples[$number].Order.id
        if (-not $customerOrders.ContainsKey($customer.Email)) {
            $customerOrders[$customer.Email] = @((Invoke-Api GET "/api/v1/orders?status=DELIVERED&$size" $customer.Email).content |
                ForEach-Object { $_.id })
        }
        $order = Invoke-Api GET "/api/v1/orders/$id" $customer.Email
        $payment = Invoke-Api GET "/api/v1/payments/orders/$id" $customer.Email
        $delivery = $adminDeliveries[$id]
        $amounts = $order.amounts
        $check = { param([bool] $ok, [string] $what) if (-not $ok) { $problems.Add("Sample order $number (order ${id}): $what") } }

        & $check ($order.status -eq "DELIVERED") "the status is $($order.status)"
        & $check ($order.paymentStatus -eq "PAID") "the payment status is $($order.paymentStatus)"
        # The money: each line, the items, the delivery fee (free from 299.00), the total, the payment
        $itemsSum = 0.0
        foreach ($item in $order.items) {
            & $check (Test-Amount $item.lineTotal ($item.unitPrice * $item.quantity)) "$($item.productName): $($item.lineTotal) is not $($item.quantity) x $($item.unitPrice)"
            $itemsSum += $item.lineTotal
        }
        & $check (Test-Amount $amounts.itemsTotal $itemsSum) "the items total $($amounts.itemsTotal) is not the sum of the lines ($itemsSum)"
        $fee = if ($amounts.itemsTotal -ge 299) { 0 } else { 30 }
        & $check (Test-Amount $amounts.deliveryFee $fee) "the delivery fee is $($amounts.deliveryFee), expected $fee"
        & $check (Test-Amount $amounts.totalAmount ($amounts.itemsTotal - $amounts.couponDiscount + $amounts.deliveryFee)) "the total $($amounts.totalAmount) is not items - coupon + delivery"
        & $check (Test-Amount $payment.amount $amounts.totalAmount) "the payment is $($payment.amount), the order $($amounts.totalAmount)"
        $paymentStatus = if ($order.paymentMethod -eq "COD") { "COLLECTED" } else { "PAID" }
        & $check ($payment.status -eq $paymentStatus) "the payment is $($payment.status), expected $paymentStatus"
        # The panels
        & $check ($adminOrders -contains $id) "not in the admin's delivered orders"
        & $check ($vendorOrders[$order.storeId] -contains $id) "not in $($order.storeName)'s delivered orders"
        & $check ($customerOrders[$customer.Email] -contains $id) "not in the order history of $($customer.Email)"
        & $check ($null -ne $delivery) "no delivered delivery in the admin's deliveries"
        if ($delivery) {
            & $check ($partnerDeliveries[$delivery.partnerUserId] -contains $id) "not in the partner's completed deliveries"
            $toCollect = if ($order.paymentMethod -eq "COD") { $amounts.totalAmount } else { 0 }
            & $check (Test-Amount $delivery.amountToCollect $toCollect) "the partner had to collect $($delivery.amountToCollect), expected $toCollect"
        }

        $rows += [pscustomobject]@{
            No       = $number
            Order    = $id
            Customer = $order.deliveryAddress.contactName
            Store    = $order.storeName
            Products = $order.items.Count
            Payment  = "$($order.paymentMethod) $($payment.status)"
            Coupon   = if ($amounts.couponCode) { "$($amounts.couponCode) -$($amounts.couponDiscount)" } else { "" }
            Delivery = $amounts.deliveryFee
            Total    = $amounts.totalAmount
            Partner  = $order.deliveryPartnerName
        }
    }
    $rows | Format-Table -AutoSize | Out-Host

    # Paging: 10 per page must give the right number of pages, and the last page the rest
    $firstPage = Invoke-Api GET "/api/v1/admin/orders?status=DELIVERED&size=10&page=0" $Admin
    $pages = [math]::Ceiling($firstPage.totalElements / 10)
    $lastPage = Invoke-Api GET "/api/v1/admin/orders?status=DELIVERED&size=10&page=$($pages - 1)" $Admin
    if ($firstPage.totalPages -ne $pages -or $lastPage.content.Count -ne $firstPage.totalElements - 10 * ($pages - 1)) {
        $problems.Add("Admin orders, 10 per page: $($firstPage.totalElements) orders in $($firstPage.totalPages) pages, the last page has $($lastPage.content.Count)")
    }
    Write-Host "Admin orders, delivered, 10 per page: $($firstPage.totalElements) orders on $($firstPage.totalPages) pages"

    # The dashboard counts every order from the events (report-service)
    $dashboard = Invoke-Api GET "/api/v1/admin/reports/dashboard" $Admin
    Write-Host ("Admin dashboard: {0} orders in total, {1} today, sales today Rs. {2}, average delivery {3} min" -f
        $dashboard.totalOrders, $dashboard.ordersToday, $dashboard.salesToday, $dashboard.averageDeliveryMinutes)
    if ($dashboard.totalOrders -lt $Samples.Count) {
        $problems.Add("The dashboard counts $($dashboard.totalOrders) orders, fewer than the $($Samples.Count) sample orders")
    }

    foreach ($problem in $problems) { Write-Host "  PROBLEM: $problem" -ForegroundColor Red }
    return $problems.Count
}

# ----- The run -------------------------------------------------------------------------------------------------------

if ($VerifyOnly) {
    $samples = Find-SampleOrders
    if ($samples.Count -eq 0) { throw "There are no sample orders yet: run the script without -VerifyOnly first." }
    Write-Host "Checking the $($samples.Count) sample orders..." -ForegroundColor Cyan
    if ((Test-SampleOrders $samples) -gt 0) { exit 1 }
    Write-Host "All sample orders are consistent in every panel." -ForegroundColor Green
    exit 0
}

Write-Host "1. Accounts (customers, delivery partners)" -ForegroundColor Cyan
foreach ($customer in $Customers) { Initialize-Customer $customer }
foreach ($partner in $Partners) { Initialize-Partner $partner }

$samples = Find-SampleOrders

# A delivery that waits for a partner would go to the first partner who comes online, instead of the sample order;
# a partner with another delivery on the way gets no new one
$waiting = @((Invoke-Api GET "/api/v1/admin/deliveries?status=WAITING_FOR_PARTNER&size=50" $Admin).content |
    Where-Object { $orderId = $_.orderId; -not ($samples.Values | Where-Object { $_.Order.id -eq $orderId }) })
if ($waiting.Count -gt 0) {
    throw "Order(s) $(($waiting | ForEach-Object { $_.orderId }) -join ', ') wait for a delivery partner. Assign them (admin, Deliveries) or cancel them first."
}
foreach ($partner in $Partners) {
    $busy = @((Invoke-Api GET "/api/v1/delivery/deliveries?active=true" $partner.Email).content |
        Where-Object { $orderId = $_.orderId; -not ($samples.Values | Where-Object { $_.Order.id -eq $orderId }) })
    if ($busy.Count -gt 0) {
        throw "$($partner.Name) is busy with order $($busy[0].orderId) ($($busy[0].status)): finish it first."
    }
}

$StoreProducts = @{ 1 = @(Get-StoreProducts 1); 2 = @(Get-StoreProducts 2) }
$StockLeft = @{}
foreach ($product in $StoreProducts[1] + $StoreProducts[2]) { $StockLeft[$product.id] = [int] $product.availableQuantity }

# customer1's and customer2's carts, to put them back at the end
$savedCarts = @{}
foreach ($customer in $Customers | Where-Object { $_.Existing }) {
    $savedCarts[$customer.Email] = Invoke-Api GET "/api/v1/cart" $customer.Email
}
$changedCarts = @{}

Write-Host "2. Sample orders 1-$Count" -ForegroundColor Cyan
try {
    for ($number = 1; $number -le $Count; $number++) {
        $plan = New-OrderPlan $number
        $label = "Sample order {0:D2}" -f $number
        if ($samples.ContainsKey($number)) {
            $order = $samples[$number].Order
            if ($order.status -eq "DELIVERED") {
                Write-Host "  ${label}: order $($order.id) is delivered already"
                continue
            }
            Write-Host "  ${label}: finishing order $($order.id) ($($order.status))"
        } else {
            Add-Products $plan
            $order = New-SampleOrder $plan
            $changedCarts[$plan.Customer.Email] = $true
            $products = ($plan.Lines | ForEach-Object { "$($_.Quantity) x $($_.Product.name)" }) -join ", "
            Write-Host "  ${label}: order $($order.id), $($Stores[$plan.StoreId].Name), $($plan.Payment)$(if ($plan.Coupon) { ", $($plan.Coupon)" }): $products"
        }
        $order = Complete-SampleOrder $order $plan
        $samples[$number] = @{ Order = $order; Customer = $plan.Customer }
        Write-Host ("    delivered by {0}, total Rs. {1}" -f $order.deliveryPartnerName, $order.amounts.totalAmount) -ForegroundColor Green
    }
} finally {
    Write-Host "3. Putting back the carts and the partners' online status" -ForegroundColor Cyan
    foreach ($email in $changedCarts.Keys) {
        if ($savedCarts.ContainsKey($email)) { Restore-Cart $email $savedCarts[$email] }
    }
    foreach ($partner in $Partners) {
        $state = if ($partner.WasOnline) { "online" } else { "offline" }
        try { Invoke-Api POST "/api/v1/delivery/availability/$state" $partner.Email | Out-Null }
        catch { Write-Host "  could not set $($partner.Name) $($state): $_" -ForegroundColor Yellow }
    }
}

Write-Host "4. Checks" -ForegroundColor Cyan
if ((Test-SampleOrders $samples) -gt 0) { exit 1 }
Write-Host "Done: $($samples.Count) sample orders, delivered and consistent in every panel." -ForegroundColor Green
