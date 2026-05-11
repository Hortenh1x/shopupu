param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "catalog.admin@shopupu.local",
    [string]$AdminPassword = "ShopupuCatalogAdmin2026!",
    [string]$ImageDir = "$PSScriptRoot\catalog-images"
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

function Find-BySlug {
    param([array]$Items, [string]$Slug)
    return $Items | Where-Object { $_.slug -eq $Slug } | Select-Object -First 1
}

function Find-BySku {
    param([array]$Items, [string]$Sku)
    return $Items | Where-Object { $_.sku -eq $Sku } | Select-Object -First 1
}

function Draw-ProductImage {
    param(
        [object]$Product,
        [string]$Path
    )

    Add-Type -AssemblyName System.Drawing

    $width = 1200
    $height = 850
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

    $bg = [System.Drawing.ColorTranslator]::FromHtml($Product.background)
    $panel = [System.Drawing.ColorTranslator]::FromHtml($Product.panel)
    $accent = [System.Drawing.ColorTranslator]::FromHtml($Product.accent)
    $text = [System.Drawing.ColorTranslator]::FromHtml("#F2F4F8")
    $muted = [System.Drawing.ColorTranslator]::FromHtml("#AAB2C0")

    $graphics.Clear($bg)
    $graphics.FillRectangle((New-Object System.Drawing.SolidBrush($panel)), 92, 92, 1016, 666)
    $graphics.DrawRectangle((New-Object System.Drawing.Pen($accent, 7)), 92, 92, 1016, 666)
    $graphics.FillEllipse((New-Object System.Drawing.SolidBrush($accent)), 800, 170, 210, 210)
    $graphics.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(80, $accent))), 172, 540, 520, 84)
    $graphics.DrawLine((New-Object System.Drawing.Pen($muted, 3)), 172, 500, 620, 500)

    $titleFont = New-Object System.Drawing.Font("Segoe UI Semibold", 38, [System.Drawing.FontStyle]::Bold)
    $metaFont = New-Object System.Drawing.Font("Segoe UI", 22, [System.Drawing.FontStyle]::Regular)
    $brandFont = New-Object System.Drawing.Font("Consolas", 20, [System.Drawing.FontStyle]::Regular)

    $titleBrush = New-Object System.Drawing.SolidBrush($text)
    $mutedBrush = New-Object System.Drawing.SolidBrush($muted)

    $graphics.DrawString($Product.title, $titleFont, $titleBrush, 172, 178)
    $graphics.DrawString($Product.categoryName, $metaFont, $mutedBrush, 176, 440)
    $graphics.DrawString("shopupu", $brandFont, $mutedBrush, 176, 682)

    $productShapeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(210, $accent))
    switch ($Product.shape) {
        "lamp" {
            $graphics.FillRectangle($productShapeBrush, 825, 305, 90, 270)
            $graphics.FillPolygon($productShapeBrush, @(
                (New-Object System.Drawing.Point(760, 305)),
                (New-Object System.Drawing.Point(980, 305)),
                (New-Object System.Drawing.Point(930, 215)),
                (New-Object System.Drawing.Point(810, 215))
            ))
        }
        "book" {
            $graphics.FillRectangle($productShapeBrush, 760, 250, 220, 330)
            $graphics.DrawLine((New-Object System.Drawing.Pen($bg, 6)), 815, 250, 815, 580)
        }
        "audio" {
            $graphics.FillEllipse($productShapeBrush, 735, 260, 135, 250)
            $graphics.FillEllipse($productShapeBrush, 910, 260, 135, 250)
            $graphics.DrawArc((New-Object System.Drawing.Pen($accent, 18)), 795, 180, 195, 210, 190, 160)
        }
        "bag" {
            $graphics.FillRectangle($productShapeBrush, 745, 320, 290, 240)
            $graphics.DrawArc((New-Object System.Drawing.Pen($accent, 14)), 805, 235, 170, 170, 190, 160)
        }
        "wear" {
            $graphics.FillPolygon($productShapeBrush, @(
                (New-Object System.Drawing.Point(780, 240)),
                (New-Object System.Drawing.Point(1000, 240)),
                (New-Object System.Drawing.Point(1060, 410)),
                (New-Object System.Drawing.Point(980, 450)),
                (New-Object System.Drawing.Point(960, 600)),
                (New-Object System.Drawing.Point(820, 600)),
                (New-Object System.Drawing.Point(800, 450)),
                (New-Object System.Drawing.Point(720, 410))
            ))
        }
        default {
            $graphics.FillRectangle($productShapeBrush, 760, 270, 260, 260)
            $graphics.FillEllipse((New-Object System.Drawing.SolidBrush($panel)), 825, 335, 130, 130)
        }
    }

    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}

