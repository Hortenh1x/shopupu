# План рефакторинга shopupu → production-ready магазин одежды

Дата аудита: 2026-07-05. Аудит проведён по чек-листу требований (27 разделов, 208 пунктов).

**Итог аудита: есть — 20, частично — 73, нет — 115.**

---

## 1. Сводка аудита по разделам

| Раздел | Есть | Частично | Нет | Главные пробелы |
|---|---|---|---|---|
| 0. ARCH | 5 | 3 | 2 | нет MapStruct, нет статанализа, HTTP-вызов платёжки внутри транзакции |
| 1. AUTH | 1 | 4 | 7 | нет logout/верификации email/сброса пароля/брутфорс-защиты; JWT-секрет с дефолтом в git; BCrypt cost 10 |
| 2. AUTHZ | 1 | 3 | 2 | **permit-by-default** (`anyRequest().permitAll()`); нет роли MANAGER; нет audit trail |
| 3. SEC | 4 | 8 | 6 | **stub-верификатор платёжных callback'ов принимает всё** → любой может пометить заказ PAID; нет rate limiting; загрузка файлов без magic-bytes; Spring Boot 4.0.0-M3 (milestone, не GA) |
| 4. DB | 1 | 6 | 8 | **нет product_variant и inventory**; нет @Version; списание остатка — неатомарный read-modify-write (oversell); Double в фильтрах цены |
| 5. VALID | 2 | 3 | 2 | callback обходит Bean Validation; конфиг-properties без @Validated |
| 6. API | 0 | 6 | 3 | нет /api/v1; создание возвращает 200 вместо 201; нет Idempotency-Key; списки без пагинации |
| 7. ERR | 1 | 4 | 1 | нет catch-all обработчика (сырые 500); нет correlation ID |
| 8. CAT | 0 | 4 | 5 | **нет вариантов (размер/цвет), нет атрибутов одежды (бренд/материал/сезон/пол)**, нет slug у товара, нет старой цены |
| 9. INV | 0 | 1 | 5 | **нет Inventory/reserved; отмена заказа не возвращает остаток (утечка склада!); oversell под конкуренцией** |
| 10. CART | 2 | 1 | 2 | нет гостевой корзины и merge при логине |
| 11. ORD | 0 | 4 | 6 | только 5 статусов из 9; нет истории статусов, номера заказа, идемпотентного чекаута, снапшота адреса; отмена не освобождает склад |
| 12. PAY | 0 | 6 | 3 | нет monobank/fondy; нет refund; callback принимает любые переходы статусов; idempotency key — случайный UUID на каждую попытку |
| 13. SHIP | 0 | 3 | 2 | плоские тарифы из конфига; нет адресной книги; нет free-shipping порога |
| 14. PROMO | 0 | 0 | 5 | **промокодов нет вообще** — с нуля |
| 15. REV | 1 | 1 | 3 | **отзыв без покупки возможен**; нет пре-модерации (сразу PUBLISHED); нет санитизации; утечка email через displayName |
| 16. USER | 1 | 0 | 5 | нет профиля/адресной книги/wishlist/GDPR/согласий |
| 17. NOTIF | 0 | 0 | 6 | email-уведомлений нет вообще |
| 18. CACHE | 0 | 0 | 4 | кэша нет вообще |
| 19. ASYNC | 0 | 0 | 6 | ни брокера, ни @Async/@Scheduled, ни outbox |
| 20. OBS | 0 | 1 | 6 | **actuator отсутствует в pom.xml**; 3 log-statement на весь код; нет метрик/трейсинга |
| 21. TEST | 0 | 2 | 7 | нет @WebMvcTest/security-тестов, N+1-тестов, конкурентных тестов, JaCoCo |
| 22. PERF | 0 | 3 | 4 | RestClient без таймаутов на каждый вызов; нет Resilience4j; Hikari по умолчанию |
| 23. CONFIG | 0 | 4 | 1 | нет prod/staging профилей; секреты с рабочими дефолтами |
| 24. DEVOPS | 0 | 1 | 6 | нет Dockerfile, CI, graceful shutdown, probes |
| 25. DOC | 0 | 2 | 3 | OpenAPI без описаний; нет ADR/ER-диаграммы/runbook |
| 26. COMPL | 1 | 0 | 5 | PCI DSS ок (карты не трогаем); GDPR/retention/consent — нет |
| 27. I18N | 1 | 0 | 3 | UTC ок; MessageSource/мультивалютности нет |

Полный пофайловый разбор каждого ID — в выводе аудита (9 агентов, см. историю сессии).

## 2. Критические находки (блокеры прода)

