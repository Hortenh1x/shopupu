param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "catalog.admin@shopupu.local",
    [string]$AdminPassword = "ShopupuCatalogAdmin2026!",
    [string]$ImageDir = "$PSScriptRoot\web-product-images"
)

$ErrorActionPreference = "Stop"

$UserAgent = "ShopupuLocalSeeder/1.0 (dmytro.bolibok@gmail.com)"

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

function ConvertTo-Array {
    param([object]$Value)

    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [array]) {
        return $Value
    }
    if ($Value.PSObject.Properties.Name -contains "value") {
        return @($Value.value)
    }
    return @($Value)
}

function Get-OpenverseImage {
    param([string]$Query)

    $encodedQuery = [uri]::EscapeDataString($Query)
    $url = "https://api.openverse.engineering/v1/images/?q=$encodedQuery&page_size=8&license_type=commercial,modification&source=flickr,wikimedia&mature=false"
    $result = Invoke-RestMethod -Uri $url -Headers @{ "User-Agent" = $UserAgent } -TimeoutSec 30

    if ($null -eq $result.results -or $result.results.Count -eq 0) {
        throw "No Openverse image found for query: $Query"
    }

    foreach ($image in $result.results) {
        if ($image.mature -eq $true) {
            continue
        }
        if (-not $image.url) {
            continue
        }

        $downloadUrl = Get-DownloadUrl -Image $image
        if ($downloadUrl -match '\.svg($|\?)' -or $downloadUrl -match '\.gif($|\?)' -or $downloadUrl -match '\.tif($|\?)') {
            continue
        }

        return [pscustomobject]@{
            title = $image.title
            sourcePage = $image.foreign_landing_url
            downloadUrl = $downloadUrl
            license = "$($image.license) $($image.license_version)".Trim()
            licenseUrl = $image.license_url
            artist = $image.creator
            provider = $image.provider
            attribution = $image.attribution
        }
    }

    throw "Only unsupported Openverse files found for query: $Query"
}

function Get-DownloadUrl {
    param([object]$Image)

    $url = [string]$Image.url
    if ($Image.source -eq "flickr" -and $url -match '^(?<base>.+)_([a-z])\.(?<ext>jpe?g)$') {
        return "$($Matches.base)_z.$($Matches.ext)"
    }

    if ($Image.source -eq "flickr" -and $url -match '^(?<base>.+)\.(?<ext>jpe?g)$') {
        return "$($Matches.base)_z.$($Matches.ext)"
    }

    if ($Image.thumbnail) {
        return [string]$Image.thumbnail
    }

    return $url
}

function Download-Image {
    param(
        [string]$Url,
        [string]$Path
    )

    $lastError = $null
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        try {
            Invoke-WebRequest -Uri $Url -OutFile $Path -Headers @{ "User-Agent" = $UserAgent } -TimeoutSec 60
            return
        } catch {
            $lastError = $_
            if ($attempt -eq 4) {
                break
            }
            Start-Sleep -Seconds (5 * $attempt)
        }
    }

    throw $lastError
}

function Upload-ProductImage {
    param(
        [long]$ProductId,
        [string]$FilePath,
        [string]$AltText,
        [string]$Token
    )

    $lowerPath = $FilePath.ToLowerInvariant()
    $contentType = if ($lowerPath.EndsWith(".png")) {
        "image/png"
    } elseif ($lowerPath.EndsWith(".webp")) {
        "image/webp"
    } else {
        "image/jpeg"
    }
    $arguments = @(
        "-sS",
        "-X", "POST",
        "$BaseUrl/api/admin/catalog/products/$ProductId/images",
        "-H", "Authorization: Bearer $Token",
        "-F", "file=@$FilePath;type=$contentType",
        "-F", "altText=$AltText",
        "-F", "sortOrder=0"
    )

    $json = & curl.exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Image upload failed for product $ProductId"
    }
    $response = $json | ConvertFrom-Json
    if ($response.status -and [int]$response.status -ge 400) {
        throw "Image upload failed for product $ProductId`: $($response.detail)"
    }
    if (-not $response.id) {
        throw "Image upload failed for product $ProductId`: unexpected response"
    }
    return $response
}

function Delete-ProductImage {
    param(
        [long]$ProductId,
        [long]$ImageId,
        [string]$Token
    )

    try {
        Invoke-ShopupuJson -Method "DELETE" -Path "/api/admin/catalog/products/$ProductId/images/$ImageId" -Token $Token | Out-Null
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -ne 404) {
            throw
        }
    }
}

$imageQueries = @{
    "AUDIO-MERIDIAN-HEADPHONES" = "wireless headphones"
    "AUDIO-SIGNAL-SPEAKER" = "portable bluetooth speaker"
    "DESK-COMPACT-KEYBOARD" = "mechanical keyboard"
    "DESK-AURORA-LAMP" = "desk lamp"
    "DESK-OBSIDIAN-NOTEBOOK" = "black notebook"
    "WEAR-NORTHLINE-COAT" = "wool coat"
    "WEAR-STONE-OVERSHIRT" = "button down shirt clothing"
    "BAG-ARCHIVE-TOTE" = "canvas tote bag"
    "BAG-COMMUTER-SLING" = "messenger bag"
    "BOOK-DESIGN-SYSTEMS-GUIDE" = "design systems book"
    "BOOK-EASTERN-MODERNISM" = "architecture book"
    "HOME-MATTE-BLACK-CUP" = "black ceramic coffee cup"
    "HOME-CONCRETE-POT-SET" = "concrete plant pot"
    "HOME-LINEN-THROW" = "linen throw blanket"
    "DESK-STEEL-WATER-BOTTLE" = "steel water bottle"
    "HOME-MINIMAL-WALL-CLOCK" = "white wall clock"
}

New-Item -ItemType Directory -Force -Path $ImageDir | Out-Null

$loginBody = @{
    email = $AdminEmail
    password = $AdminPassword
}
$adminToken = (Invoke-ShopupuJson -Method "POST" -Path "/api/auth/login" -Body $loginBody).accessToken
$products = ConvertTo-Array (Invoke-ShopupuJson -Method "GET" -Path "/api/admin/catalog/products" -Token $adminToken)
$updated = @()

foreach ($product in $products) {
    if (-not $imageQueries.ContainsKey($product.sku)) {
        continue
    }

    $query = $imageQueries[$product.sku]
    $oldImages = ConvertTo-Array $product.images
    $internetImage = Get-OpenverseImage -Query $query
    Start-Sleep -Milliseconds 700
    $extension = if ($internetImage.downloadUrl -match '\.png($|\?)') {
        "png"
    } elseif ($internetImage.downloadUrl -match '\.webp($|\?)') {
        "webp"
    } else {
        "jpg"
    }
    $fileName = $product.sku.ToLowerInvariant() + "." + $extension
    $filePath = Join-Path $ImageDir $fileName

    Download-Image -Url $internetImage.downloadUrl -Path $filePath
    Start-Sleep -Milliseconds 700

    $uploadedImage = Upload-ProductImage -ProductId $product.id -FilePath $filePath -AltText $product.title -Token $adminToken
    foreach ($image in $oldImages) {
        if ($image.id -ne $uploadedImage.id) {
            Delete-ProductImage -ProductId $product.id -ImageId $image.id -Token $adminToken
        }
    }

    $updated += [ordered]@{
        productId = $product.id
        title = $product.title
        sku = $product.sku
        query = $query
        image = $uploadedImage
        source = $internetImage
    }
}

$updated | ConvertTo-Json -Depth 20