function Upload-ProductImage {
    param(
        [long]$ProductId,
        [string]$FilePath,
        [string]$AltText,
        [string]$Token
    )

    $arguments = @(
        "-sS",
        "-X", "POST",
        "$BaseUrl/api/admin/catalog/products/$ProductId/images",
        "-H", "Authorization: Bearer $Token",
        "-F", "file=@$FilePath;type=image/png",
        "-F", "altText=$AltText",
        "-F", "sortOrder=0"
    )

    $json = & curl.exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Image upload failed for product $ProductId"
    }
    return $json | ConvertFrom-Json
}

$loginBody = @{
    email = $AdminEmail
    password = $AdminPassword
}
$adminToken = (Invoke-ShopupuJson -Method "POST" -Path "/api/auth/login" -Body $loginBody).accessToken

$categories = ConvertTo-Array (Invoke-ShopupuJson -Method "GET" -Path "/api/catalog/categories")
$usedDemoCategoryIds = @{}

function Ensure-Category {
    param(
        [string]$Name,
        [string]$Slug,
        [string]$Description
    )

    $existing = Find-BySlug -Items $script:categories -Slug $Slug
    if ($existing) {
        return $existing
    }

    $demoCategory = $script:categories |
            Where-Object { $_.slug -like "demo-*" -and -not $script:usedDemoCategoryIds.ContainsKey([string]$_.id) } |
            Select-Object -First 1

    $body = @{
        name = $Name
        slug = $Slug
        description = $Description
        parentId = $null
    }

    if ($demoCategory) {
        $script:usedDemoCategoryIds[[string]$demoCategory.id] = $true
        $updated = Invoke-ShopupuJson -Method "PUT" -Path "/api/admin/catalog/categories/$($demoCategory.id)" -Body $body -Token $adminToken
        $script:categories = @($script:categories | Where-Object { $_.id -ne $updated.id }) + @($updated)
        return $updated
    }

    $created = Invoke-ShopupuJson -Method "POST" -Path "/api/admin/catalog/categories" -Body $body -Token $adminToken
    $script:categories += $created
    return $created
}

$categoryMap = @{}
$categoryMap["audio-electronics"] = Ensure-Category -Name "Audio & Electronics" -Slug "audio-electronics" -Description "Compact devices and useful everyday electronics."
$categoryMap["desk-essentials"] = Ensure-Category -Name "Desk Essentials" -Slug "desk-essentials" -Description "Tools for a clean, focused workspace."
$categoryMap["outerwear"] = Ensure-Category -Name "Outerwear" -Slug "outerwear" -Description "Layered clothing for city weather."
$categoryMap["bags-carry"] = Ensure-Category -Name "Bags & Carry" -Slug "bags-carry" -Description "Bags designed for commuting and daily movement."
$categoryMap["books-objects"] = Ensure-Category -Name "Books & Objects" -Slug "books-objects" -Description "Printed matter and small objects with a design focus."
$categoryMap["home-goods"] = Ensure-Category -Name "Home Goods" -Slug "home-goods" -Description "Quiet, practical pieces for the home."