1. **Подделка оплаты**: `payments.default-provider=stub` + пустой `PAYMENT_CALLBACK_SECRET` (дефолт) → `StubPaymentCallbackVerifier.isValid()` возвращает `true` для любого запроса, а `/api/payments/callback` — `permitAll`. Любой аноним помечает любой заказ как PAID.
2. **Permit-by-default**: `SecurityConfig` заканчивается `anyRequest().permitAll()` — каждый новый endpoint автоматически публичный.
3. **Рабочий JWT-секрет в git**: `application.yml` содержит полноценный секрет как fallback → подделка любых токенов, включая админские.
4. **Oversell + утечка склада**: списание `products.stock` — неатомарное чтение-изменение без блокировок; отмена заказа НЕ возвращает остаток; резерва нет.
5. **Нет доменной модели одежды**: без `product_variant` (размер/цвет/SKU) и `inventory` магазин одежды не работает в принципе.
6. **Spring Boot 4.0.0-M3** — milestone без security-патчей.
7. **HTTP-вызов платёжного шлюза внутри открытой DB-транзакции** (`PaymentService.createPayment`), клиент без таймаутов.

## 3. Целевая доменная модель (одежда)

```
Category (иерархия, slug)
Brand (новое)
Product (модель: title, slug, description, brand, gender, season, material, care, meta)
 └── ProductVariant (SKU, size, color, price, oldPrice/compareAt, @Version)
      └── Inventory (stock, reserved, @Version) + InventoryMovement (журнал)
      └── ProductImage (привязка к product + опционально color)
Cart → CartItem(variant_id)
Order (orderNumber, статусы: CREATED→PENDING_PAYMENT→PAID→PROCESSING→SHIPPED→DELIVERED→COMPLETED | CANCELLED | REFUNDED)
 └── OrderItem (снапшот: sku, title, size, color, price, qty) + OrderStatusHistory
 └── снапшот адреса доставки в заказ
Payment (провайдеры: monobank(UAH)/fondy(EUR)/stub(dev), refund)
PromoCode + PromoRedemption (атомарный учёт)
User → UserAddress (адресная книга), Wishlist
Review (PENDING→APPROVED/REJECTED, verified purchase)
```

## 4. Фазы рефакторинга

Статус на 2026-07-06: фазы 0–3 выполнены, фаза 4 выполнена частично.
Тесты: 116, все зелёные (включая интеграционные на Testcontainers).

### Фаза 0 — Фундамент [C/H] ✅
- [x] SEC-17: Spring Boot 4.0.0-M3 → **4.0.7 GA**
- [x] SEC-08/AUTH-04/CONFIG-02: секреты-дефолты удалены из yml (JWT-секрет обязателен вне dev-профиля); `.env.example`; профили dev/prod; JWT `iss`/`aud` выпускаются и проверяются
- [x] VALID-07/CONFIG-03: `@Validated` + constraints на всех `@ConfigurationProperties` (Jwt/Payment/Shipping/Checkout/RateLimit), fail-fast на старте
- [x] OBS-04/DEVOPS-03: actuator (health/liveness/readiness открыты, остальное — ADMIN), graceful shutdown
- [x] DB-03/DB-13: Hikari (pool/timeout/leak detection), batch_size+order_inserts; SEC-11: multipart-лимиты 5MB/25MB; PERF-05: compression; `open-in-view: false`
- [x] ARCH-09: Spotless (verify падает при нарушениях); TEST-07: JaCoCo с порогом в verify

### Фаза 1 — Безопасность [C] ✅
- [x] AUTHZ-02: deny-by-default (`anyRequest().authenticated()`), явный whitelist публичных ручек; JSON 401/403 (Problem Details)
- [x] PAY-03/SEC-18: fail-closed верификация callback — stub теперь **отклоняет всё** при пустом секрете и проверяет HMAC-SHA256; валидация переходов статусов платежа (SUCCEEDED→FAILED отклоняется)
- [x] AUTH-01: BCrypt(12); AUTH-03: refresh-токены хранятся **хэшированными (SHA-256)**, ротация + reuse-detection с отзывом всей цепочки; AUTH-05: `POST /api/auth/logout`
- [x] AUTH-12: `POST /api/auth/change-password` с инвалидацией всех сессий
- [x] SEC-05/AUTH-08: rate limiting (Bucket4j, per-IP) на auth- и checkout/payment-ручках, 429 + `Retry-After`
- [x] SEC-04: CSP, Referrer-Policy, HSTS (nosniff/frame-deny — дефолты Spring Security)
- [x] SEC-12: magic-bytes вместо клиентского Content-Type, расширение выводится из сигнатуры
- [x] ERR-01/ERR-05/SEC-15: catch-all handler (500 без внутренностей), `X-Request-Id` + MDC + `requestId` в Problem Details; 4xx — warn, 5xx — error
- [x] AUTHZ-01: роль MANAGER (миграция V10), разделение прав ADMIN/MANAGER (users — только ADMIN)
- [x] Retention: ежедневная очистка истёкших refresh-токенов (COMPL-02 частично)
- [ ] SEC-16: register по-прежнему отвечает 409 при дубликате email (нужна почтовая инфраструктура для полноценного решения)

