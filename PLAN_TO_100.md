# План доведения до 100% — shopupu (backend)

Текущее состояние: production-ready MVP. Фазы 0–3 `REFACTORING_PLAN.md` закрыты полностью, фаза 4 ~90%. «100%» здесь = добить хвосты фазы 4, формально зафиксировать границу v1 и задеплоить.

## 1. Хвосты фазы 4
- [x] ARCH-03: перевести ручные мапперы entity→DTO на MapStruct (зависимость 1.6.3 уже в `pom.xml`, используется вручную). _2026-08-10: все 5 мапперов — интерфейсы с `unmappedTargetPolicy = ERROR`._
- [x] Reconciliation-job платежей: периодическая сверка статусов провайдера (monobank/Fondy) с локальными orders/payments; расхождения — в лог + метрику `shopupu.payments`. _2026-08-10: `PaymentReconciliationJob` (15 мин), `fetchPaymentStatus` у клиентов, процедура в runbook._
- [x] Глубокий прогон Sign in with Google (подключён через V18, мало тестировался): регистрация, линковка с существующим email-аккаунтом, refresh-flow. _2026-08-10: `GoogleAuthFlowIT` + юнит-тесты; найден и исправлен баг — ревокация цепочки refresh-токенов при reuse откатывалась вместе с транзакцией (теперь REQUIRES_NEW)._

## 2. Формально определить границу v1
- [x] В `REFACTORING_PLAN.md` пометить Phase 5 (Redis-кэш, outbox+брокер, PG FTS, 2FA, cursor pagination, HIBP, мульти-склад) как **out-of-scope v1** — иначе «100%» недостижимо по определению.
- [x] Прогнать полный `./mvnw verify` + перепроверить, что JaCoCo gate и Spotless зелёные. _2026-08-11: BUILD SUCCESS, 189 тестов, coverage gate met, Spotless clean._

## 3. Деплой (выполняется на VPS; локально готово: прод-образ собирается, compose-профиль prod и docs/deploy-cloudflare.md на месте)
- [ ] VPS: `docker compose up -d db` + `docker compose --profile prod up -d app` (network_mode=host, порт 8080).
- [ ] Домен (судя по фронту — shopupu.net): реверс-прокси с TLS перед 8080.
- [ ] Боевой `.env`: `JWT_SECRET` (48+ байт), `SPRING_PROFILES_ACTIVE=prod`, `PAYMENTS_DEFAULT_PROVIDER` (monobank/fondy — не stub), токены провайдера, **`PAYMENT_CALLBACK_SECRET`** (fail-closed), `CORS_ALLOWED_ORIGINS=https://shopupu.net`, `FRONTEND_BASE_URL`, `RESEND_API_KEY` или SMTP.
- [ ] Вебхуки: боевой `PAYMENT_CALLBACK_URL` (cloudflared-профиль уже готов для теста).
- [ ] Смоук: регистрация → каталог → корзина → checkout (идемпотентность) → оплата (sandbox) → статусы → refund; email-уведомления.

## 4. Гигиена
- [x] Обновить knowledge-граф: `graphify update .` (снапшот от 2026-07-08, после него были коммиты: AI-каталог, CORS, guest carts). _2026-08-11: 3322 узла / 7911 рёбер, +978 узлов к прошлому снапшоту._
- [x] Решение по AI-фичам в проде: включить (Ollama на сервере / Voyage ключ + DeepSeek) или оставить stub — отметить в README. _Зафиксировано в README: включено (Ollama bge-m3 на сервере + DeepSeek flash), откат — `AI_ENABLED=false`._

## Локальные порты (без изменений — конфликтов нет)
API 8080, Postgres 127.0.0.1:5432, Ollama 11434 (опц.). Фронт ожидается на localhost:3000 (CORS) — shopupu-web там и остаётся.
