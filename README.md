# Shopupu Backend

Shopupu is a modular Spring Boot REST API for an online shop. The current backend covers authentication, identity, catalog, cart, checkout orders, shipping, and a stub payment flow prepared for a future custom HTTP payment service.

## Tech Stack

- Java 25
- Spring Boot 4.0.0-M3
- Spring Security with JWT
- Spring Data JPA and Hibernate
- PostgreSQL
- Flyway
- Springdoc OpenAPI / Swagger UI
- JUnit 5, Mockito, Testcontainers

## Local Run

The application reads runtime secrets from environment variables. Do not store real passwords in `application.yml`.

Required local variables:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/shopupu"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_postgres_password"
```

Recommended optional variables:

```powershell
$env:JWT_SECRET="use_a_long_random_secret_at_least_32_bytes"
$env:BOOTSTRAP_ADMIN_ENABLED="true"
$env:BOOTSTRAP_ADMIN_EMAIL="dmytro.bolibok@gmail.com"
$env:BOOTSTRAP_ADMIN_PASSWORD="use_a_strong_admin_password"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173,http://localhost:3000"
```

Run:

```powershell
.\mvnw.cmd spring-boot:run
```

Test:

```powershell
.\mvnw.cmd test
```

Swagger UI:

```text
http://localhost:8080/swagger
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## IntelliJ Setup

A local IntelliJ run configuration exists at:

```text
.idea/runConfigurations/ShopupuApplication.xml
```

It sets:

```text
DB_URL=jdbc:postgresql://localhost:5432/shopupu
DB_USERNAME=postgres
DB_PASSWORD=<shopupu_db_password>
```

The `.idea` directory is ignored by Git in this project, so this is a local development setting. If IntelliJ does not pick it up automatically, open `Run | Edit Configurations`, choose `ShopupuApplication`, and add the same environment variables manually.

## Database

Flyway is the only source of database schema. Hibernate is configured with:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

Migrations:

- `V1__identity.sql`: users, roles, user_roles, refresh_tokens, role seed data.
- `V2__catalog.sql`: categories, products, product_images.
- `V3__cart.sql`: carts, cart_items.
- `V4__orders.sql`: orders, order_items.
- `V5__payments.sql`: payments.
- `V6__payment_events.sql`: payment_events.
- `V7__shipping.sql`: shipping_addresses, shipments.

For a clean local reset, connect to the `postgres` maintenance database and run:

```sql
DROP DATABASE shopupu WITH (FORCE);
CREATE DATABASE shopupu;
```

Then start the application and Flyway will apply all migrations.

## Architecture

Main modules:

- `auth`: login, registration, JWT access tokens, refresh tokens.
- `identity`: users, roles, admin user listing.
- `catalog`: public product/category queries and admin catalog mutations.
- `cart`: authenticated shopping cart.
- `orders`: checkout, owner order access, admin order management.
- `shipping`: shipping address, method, status, tracking.
- `payments`: local payment attempts, stub gateway, callback skeleton.
- `common`: exceptions, global error handling, access control helpers.
- `config`: Spring Security, CORS, OpenAPI, payment/shipping/bootstrap configuration.

Each business module is organized around controllers, services, repositories, entities, DTOs, and mappers where applicable.

## Authentication

Most protected endpoints require:

```http
Authorization: Bearer <accessToken>
```

Admin endpoints require a user with `ROLE_ADMIN`.

Public endpoints:

- `/api/auth/register`
- `/api/auth/login`
- `/api/auth/refresh`
- `/api/catalog/**`
- `/api/payments/callback`
- `/swagger`
- `/swagger-ui/**`
- `/v3/api-docs/**`

## Error Format

Errors are returned as Spring `ProblemDetail` responses.

Example:

```json
{
  "type": "urn:shopupu:error:validation-failed",
  "title": "bad request",
  "status": 400,
  "detail": "validation failed",
  "instance": "/api/auth/register",
  "code": "VALIDATION_FAILED",
  "errors": [
    {
      "field": "email",
      "message": "must be a well-formed email address"
    }
  ]
}
```

Common statuses:

- `400`: invalid request or malformed input.
- `401`: missing or invalid authentication.
- `403`: authenticated user has no access.
- `404`: resource was not found.
- `409`: unique constraint or domain conflict.
- `422`: business rule violation.

## Domain Statuses

Order statuses:

```text
NEW, PAID, SHIPPED, COMPLETED, CANCELED
```

Payment statuses:

```text
CREATED, PENDING, SUCCEEDED, FAILED, CANCELED, EXPIRED, REFUNDED
```

Shipping methods:

```text
DHL, LOCAL_PICKUP, STANDARD_POST
```

Shipping statuses:

```text
PENDING, PREPARING, SHIPPED, DELIVERED, READY_FOR_PICKUP, PICKED_UP, CANCELED
```

## API Reference

### Auth

#### POST `/api/auth/register`

Registers a new customer and returns JWT tokens.

Access: public.

Request:

```json
{
  "email": "customer@example.com",
  "password": "password123"
}
```

Validation:

- `email`: required, valid email.
- `password`: required, 8-128 characters.

Response `TokenPairResponse`:

```json
{
  "accessToken": "jwt",
  "refreshToken": "refresh-token"
}
```

#### POST `/api/auth/login`

Authenticates an existing user and returns JWT tokens.

Access: public.

Request:

```json
{
  "email": "customer@example.com",
  "password": "password123"
}
```

Response `TokenPairResponse`:

```json
{
  "accessToken": "jwt",
  "refreshToken": "refresh-token"
}
```

#### POST `/api/auth/refresh`

Rotates refresh token and returns a new token pair.

Access: public.

Request:

```json
{
  "refreshToken": "refresh-token"
}
```

Response `TokenPairResponse`:

```json
{
  "accessToken": "new-jwt",
  "refreshToken": "new-refresh-token"
}
```

#### GET `/api/auth/me`

Returns the current authenticated user.

Access: authenticated.

Response `UserProfile`:

```json
{
  "id": 1,
  "email": "customer@example.com",
  "enabled": true
}
```

### Catalog Public API

#### GET `/api/catalog/categories`

Returns all categories.

Access: public.

Response `CategoryResponse[]`:

```json
[
  {
    "id": 1,
    "name": "Shoes",
    "slug": "shoes",
    "description": "Running and casual shoes",
    "parentId": null
  }
]
```

#### GET `/api/catalog/categories/{slug}`

Returns one category by slug.

Access: public.

Response `CategoryResponse`:

```json
{
  "id": 1,
  "name": "Shoes",
  "slug": "shoes",
  "description": "Running and casual shoes",
  "parentId": null
}
```

#### GET `/api/catalog/products`

Returns enabled products.

Access: public.

Response `ProductResponse[]`:

```json
[
  {
    "id": 10,
    "title": "Product title",
    "description": "Product description",
    "price": 49.99,
    "sku": "SKU-001",
    "stock": 15,
    "enabled": true,
    "createdAt": "2026-05-03T20:00:00Z",
    "categoryId": 1,
    "categoryName": "Shoes",
    "categorySlug": "shoes",
    "images": [
      {
        "id": 100,
        "url": "https://example.com/image.jpg",
        "altText": "Product image",
        "position": 0
      }
    ]
  }
]
```

#### GET `/api/catalog/categories/{slug}/products`

Returns enabled products in a category.

Access: public.

Response: same as `ProductResponse[]`.

#### GET `/api/catalog/products/search`

Searches products using filters and Spring pageable query parameters.

Access: public.

Query parameters:

- `q`: optional text search.
- `categoryId`: optional category id.
- `minPrice`: optional minimum price.
- `maxPrice`: optional maximum price.
- `enabled`: optional boolean, defaults to `true`.
- `page`: page index, defaults to Spring pageable default.
- `size`: page size.
- `sort`: sort expression, for example `price,asc`.

Response `Page<ProductListItem>`:

```json
{
  "content": [
    {
      "id": 10,
      "title": "Product title",
      "price": 49.99,
      "enabled": true,
      "createdAt": "2026-05-03T20:00:00Z",
      "categoryId": 1,
      "categorySlug": "shoes"
    }
  ],
  "pageable": {},
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "size": 20,
  "number": 0
}
```

### Catalog Admin API

All endpoints in this section require `ROLE_ADMIN`.

#### POST `/api/admin/catalog/categories`

Creates a category.

Request `CategoryRequest`:

```json
{
  "name": "Shoes",
  "slug": "shoes",
  "description": "Running and casual shoes",
  "parentId": null
}
```

Response: `CategoryResponse`.

#### PUT `/api/admin/catalog/categories/{id}`

Updates a category.

Request: `CategoryRequest`.

Response: `CategoryResponse`.

#### DELETE `/api/admin/catalog/categories/{id}`

Deletes a category.

Response: `204 No Content`.

#### POST `/api/admin/catalog/products`

Creates a product.

Request `ProductRequest`:

```json
{
  "categoryId": 1,
  "title": "Product title",
  "description": "Product description",
  "price": 49.99,
  "sku": "SKU-001",
  "stock": 15,
  "enabled": true
}
```