$products = @(
    @{
        title = "Meridian Wireless Headphones"; sku = "AUDIO-MERIDIAN-HEADPHONES"; categorySlug = "audio-electronics"; price = 149.00; stock = 24; shape = "audio";
        description = "Closed-back wireless headphones with a balanced sound profile, soft ear pads, and up to 38 hours of battery life.";
        background = "#20242C"; panel = "#2C313B"; accent = "#7FA884"
    },
    @{
        title = "Signal Portable Speaker"; sku = "AUDIO-SIGNAL-SPEAKER"; categorySlug = "audio-electronics"; price = 86.00; stock = 31; shape = "audio";
        description = "A compact portable speaker with a dense aluminum body, warm midrange, and splash-resistant housing.";
        background = "#1F2229"; panel = "#30343D"; accent = "#B7AD91"
    },
    @{
        title = "Compact Mechanical Keyboard"; sku = "DESK-COMPACT-KEYBOARD"; categorySlug = "desk-essentials"; price = 119.00; stock = 19; shape = "object";
        description = "A 75 percent mechanical keyboard with quiet tactile switches, hot-swap sockets, and a graphite case.";
        background = "#20242C"; panel = "#2B3039"; accent = "#9099A8"
    },
    @{
        title = "Aurora Desk Lamp, Graphite"; sku = "DESK-AURORA-LAMP"; categorySlug = "desk-essentials"; price = 89.00; stock = 17; shape = "lamp";
        description = "Adjustable desk lamp with a matte graphite finish, soft neutral light, and a small footprint.";
        background = "#1D2027"; panel = "#2B3039"; accent = "#C7786F"
    },
    @{
        title = "Obsidian Notebook A5"; sku = "DESK-OBSIDIAN-NOTEBOOK"; categorySlug = "desk-essentials"; price = 24.50; stock = 80; shape = "book";
        description = "A5 notebook with 160 pages of smooth ivory paper, lay-flat binding, and a durable black cloth cover.";
        background = "#20242C"; panel = "#2D323C"; accent = "#6F7A8A"
    },
    @{
        title = "Northline Wool Coat"; sku = "WEAR-NORTHLINE-COAT"; categorySlug = "outerwear"; price = 219.00; stock = 12; shape = "wear";
        description = "Straight-cut wool blend coat with a soft lining, hidden buttons, and a calm charcoal tone.";
        background = "#1E2128"; panel = "#30343D"; accent = "#B7AD91"
    },
    @{
        title = "Stone Cotton Overshirt"; sku = "WEAR-STONE-OVERSHIRT"; categorySlug = "outerwear"; price = 96.00; stock = 22; shape = "wear";
        description = "Heavy cotton overshirt with large chest pockets, relaxed shoulders, and a muted stone color.";
        background = "#22262E"; panel = "#323742"; accent = "#7FA884"
    },
    @{
        title = "Archive Canvas Tote"; sku = "BAG-ARCHIVE-TOTE"; categorySlug = "bags-carry"; price = 42.00; stock = 46; shape = "bag";
        description = "Heavy canvas tote with reinforced handles, an inner pocket, and enough room for a laptop and books.";
        background = "#20242C"; panel = "#2D323C"; accent = "#B7AD91"
    },
    @{
        title = "Commuter Sling Bag"; sku = "BAG-COMMUTER-SLING"; categorySlug = "bags-carry"; price = 68.00; stock = 28; shape = "bag";
        description = "Low-profile sling bag with weather-resistant fabric, quick-access front pocket, and adjustable strap.";
        background = "#1F2229"; panel = "#2B3039"; accent = "#9099A8"
    },
    @{
        title = "Design Systems Field Guide"; sku = "BOOK-DESIGN-SYSTEMS-GUIDE"; categorySlug = "books-objects"; price = 38.00; stock = 35; shape = "book";
        description = "A practical field guide about interface systems, tokens, naming, component governance, and product consistency.";
        background = "#20242C"; panel = "#30343D"; accent = "#C7786F"
    },
    @{
        title = "Eastern Modernism Photo Book"; sku = "BOOK-EASTERN-MODERNISM"; categorySlug = "books-objects"; price = 54.00; stock = 20; shape = "book";
        description = "Hardcover photo book documenting concrete forms, public spaces, and quiet modernist architecture.";
        background = "#1E2128"; panel = "#2C313B"; accent = "#B7AD91"
    },
    @{
        title = "Ceramic Coffee Cup, Matte Black"; sku = "HOME-MATTE-BLACK-CUP"; categorySlug = "home-goods"; price = 18.00; stock = 64; shape = "object";
        description = "Hand-finished ceramic cup with a matte black glaze, comfortable handle, and 280 ml capacity.";
        background = "#20242C"; panel = "#2B3039"; accent = "#7FA884"
    },
    @{
        title = "Concrete Plant Pot Set"; sku = "HOME-CONCRETE-POT-SET"; categorySlug = "home-goods"; price = 34.00; stock = 27; shape = "object";
        description = "Set of two small concrete plant pots with drainage trays and a smooth sealed interior.";
        background = "#22262E"; panel = "#323742"; accent = "#9099A8"
    },
    @{
        title = "Linen Throw Blanket"; sku = "HOME-LINEN-THROW"; categorySlug = "home-goods"; price = 74.00; stock = 18; shape = "object";
        description = "Soft linen-cotton throw blanket with a dry texture, generous size, and subtle woven edge.";
        background = "#1F2229"; panel = "#30343D"; accent = "#B7AD91"
    },
    @{
        title = "Steel Water Bottle"; sku = "DESK-STEEL-WATER-BOTTLE"; categorySlug = "desk-essentials"; price = 29.00; stock = 55; shape = "object";
        description = "Double-wall stainless steel bottle with a 600 ml capacity, leak-proof cap, and satin finish.";
        background = "#20242C"; panel = "#2C313B"; accent = "#9099A8"
    },
    @{
        title = "Minimal Wall Clock"; sku = "HOME-MINIMAL-WALL-CLOCK"; categorySlug = "home-goods"; price = 46.00; stock = 23; shape = "object";
        description = "Quiet wall clock with a slim black frame, off-white face, and non-ticking movement.";
        background = "#1D2027"; panel = "#2B3039"; accent = "#C7786F"
    }
)