### Фаза 2 — Домен одежды [C] ✅
- [x] Миграция V9: brands, product_variants, inventory, inventory_movements, атрибуты одежды, slug, old_price; каждый существующий товар → дефолтный вариант с переносом остатка
- [x] CAT-01/03/08/09: Brand/ProductVariant (SKU уникален), gender/season/material/care, slug + meta, oldPrice
- [x] INV-01/02/03, DB-05/06: Inventory(stock/reserved) + @Version; атомарные UPDATE для reserve/release/commitSale/restock; журнал движений (INV-06); проверено конкурентным интеграционным тестом (oversell невозможен)
- [x] CART: корзина по variant_id; CART-05: количество против available = stock − reserved
- [x] CAT-05: фильтры q/категория/бренд/пол/размер/цвет/цена(BigDecimal, DB-02)/наличие через EXISTS-подзапросы
- [x] Каталог: пагинация всех списков (DB-09/API-03/PERF-03), @BatchSize против N+1, индексы в V9

### Фаза 3 — Заказы, платежи, промо [C] ✅
- [x] ORD-01/05/08: state machine из 9 статусов с валидацией переходов, order_status_history (кто/когда), orderNumber `ORD-YYYYMMDD-XXXXXX`
- [x] ORD-02: снапшот sku/size/color/brand/title/price в order_items + снапшот адреса в shipment
- [x] ORD-03/API-04: идемпотентный checkout по `Idempotency-Key` (уникальный индекс user+key)
- [x] ORD-06/INV-02: отмена → release резерва; оплата → commitSale; refund → restock; @Scheduled авто-отмена неоплаченных заказов по TTL
- [x] ARCH-10/DB-04: HTTP-вызов шлюза вынесен из транзакции (TransactionTemplate: prepare → HTTP → apply); RestClient с connect/read-таймаутами
- [x] PAY-01: клиенты monobank (UAH, hosted invoice) и Fondy (EUR, hosted checkout) — карты не касаются бэкенда; выбор через `payments.default-provider`
- [x] PAY-05/ORD-07: full refund через провайдера + REFUNDED + возврат склада (`POST /api/admin/payments/{id}/refund`)
- [x] PROMO-01/02/03: promo_codes + promo_redemptions (V11), атомарный `tryRedeem`, лимиты глобальный/на пользователя, повторная валидация в чекауте
- [x] ORD-04: итог = позиции + доставка − скидка на сервере; SHIP-05: порог бесплатной доставки
- [ ] PAY-02: клиентский Idempotency-Key для `POST /api/payments` (сервер генерирует свой; повтор защищён проверкой активной попытки)
- [ ] PAY-07: reconciliation-джоба

### Фаза 4 — Качество и эксплуатация [H] — частично ✅
- [x] REV-01/02/03: verified purchase (только купившие), PENDING→APPROVED/REJECTED (V10), санитизация Jsoup, email не раскрывается в публичных отзывах
- [x] NOTIF-03/ASYNC-02 (основа): доменное событие OrderStatusChanged + @Async listener AFTER_COMMIT на выделенном пуле; сбой уведомления не ломает checkout (SMTP-провайдер подключается реализацией NotificationService)
- [x] CACHE-01/02: Caffeine на категории и рейтинг товара + инвалидация при изменениях
- [x] ERR/OBS частично: correlation ID сквозной; actuator+Prometheus endpoint
- [x] TEST-02/03/09: интеграционные тесты (Testcontainers PostgreSQL): конкурентный oversell-тест, security-тест (deny-by-default, роли, IDOR), 116 тестов зелёные
- [x] DEVOPS-01/02: multi-stage Dockerfile (non-root), docker-compose для БД, GitHub Actions (build→verify→coverage), Dependabot (SEC-13)
- [x] DB-11: soft delete товаров; DB-10 частично (@EnableJpaAuditing включён, createdBy/updatedBy не внедрены)
- [ ] USER-01/02/05: профиль, адресная книга, GDPR export/delete
- [ ] API-02: /api/v1 (сознательно отложено — ломает текущий фронт; вводить вместе с фронтом)
- [ ] ARCH-03: MapStruct (зависимости подключены, мапперы пока ручные)
- [ ] NOTIF-01/02: реальные email-шаблоны и SMTP-провайдер
- [ ] OBS-01/03: JSON-логи, бизнес-метрики Micrometer
- [ ] SEC-14/AUTHZ-05: audit log админ-действий
- [ ] I18N-01: MessageSource

### Фаза 5 — Поэтапно [M]
Гостевая корзина + merge, полнотекстовый поиск (PG FTS), Redis-кэш, outbox + брокер, 2FA/OAuth2, wishlist, мультисклад, курсорная пагинация, ADR/ER-диаграмма, reconciliation-джоба, HIBP, консенты, retention-джобы.

## 5. Порядок работы

Каждая фаза = отдельные коммиты по ID требований. После каждой фазы: `mvnw test` зелёный, миграции `validate`-совместимы. Фазы 0–3 закрывают все [C]; фаза 4 — большинство [H].