Response: `ProductResponse`.

#### PUT `/api/admin/catalog/products/{id}`

Updates a product.

Request: `ProductRequest`.

Response: `ProductResponse`.

#### DELETE `/api/admin/catalog/products/{id}`

Deletes a product.

Response: `204 No Content`.

### Cart

All cart endpoints require authentication.

#### GET `/api/cart`

Returns current user's cart.

Response `CartResponse`:

```json
{
  "items": [
    {
      "productId": 10,
      "title": "Product title",
      "price": 49.99,
      "quantity": 2,
      "lineTotal": 99.98
    }
  ],
  "totalItems": 2,
  "subtotal": 99.98
}
```

#### POST `/api/cart/items`

Adds an item or increases its quantity.

Request `AddOrUpdateItemRequest`:

```json
{
  "productId": 10,
  "quantity": 2
}
```

Response: `CartResponse`.

#### PUT `/api/cart/items/{productId}`

Sets quantity for an existing cart item. Quantity `0` removes the item.

Request:

```json
{
  "productId": 10,
  "quantity": 3
}
```

Response: `CartResponse`.

#### DELETE `/api/cart/items/{productId}`

Removes one product from the cart.

Response: `CartResponse`.

#### DELETE `/api/cart`

Clears the cart.

Response: `CartResponse`.

### Orders

User order endpoints require authentication and operate on the current user's orders.

#### POST `/api/orders/checkout`

Creates an order from the current cart and immediately decreases product stock.

Response `OrderDto`:

```json
{
  "id": 1,
  "subtotalAmount": 99.98,
  "shippingAmount": 0.00,
  "paymentAmount": 99.98,
  "status": "NEW",
  "createdAt": "2026-05-03T20:00:00Z",
  "updatedAt": "2026-05-03T20:00:00Z",
  "items": [
    {
      "id": 1,
      "productId": 10,
      "title": "Product title",
      "price": 49.99,
      "quantity": 2,
      "lineTotal": 99.98
    }
  ]
}
```

#### GET `/api/orders`

Returns current user's orders.

Query parameters:

- `status`: optional order status filter.

Response: `OrderDto[]`.

#### GET `/api/orders/{id}`

Returns one current-user order.

Response: `OrderDto`.

#### PATCH `/api/orders/{id}/cancel`

Cancels a current-user order when the current status allows it.

Response: `OrderDto`.

### Orders Admin API

All endpoints in this section require `ROLE_ADMIN`.

#### GET `/api/admin/orders`

Returns all orders.

Response: `OrderDto[]`.

#### GET `/api/admin/orders/{id}`

Returns any order by id.

Response: `OrderDto`.

#### PATCH `/api/admin/orders/{id}/status`

Updates order status through the order state rules.

Query parameters:

- `status`: target `OrderStatus`.

Example:

```text
PATCH /api/admin/orders/1/status?status=PAID
```

Response: `OrderDto`.

### Shipping

Shipping user endpoints require authentication and owner/admin access to the order.

#### POST `/api/shipping/address`

Sets or replaces shipping address for an order after checkout and before payment.

Request `SetShippingAddressRequest`:

```json
{
  "orderId": 1,
  "fullName": "John Customer",
  "line1": "Main Street 1",
  "line2": "Apartment 2",
  "city": "Berlin",
  "state": "Berlin",
  "postalCode": "10115",
  "country": "Germany"
}
```

Response `ShipmentDto`:

```json
{
  "orderId": 1,
  "method": "DHL",
  "shippingStatus": "PENDING",
  "orderStatus": "NEW",
  "shippingCost": 9.99,
  "currency": "EUR",
  "trackingNumber": null,
  "address": {
    "id": 1,
    "fullName": "John Customer",
    "line1": "Main Street 1",
    "line2": "Apartment 2",
    "city": "Berlin",
    "state": "Berlin",
    "postalCode": "10115",
    "country": "Germany"
  },
  "createdAt": "2026-05-03T20:00:00Z",
  "updatedAt": "2026-05-03T20:00:00Z"
}
```

#### POST `/api/shipping/method`

Sets shipping method and recalculates order shipping/payment amounts.

Request `SetShippingMethodRequest`:

```json
{
  "orderId": 1,
  "method": "DHL"
}
```

Response: `ShipmentDto`.

#### GET `/api/shipping/{orderId}`

Returns shipment data for an order.

Response: `ShipmentDto`.

### Shipping Admin API

All endpoints in this section require `ROLE_ADMIN`.

#### PATCH `/api/admin/shipping/{orderId}/status`

Updates shipping status and optional tracking number.

