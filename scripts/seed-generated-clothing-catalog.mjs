#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const baseUrl = (process.env.SHOPUPU_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const adminEmail = process.env.SHOPUPU_ADMIN_EMAIL ?? "catalog.admin@shopupu.local";
const adminPassword = process.env.SHOPUPU_ADMIN_PASSWORD ?? "ShopupuCatalogAdmin2026!";
const imageDir = process.env.SHOPUPU_IMAGE_DIR
  ? resolve(process.env.SHOPUPU_IMAGE_DIR)
  : join(scriptDir, "generated-catalog-images");
const replaceImages = process.env.SHOPUPU_REPLACE_IMAGES === "true";

const categories = [
  ["hoodies", "Hoodies", "Soft everyday hoodies and sweatshirts."],
  ["shirts-tops", "Shirts & Tops", "Clean shirts, tanks, and easy upper layers."],
  ["knitwear", "Knitwear", "Sweaters and cardigans with natural texture."],
  ["outerwear", "Outerwear", "Jackets, coats, and weather-ready layers."],
  ["bottoms", "Bottoms", "Jeans, trousers, cargo pants, and skirts."],
  ["dresses-skirts", "Dresses & Skirts", "Dresses and polished skirt silhouettes."],
  ["shoes", "Shoes", "Minimal footwear for everyday wear."],
  ["accessories", "Accessories", "Beanies, scarves, and small finishing pieces."]
];

const existingProductImages = [
  { slug: "oversized-cotton-hoodie", title: "Oversized Cotton Hoodie" },
  { slug: "zip-hoodie-classic", title: "Zip Hoodie Classic" }
];