$adminProducts = ConvertTo-Array (Invoke-ShopupuJson -Method "GET" -Path "/api/admin/catalog/products" -Token $adminToken)
$usedDemoProductIds = @{}
$seededProducts = @()
$uploadedImages = @()

foreach ($product in $products) {
    $category = $categoryMap[$product.categorySlug]
    $body = @{
        categoryId = $category.id
        title = $product.title
        description = $product.description
        price = $product.price
        sku = $product.sku
        stock = $product.stock
        enabled = $true
    }

    $saved = Find-BySku -Items $adminProducts -Sku $product.sku
    if ($saved) {
        $saved = Invoke-ShopupuJson -Method "PUT" -Path "/api/admin/catalog/products/$($saved.id)" -Body $body -Token $adminToken
    } else {
        $demoProduct = $adminProducts |
                Where-Object { ($_.sku -like "DEMO-*" -or $_.title -like "Demo *") -and -not $usedDemoProductIds.ContainsKey([string]$_.id) } |
                Select-Object -First 1

        if ($demoProduct) {
            $usedDemoProductIds[[string]$demoProduct.id] = $true
            $saved = Invoke-ShopupuJson -Method "PUT" -Path "/api/admin/catalog/products/$($demoProduct.id)" -Body $body -Token $adminToken
        } else {
            $saved = Invoke-ShopupuJson -Method "POST" -Path "/api/admin/catalog/products" -Body $body -Token $adminToken
        }
        $adminProducts += $saved
    }

    $imageCount = 0
    if ($saved.images) {
        $imageCount = @($saved.images).Count
    }

    if ($imageCount -eq 0) {
        New-Item -ItemType Directory -Force -Path $ImageDir | Out-Null
        $fileName = ($product.sku.ToLowerInvariant() + ".png")
        $imagePath = Join-Path $ImageDir $fileName
        Draw-ProductImage -Product ([pscustomobject]@{
            title = $product.title
            categoryName = $category.name
            shape = $product.shape
            background = $product.background
            panel = $product.panel
            accent = $product.accent
        }) -Path $imagePath

        $uploadedImages += Upload-ProductImage -ProductId $saved.id -FilePath $imagePath -AltText $product.title -Token $adminToken
    }

    $seededProducts += $saved
}

[ordered]@{
    baseUrl = $BaseUrl
    adminEmail = $AdminEmail
    categories = $categoryMap.Values | Sort-Object id
    products = $seededProducts | Sort-Object id
    uploadedImages = $uploadedImages
} | ConvertTo-Json -Depth 20