Query parameters:

- `status`: target `ShippingStatus`.
- `trackingNumber`: optional tracking number.

Example:

```text
PATCH /api/admin/shipping/1/status?status=SHIPPED&trackingNumber=TRACK-123
```

Response: `ShipmentDto`.

### Payments

Payment creation requires authentication and owner/admin access to the order. Callback is public but checked by the current verifier stub.

#### POST `/api/payments`

Creates a local payment attempt and calls the stub gateway.

Request `CreatePaymentRequest`:

```json
{
  "orderId": 1
}
```

Response `PaymentResponse`:

```json
{
  "id": 1,
  "orderId": 1,
  "externalPaymentId": "stub-payment-id",
  "provider": "stub",
  "status": "PENDING",
  "amount": 109.97,
  "currency": "EUR",
  "paymentUrl": "https://payments.example.test/stub-payment-id",
  "clientToken": "stub-client-token",
  "createdAt": "2026-05-03T20:00:00Z",
  "updatedAt": "2026-05-03T20:00:00Z"
}
```

#### POST `/api/payments/create`

Legacy compatibility endpoint for payment creation.

Query parameters:

- `orderId`: order id.

Response: `PaymentResponse`.

#### GET `/api/payments/{id}`

Returns payment by local payment id.

Response: `PaymentResponse`.

#### POST `/api/payments/callback`

Technical callback endpoint prepared for a future custom payment service.

Access: public endpoint, protected by `PaymentCallbackVerifier`.

Headers:

```http
X-Payment-Signature: signature-or-stub-value
```

Request `PaymentCallbackRequest`:

```json
{
  "externalEventId": "event-1",
  "externalPaymentId": "external-payment-id",
  "status": "SUCCEEDED",
  "details": "provider details"
}
```

Response:

```text
Payment callback received
```

### Users Admin API

All endpoints in this section require `ROLE_ADMIN`.

#### GET `/api/admin/users`

Returns all users.

Response `UserProfile[]`:

```json
[
  {
    "id": 1,
    "email": "customer@example.com",
    "enabled": true
  }
]
```

## Typical Business Flow

1. Register or login through `/api/auth/register` or `/api/auth/login`.
2. Use the access token as `Authorization: Bearer <token>`.
3. Browse catalog through `/api/catalog/**`.
4. Add products to `/api/cart/items`.
5. Create order through `/api/orders/checkout`.
6. Set shipping address and method through `/api/shipping/address` and `/api/shipping/method`.
7. Create payment through `/api/payments`.
8. The current payment gateway is a stub. Real custom HTTP payment integration is intentionally not implemented yet.

## Current Limitations

- Payment integration defaults to the local stub unless `PAYMENTS_DEFAULT_PROVIDER=bank_back` is set.
- The `bank_back` provider requires a separately running `Bank_back` service and a configured merchant account.
- Testcontainers-based integration tests require Docker. Without Docker they are skipped.
- PostgreSQL 18 works locally, but the current Flyway version warns that PostgreSQL 18 is newer than the latest tested Flyway support version.

## Bank_back Payment Provider

To use `Bank_back` instead of the stub provider, run `Bank_back` separately and set:

```powershell
$env:PAYMENTS_DEFAULT_PROVIDER="bank_back"
$env:PAYMENT_SERVICE_BASE_URL="http://localhost:5231"
$env:PAYMENT_SERVICE_CLIENT_ID="shopupu"
$env:PAYMENT_SERVICE_SECRET="<shared_service_secret>"
$env:PAYMENT_CALLBACK_SECRET="<shared_callback_secret>"
$env:PAYMENT_CALLBACK_URL="http://localhost:8080/api/payments/callback"
```

The current `application.yml` property is `payments.default-provider`, so in IntelliJ use:

```text
PAYMENTS_DEFAULT_PROVIDER=bank_back;PAYMENT_SERVICE_BASE_URL=http://localhost:5231;PAYMENT_SERVICE_CLIENT_ID=shopupu;PAYMENT_SERVICE_SECRET=<shared_service_secret>;PAYMENT_CALLBACK_SECRET=<shared_callback_secret>;PAYMENT_CALLBACK_URL=http://localhost:8080/api/payments/callback
```

Manual flow:

1. Checkout in `shopupu`.
2. Create payment in `shopupu`.
3. Open returned `paymentUrl` (`bankfront://pay?...`) or copy `externalPaymentId`.
4. Login in `Bank_Front_WPF` as a bank user.
5. Confirm payment from one of the user's bank accounts.
6. `Bank_back` sends a signed callback and `shopupu` marks the order as `PAID`.