const products = [
  {
    title: "Alpine Cotton Hoodie",
    slug: "alpine-cotton-hoodie",
    categorySlug: "hoodies",
    brandName: "NorthPeak",
    gender: "UNISEX",
    season: "All-season",
    material: "Organic cotton fleece",
    careInstructions: "Machine wash cold with similar colors. Tumble dry low.",
    description: "A soft pullover hoodie in midweight cotton fleece with a relaxed fit, ribbed trims, and a roomy kangaroo pocket.",
    price: "74.00",
    variants: sizeVariants("ALP-HDY", ["S", "M", "L"], "Alpine Green", "74.00", [18, 24, 20])
  },
  {
    title: "Linen Resort Shirt",
    slug: "linen-resort-shirt",
    categorySlug: "shirts-tops",
    brandName: "Harbor Loom",
    gender: "MEN",
    season: "Summer",
    material: "Washed linen",
    careInstructions: "Machine wash cold. Hang dry for a natural linen texture.",
    description: "A relaxed short-sleeve linen shirt with an open camp collar, breathable texture, and an easy straight hem.",
    price: "68.00",
    variants: sizeVariants("LIN-RES", ["S", "M", "L"], "Ivory", "68.00", [14, 19, 16])
  },
  {
    title: "Urban Ribbed Tank",
    slug: "urban-ribbed-tank",
    categorySlug: "shirts-tops",
    brandName: "Shopupu Basics",
    gender: "WOMEN",
    season: "Summer",
    material: "Ribbed cotton jersey",
    careInstructions: "Machine wash cold. Reshape while damp.",
    description: "A minimal ribbed tank with a clean scoop neck, soft stretch, and a close but comfortable fit.",
    price: "29.00",
    variants: sizeVariants("URB-TNK", ["XS", "S", "M", "L"], "Black", "29.00", [18, 22, 24, 16])
  },
  {
    title: "Merino Crewneck Sweater",
    slug: "merino-crewneck-sweater",
    categorySlug: "knitwear",
    brandName: "Mellow Wool",
    gender: "UNISEX",
    season: "Winter",
    material: "Fine merino wool",
    careInstructions: "Hand wash cold or dry clean. Dry flat.",
    description: "A fine-gauge merino sweater with a smooth handfeel, ribbed trims, and a clean crew neckline.",
    price: "96.00",
    variants: sizeVariants("MER-CRW", ["S", "M", "L", "XL"], "Heather Gray", "96.00", [10, 15, 14, 9])
  },
  {
    title: "Cropped Denim Jacket",
    slug: "cropped-denim-jacket",
    categorySlug: "outerwear",
    brandName: "Blue Yard",
    gender: "WOMEN",
    season: "Spring",
    material: "Cotton denim",
    careInstructions: "Machine wash inside out. Line dry.",
    description: "A cropped denim jacket with a structured collar, chest pockets, and a medium blue wash.",
    price: "112.00",
    variants: sizeVariants("CRP-DNM", ["XS", "S", "M", "L"], "Medium Blue", "112.00", [8, 12, 14, 10])
  },
  {
    title: "Tailored Wool Blazer",
    slug: "tailored-wool-blazer",
    categorySlug: "outerwear",
    brandName: "Atelier North",
    gender: "MEN",
    season: "All-season",
    material: "Wool blend",
    careInstructions: "Dry clean only.",
    description: "A sharp wool-blend blazer with notched lapels, clean shoulders, and a restrained two-button front.",
    price: "184.00",
    oldPrice: "219.00",
    variants: sizeVariants("TWL-BLZ", ["S", "M", "L", "XL"], "Charcoal", "184.00", [7, 11, 10, 6], "219.00")
  },
  {
    title: "Lightweight Trench Coat",
    slug: "lightweight-trench-coat",
    categorySlug: "outerwear",
    brandName: "Rainline",
    gender: "WOMEN",
    season: "Spring",
    material: "Cotton twill",
    careInstructions: "Machine wash gentle. Hang dry.",
    description: "A light trench coat with a double-breasted front, soft belt, storm flap, and clean beige twill.",
    price: "158.00",
    variants: sizeVariants("TRN-LGT", ["XS", "S", "M", "L"], "Beige", "158.00", [7, 10, 10, 6])
  },
  {
    title: "Quilted Puffer Vest",
    slug: "quilted-puffer-vest",
    categorySlug: "outerwear",
    brandName: "NorthPeak",
    gender: "UNISEX",
    season: "Autumn",
    material: "Recycled nylon shell",
    careInstructions: "Machine wash cold. Tumble dry low with dryer balls.",
    description: "A lightly padded quilted vest with a stand collar, zip front, and easy layering weight.",
    price: "88.00",
    variants: sizeVariants("QPV-VST", ["S", "M", "L", "XL"], "Sage", "88.00", [11, 18, 16, 9])
  },
  {
    title: "Straight Leg Blue Jeans",
    slug: "straight-leg-blue-jeans",
    categorySlug: "bottoms",
    brandName: "Blue Yard",
    gender: "UNISEX",
    season: "All-season",
    material: "Cotton denim",
    careInstructions: "Machine wash inside out. Line dry.",
    description: "Straight leg jeans in a medium blue wash with classic five-pocket construction and a clean everyday fit.",
    price: "89.00",
    variants: sizeVariants("STJ-BLU", ["30", "32", "34", "36"], "Medium Blue", "89.00", [12, 18, 16, 8])
  },
  {
    title: "Pleated Wide Trousers",
    slug: "pleated-wide-trousers",
    categorySlug: "bottoms",
    brandName: "Atelier North",
    gender: "WOMEN",
    season: "All-season",
    material: "Wool blend",
    careInstructions: "Dry clean or machine wash cold on delicate. Hang dry.",
    description: "Wide-leg trousers with soft front pleats, a clean waistband, and a polished taupe drape.",
    price: "104.00",
    variants: sizeVariants("PLT-WID", ["XS", "S", "M", "L"], "Taupe", "104.00", [8, 13, 13, 7])
  },
  {
    title: "Relaxed Cargo Pants",
    slug: "relaxed-cargo-pants",
    categorySlug: "bottoms",
    brandName: "Field Form",
    gender: "MEN",
    season: "All-season",
    material: "Cotton ripstop",
    careInstructions: "Machine wash cold. Tumble dry low.",
    description: "Relaxed cargo pants in olive cotton ripstop with roomy utility pockets and an adjustable drawstring waist.",
    price: "82.00",
    variants: sizeVariants("RCP-OLV", ["S", "M", "L", "XL"], "Olive", "82.00", [10, 18, 16, 9])
  },
  {
    title: "Satin Midi Skirt",
    slug: "satin-midi-skirt",
    categorySlug: "dresses-skirts",
    brandName: "Luma Studio",
    gender: "WOMEN",
    season: "All-season",
    material: "Satin viscose blend",
    careInstructions: "Hand wash cold. Hang dry.",
    description: "A smooth satin midi skirt with a soft A-line shape, subtle sheen, and clean high waist.",
    price: "72.00",
    variants: sizeVariants("SAT-MID", ["XS", "S", "M", "L"], "Black", "72.00", [9, 14, 13, 8])
  },
  {
    title: "Wrap Jersey Dress",
    slug: "wrap-jersey-dress",
    categorySlug: "dresses-skirts",
    brandName: "Luma Studio",
    gender: "WOMEN",
    season: "All-season",
    material: "Soft jersey knit",
    careInstructions: "Machine wash cold. Lay flat to dry.",
    description: "A deep burgundy wrap dress with a soft V-neck, waist tie, long sleeves, and an easy midi length.",
    price: "118.00",
    variants: sizeVariants("WRP-JRS", ["XS", "S", "M", "L"], "Burgundy", "118.00", [7, 12, 12, 7])
  },
  {
    title: "Knit Polo Cardigan",
    slug: "knit-polo-cardigan",
    categorySlug: "knitwear",
    brandName: "Mellow Wool",
    gender: "MEN",
    season: "Autumn",
    material: "Cotton-wool knit",
    careInstructions: "Hand wash cold. Dry flat.",
    description: "A navy knit cardigan with a polo collar, button front, and softly ribbed trims.",
    price: "98.00",
    variants: sizeVariants("KPC-NVY", ["S", "M", "L", "XL"], "Navy", "98.00", [8, 15, 14, 8])
  },
  {
    title: "Oversized Oxford Shirt",
    slug: "oversized-oxford-shirt",
    categorySlug: "shirts-tops",
    brandName: "Harbor Loom",
    gender: "UNISEX",
    season: "All-season",
    material: "Cotton oxford",
    careInstructions: "Machine wash cold. Warm iron if needed.",
    description: "An oversized pale blue oxford shirt with a crisp collar, curved hem, and easy everyday structure.",
    price: "76.00",
    variants: sizeVariants("OXF-OS", ["XS", "S", "M", "L", "XL"], "Pale Blue", "76.00", [8, 13, 16, 12, 7])
  },
  {
    title: "Technical Rain Jacket",
    slug: "technical-rain-jacket",
    categorySlug: "outerwear",
    brandName: "Rainline",
    gender: "UNISEX",
    season: "Rain",
    material: "Waterproof recycled shell",
    careInstructions: "Machine wash cold. Do not use fabric softener.",
    description: "A lightweight rain jacket with a hood, sealed zipper, adjustable cuffs, and a matte waterproof shell.",
    price: "132.00",
    oldPrice: "159.00",
    variants: sizeVariants("RNJ-TEC", ["S", "M", "L", "XL"], "Slate Teal", "132.00", [9, 16, 14, 8], "159.00")
  },
  {
    title: "Brushed Flannel Overshirt",
    slug: "brushed-flannel-overshirt",
    categorySlug: "shirts-tops",
    brandName: "Field Form",
    gender: "MEN",
    season: "Autumn",
    material: "Brushed cotton flannel",
    careInstructions: "Machine wash cold. Tumble dry low.",
    description: "A relaxed brushed flannel overshirt with chest pockets, soft plaid texture, and an easy layering fit.",
    price: "86.00",
    variants: sizeVariants("FLN-OVR", ["S", "M", "L", "XL"], "Cream Plaid", "86.00", [10, 16, 15, 8])
  },
  {
    title: "Minimal White Sneakers",
    slug: "minimal-white-sneakers",
    categorySlug: "shoes",
    brandName: "Plainstep",
    gender: "UNISEX",
    season: "All-season",
    material: "Leather upper, rubber sole",
    careInstructions: "Wipe clean with a damp cloth. Air dry.",
    description: "Minimal low-top sneakers in smooth white leather with clean laces, simple rubber soles, and no visible branding.",
    price: "124.00",
    variants: sizeVariants("MWS-WHT", ["40", "41", "42", "43"], "White", "124.00", [8, 10, 10, 7])
  },
  {
    title: "Wool Beanie",
    slug: "wool-beanie",
    categorySlug: "accessories",
    brandName: "Mellow Wool",
    gender: "UNISEX",
    season: "Winter",
    material: "Rib-knit wool",
    careInstructions: "Hand wash cold. Dry flat.",
    description: "A folded rib-knit wool beanie with a soft rounded crown and a warm charcoal finish.",
    price: "34.00",
    variants: sizeVariants("WLB-CHR", ["ONE_SIZE"], "Charcoal", "34.00", [34])
  },
  {
    title: "Cashmere Scarf",
    slug: "cashmere-scarf",
    categorySlug: "accessories",
    brandName: "Mellow Wool",
    gender: "UNISEX",
    season: "Winter",
    material: "Cashmere",
    careInstructions: "Dry clean or hand wash cold. Dry flat.",
    description: "A soft camel cashmere scarf with a subtle woven texture, clean fringe, and generous drape.",
    price: "118.00",
    variants: sizeVariants("CSH-SCF", ["ONE_SIZE"], "Camel", "118.00", [22])
  }
];

