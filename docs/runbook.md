# Runbook — эксплуатационные инциденты

## Быстрая диагностика

- Здоровье: `GET /actuator/health` (liveness/readiness), метрики:
  `GET /actuator/metrics` и `/actuator/prometheus` (нужна роль ADMIN).
- Каждый ответ несёт `X-Request-Id` — ищите его в логах (`requestId` в MDC,
  в prod-профиле логи в JSON/ECS).
- Бизнес-метрики: `shopupu.orders{event=created|paid|cancelled|refunded}`,
  `shopupu.payments{result=succeeded|failed}`.

## Платёжный провайдер недоступен / зависшие оплаты

Симптомы: рост `shopupu.payments{result=failed}`, жалобы «не открывается оплата»,
в логах `Payment gateway call failed`.

1. Проверить доступность провайдера и корректность секретов
   (`PAYMENT_SERVICE_*`, `MONOBANK_TOKEN`, `FONDY_*`).
2. Ничего не чинить руками в БД: недоведённые платежи автоматически истекают
   (job каждые 5 минут → статус EXPIRED, заказ возвращается в CREATED,
   пользователь может платить снова).
3. Неоплаченные заказы освобождают резерв склада по TTL
   (`CHECKOUT_PENDING_TTL_MIN`, по умолчанию 30 мин) — oversell невозможен.
4. Если провайдер подтвердил списание, а заказ не PAID: проверить, дошёл ли
   webhook (таблица `payment_events`), подпись (`PAYMENT_CALLBACK_SECRET`)
   и статусные переходы; повторную доставку webhook можно запросить у
   провайдера — обработка идемпотентна по `external_event_id`.

## Reconciliation: расхождение статусов с провайдером

Симптомы: рост `shopupu.payments{result=reconciliation_mismatch}`, в логах WARN
`Payment reconciliation mismatch: payment ... is X locally but Y at the provider`.

Джоба каждые 15 минут сверяет незакрытые платежи (CREATED/PENDING/EXPIRED за
последние 24 ч) со статусом у провайдера. Она **ничего не меняет** — источник
истины по-прежнему подписанный webhook.

1. `X=PENDING, Y=SUCCEEDED` (потерян webhook): запросить повторную доставку
   webhook у провайдера — обработка идемпотентна; заказ станет PAID штатно.
2. `X=EXPIRED, Y=SUCCEEDED` (клиент оплатил после нашего TTL): деньги списаны,
   заказ уже отменён и резерв снят. Связаться с клиентом: повторно провести
   заказ или сделать refund у провайдера. Не «чинить» статус руками в БД.
3. `X=PENDING, Y=FAILED/EXPIRED`: ничего не делать — job истечения сам переведёт
   платёж в EXPIRED и вернёт заказ в CREATED.

## БД недоступна

1. `GET /actuator/health` покажет DOWN; приложение продолжает отдавать 5xx —
   не рестартовать без нужды, Hikari восстановит пул сам (connection-timeout 3s).
2. Проверить количество коннектов на стороне PostgreSQL; leak detection пишет
   в лог стектрейсы удержанных >30s коннектов.

## Рост 5xx

1. Грепать логи по `"Unhandled exception"` — каждый содержит requestId и стек.
2. 4xx (validation/401/403/429) — WARN, не считаются инцидентом.
3. Rate limiting отдаёт 429 c `Retry-After` — при легитимном всплеске поднять
   лимиты `app.rate-limit.*`.

## Подозрение на компрометацию аккаунта

1. Таблица `audit_events`: `LOGIN_FAILED`/`LOGIN_SUCCEEDED` по актору.
2. Отзыв всех сессий пользователя: смена пароля пользователем ИЛИ вручную
   `update refresh_tokens set revoked = true where user_id = ?`.
3. Повторное использование refresh-токена уже отзывает всю цепочку
   автоматически (см. WARN "Refresh token reuse detected").

## Откат релиза

1. Образ: откатить на предыдущий тег (schema совместима: миграции только
   аддитивные, expand/contract).
2. Flyway-миграции не откатываются автоматически; для отката несовместимой
   миграции — восстановление из бэкапа + повторный прогон (см. стратегию
   бэкапов вашего PostgreSQL: pg_dump ежедневно + WAL/PITR рекомендуется).
