param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "seed-admin@example.com",
    [string]$AdminPassword = "DemoAdmin123!",
    [string]$CustomerEmail = "demo-customer@example.com",
    [string]$CustomerPassword = "DemoCustomer123!"
)

$ErrorActionPreference = "Stop"

function Invoke-ShopupuJson {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
    }

    if ($null -ne $Body) {
        $params["ContentType"] = "application/json"
        $params["Body"] = ($Body | ConvertTo-Json -Depth 10)
    }

    Invoke-RestMethod @params
}

function Try-ShopupuJson {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null
    )

    try {
        Invoke-ShopupuJson -Method $Method -Path $Path -Body $Body -Token $Token
    } catch {
        $response = $_.Exception.Response
        if ($response -and $response.StatusCode.value__ -eq 409) {
            return $null
        }
        throw
    }
}

$runId = Get-Date -Format "yyyyMMddHHmmss"

$adminTokens = Invoke-ShopupuJson -Method "POST" -Path "/api/auth/login" -Body @{
    email = $AdminEmail
    password = $AdminPassword
}
$adminToken = $adminTokens.accessToken

$electronics = Try-ShopupuJson -Method "POST" -Path "/api/admin/catalog/categories" -Token $adminToken -Body @{
    name = "Demo Electronics $runId"
    slug = "demo-electronics-$runId"
    description = "Demo electronics category"
    parentId = $null
}

$clothes = Try-ShopupuJson -Method "POST" -Path "/api/admin/catalog/categories" -Token $adminToken -Body @{
    name = "Demo Clothes $runId"
    slug = "demo-clothes-$runId"
    description = "Demo clothes category"
    parentId = $null
}

$books = Try-ShopupuJson -Method "POST" -Path "/api/admin/catalog/categories" -Token $adminToken -Body @{
    name = "Demo Books $runId"
    slug = "demo-books-$runId"
    description = "Demo books category"
    parentId = $null
}

$phone = Invoke-ShopupuJson -Method "POST" -Path "/api/admin/catalog/products" -Token $adminToken -Body @{
    categoryId = $electronics.id
    title = "Demo Phone $runId"
    description = "Seeded smartphone for manual API testing"
    price = 699.99
    sku = "DEMO-PHONE-$runId"
    stock = 25
    enabled = $true
}

$hoodie = Invoke-ShopupuJson -Method "POST" -Path "/api/admin/catalog/products" -Token $adminToken -Body @{
    categoryId = $clothes.id
    title = "Demo Hoodie $runId"
    description = "Seeded hoodie for cart and checkout testing"
    price = 59.90
    sku = "DEMO-HOODIE-$runId"
    stock = 40
    enabled = $true
}

$book = Invoke-ShopupuJson -Method "POST" -Path "/api/admin/catalog/products" -Token $adminToken -Body @{
    categoryId = $books.id
    title = "Demo Architecture Book $runId"
    description = "Seeded book for product search testing"
    price = 34.50
    sku = "DEMO-BOOK-$runId"
    stock = 18
    enabled = $true
}

$customerTokens = $null
try {
    $customerTokens = Invoke-ShopupuJson -Method "POST" -Path "/api/auth/register" -Body @{
        email = $CustomerEmail
        password = $CustomerPassword
    }
} catch {
    $customerTokens = Invoke-ShopupuJson -Method "POST" -Path "/api/auth/login" -Body @{
        email = $CustomerEmail
        password = $CustomerPassword
    }
}
$customerToken = $customerTokens.accessToken

$cartAfterPhone = Invoke-ShopupuJson -Method "POST" -Path "/api/cart/items" -Token $customerToken -Body @{
    productId = $phone.id
    quantity = 1
}

$cartAfterHoodie = Invoke-ShopupuJson -Method "POST" -Path "/api/cart/items" -Token $customerToken -Body @{
    productId = $hoodie.id
    quantity = 2
}

$order = Invoke-ShopupuJson -Method "POST" -Path "/api/orders/checkout" -Token $customerToken

$shipmentAddress = Invoke-ShopupuJson -Method "POST" -Path "/api/shipping/address" -Token $customerToken -Body @{
    orderId = $order.id
    fullName = "Demo Customer"
    line1 = "Demo Street 10"
    line2 = "Apartment 5"
    city = "Berlin"
    state = "Berlin"
    postalCode = "10115"
    country = "Germany"
}

$shipmentMethod = Invoke-ShopupuJson -Method "POST" -Path "/api/shipping/method" -Token $customerToken -Body @{
    orderId = $order.id
    method = "DHL"
}

$payment = Invoke-ShopupuJson -Method "POST" -Path "/api/payments" -Token $customerToken -Body @{
    orderId = $order.id
}

[ordered]@{
    baseUrl = $BaseUrl
    adminEmail = $AdminEmail
    customerEmail = $CustomerEmail
    customerPassword = $CustomerPassword
    categories = @($electronics, $clothes, $books)
    products = @($phone, $hoodie, $book)
    cart = $cartAfterHoodie
    order = $order
    shipmentAddress = $shipmentAddress
    shipmentMethod = $shipmentMethod
    payment = $payment
} | ConvertTo-Json -Depth 20