function sizeVariants(prefix, sizes, color, price, stocks, oldPrice = null) {
  return sizes.map((size, index) => ({
    sku: `${prefix}-${size.replaceAll("_", "")}`,
    size,
    color,
    price,
    oldPrice,
    stock: stocks[index] ?? stocks.at(-1) ?? 0,
    enabled: true
  }));
}

async function main() {
  const token = await login();
  const categoryBySlug = await ensureCategories(token);
  const adminProducts = await getAllAdminProducts(token);

  const productsBySlug = new Map(adminProducts.map((product) => [product.slug, product]));
  const imageTargets = [];

  for (const product of products) {
    const category = categoryBySlug.get(product.categorySlug);
    if (!category) {
      throw new Error(`Missing category ${product.categorySlug}`);
    }

    const body = productBody(product, category.id);
    const existing = productsBySlug.get(product.slug);
    let saved = existing
      ? await apiJson(`/api/v1/admin/catalog/products/${existing.id}`, "PUT", body, token)
      : await apiJson("/api/v1/admin/catalog/products", "POST", body, token);

    saved = await ensureVariants(token, saved, product.variants);
    productsBySlug.set(saved.slug, saved);
    imageTargets.push({ slug: product.slug, title: product.title, productId: saved.id });
  }

  for (const target of existingProductImages) {
    const product = productsBySlug.get(target.slug) ?? (await findAdminProductBySlug(token, target.slug));
    if (!product) {
      console.warn(`Existing product not found, skipping image: ${target.slug}`);
      continue;
    }
    imageTargets.push({ slug: target.slug, title: product.title ?? target.title, productId: product.id });
  }

  const uploadedImages = [];
  for (const target of imageTargets) {
    const latest = await getAdminProduct(token, target.productId);
    if (latest.images?.length && !replaceImages) {
      console.log(`image: skip ${target.slug} (${latest.images.length} already present)`);
      continue;
    }

    const uploaded = await uploadImage(token, target.productId, target.slug, target.title);
    uploadedImages.push(uploaded);
    if (replaceImages && latest.images?.length) {
      for (const image of latest.images) {
        if (image.id !== uploaded.id) {
          await apiFetch(`/api/v1/admin/catalog/products/${target.productId}/images/${image.id}`, {
            method: "DELETE",
            token
          });
        }
      }
    }
  }

  const finalProducts = await getAllAdminProducts(token);
  const activeTargets = finalProducts.filter((product) => product.enabled && !product.deletedAt);
  const withoutImages = activeTargets.filter((product) => !product.images?.length);

  console.log(JSON.stringify({
    baseUrl,
    imageDir,
    createdOrUpdatedProducts: products.length,
    imageTargets: imageTargets.length,
    uploadedImages: uploadedImages.length,
    activeProducts: activeTargets.length,
    productsWithoutImages: withoutImages.map((product) => ({ id: product.id, slug: product.slug, title: product.title }))
  }, null, 2));
}

function productBody(product, categoryId) {
  return {
    categoryId,
    title: product.title,
    slug: product.slug,
    description: product.description,
    price: product.price,
    oldPrice: product.oldPrice ?? null,
    brandName: product.brandName,
    gender: product.gender,
    season: product.season,
    material: product.material,
    careInstructions: product.careInstructions,
    metaTitle: `${product.title} | shopupu`,
    metaDescription: product.description,
    enabled: true
  };
}

async function login() {
  const response = await apiJson("/api/v1/auth/login", "POST", {
    email: adminEmail,
    password: adminPassword
  }, null, false);
  return response.accessToken;
}

async function ensureCategories(token) {
  const existing = await apiFetch("/api/v1/catalog/categories", { token: null });
  const bySlug = new Map(existing.map((category) => [category.slug, category]));

  for (const [slug, name, description] of categories) {
    if (bySlug.has(slug)) {
      continue;
    }
    const created = await apiJson("/api/v1/admin/catalog/categories", "POST", {
      name,
      slug,
      description,
      parentId: null
    }, token);
    bySlug.set(created.slug, created);
  }

  return bySlug;
}

async function ensureVariants(token, product, variants) {
  let current = await getAdminProduct(token, product.id);
  const bySku = new Map((current.variants ?? []).map((variant) => [variant.sku, variant]));

  for (const variant of variants) {
    const existing = bySku.get(variant.sku);
    if (existing) {
      await apiJson(`/api/v1/admin/catalog/variants/${existing.id}`, "PUT", variant, token);
    } else {
      await apiJson(`/api/v1/admin/catalog/products/${product.id}/variants`, "POST", variant, token);
    }
  }

  return getAdminProduct(token, product.id);
}

async function findAdminProductBySlug(token, slug) {
  const all = await getAllAdminProducts(token);
  return all.find((product) => product.slug === slug) ?? null;
}

async function getAdminProduct(token, id) {
  return apiFetch(`/api/v1/admin/catalog/products/${id}`, { token });
}

async function getAllAdminProducts(token) {
  const result = [];
  let page = 0;

  while (true) {
    const data = await apiFetch(`/api/v1/admin/catalog/products?page=${page}&size=100`, { token });
    const content = Array.isArray(data.content) ? data.content : [];
    result.push(...content);
    if (data.last || content.length === 0) {
      return result;
    }
    page += 1;
  }
}

async function uploadImage(token, productId, slug, title) {
  const filePath = join(imageDir, `${slug}.png`);
  const bytes = await readFile(filePath);
  const form = new FormData();
  form.set("file", new File([bytes], basename(filePath), { type: "image/png" }));
  form.set("altText", title);
  form.set("sortOrder", "0");

  const uploaded = await apiFetch(`/api/v1/admin/catalog/products/${productId}/images`, {
    method: "POST",
    token,
    body: form
  });
  console.log(`image: uploaded ${slug} -> ${uploaded.url}`);
  return uploaded;
}

async function apiJson(path, method, body, token, auth = true) {
  return apiFetch(path, {
    method,
    token: auth ? token : null,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
}

async function apiFetch(path, options = {}) {
  const headers = new Headers(options.headers);
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body
  });
  const text = await response.text();
  const data = text ? parseJson(text) : null;

  if (!response.ok) {
    throw new Error(`${options.method ?? "GET"} ${path} failed with ${response.status}: ${JSON.stringify(data)}`);
  }

  return data;
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
