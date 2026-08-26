# PriceRadar

PriceRadar — Telegram-бот и backend-сервис для отслеживания цен товаров на
маркетплейсах. Текущий MVP работает только с Wildberries: пользователь отправляет
ссылку на товар, получает приблизительную текущую цену, включает отслеживание и
в дальнейшем получает уведомления о новой минимальной цене за период отслеживания
или достижении заданного порога.

Проект не связан с Wildberries и не является официальным сервисом Wildberries.
Интеграция использует неофициальный внутренний endpoint, который может измениться
или перестать работать без предупреждения.

> Все показываемые цены являются приблизительными. Итоговая цена в аккаунте
> пользователя может зависеть от выбранного города, способа оплаты, персональных скидок,
> акций и других условий Wildberries.

## Содержание

- [Что уже умеет MVP](#что-уже-умеет-mvp)
- [Технологии и требования](#технологии-и-требования)
- [Архитектура](#архитектура)
- [Основные пользовательские сценарии](#основные-пользовательские-сценарии)
- [Модули проекта](#модули-проекта)
- [Модель данных](#модель-данных)
- [Правила работы с ценой](#правила-работы-с-ценой)
- [Интеграция с Wildberries](#интеграция-с-wildberries)
- [Telegram-интеграция](#telegram-интеграция)
- [Планировщик и уведомления](#планировщик-и-уведомления)
- [Конфигурация](#конфигурация)
- [Локальный запуск через Docker Compose](#локальный-запуск-через-docker-compose)
- [Подключение к PostgreSQL](#подключение-к-postgresql)
- [Проверка и тесты](#проверка-и-тесты)
- [Ресурсы и рекомендуемый сервер](#ресурсы-и-рекомендуемый-сервер)
- [Ограничения и потенциальные проблемы](#ограничения-и-потенциальные-проблемы)
- [Что ещё нужно перед публичным production](#что-ещё-нужно-перед-публичным-production)

## Что уже умеет MVP

- Принимать canonical Wildberries URL в личном чате Telegram.
- Импортировать товары из переданной пользователем shared basket Wildberries.
- Добавлять только новые товары либо синхронизировать весь активный список после
  отдельного подтверждения удалений.
- Извлекать `nmId` и необязательный параметр `size`.
- Получать карточку товара через Wildberries provider.
- Выбирать и фиксировать конкретный вариант товара.
- Сохранять `Product`, `WatchTarget` и начальный `PriceSnapshot`.
- Показывать название, бренд, наличие, обычную цену, оценку цены с WB Кошельком,
  город и предупреждение о приблизительности цены.
- Создавать подписку на новую минимальную обычную цену за период отслеживания.
- Создавать подписку на достижение целевой цены.
- Ограничивать пользователя 50 активными подписками.
- Не создавать повторную активную подписку на тот же `WatchTarget`.
- Показывать список активных подписок и завершать подписку.
- Выбирать один из поддерживаемых городов; по умолчанию используется Москва.
- Безопасно завершать все активные подписки после отдельного подтверждения.
- Показывать последнюю сохранённую цену без нового обращения к Wildberries.
- Периодически проверять цены только у товаров с активными подписками.
- Создавать уведомления через transactional outbox и повторять временно
  неуспешную доставку.
- Считать статистику за 7, 30, 365 дней или за весь период текущей подписки.
- Защищать Actuator endpoints с помощью HTTP Basic authentication.

В MVP отсутствуют Ozon, Yandex Market, web-интерфейс, ручное принудительное
обновление цены, headless browser, cookies пользователя и точная персональная
цена Wildberries.

## Технологии и требования

- Java 21 — единственная поддерживаемая версия для build, test и runtime.
- Spring Boot 3.5.x. Spring Boot 4 в проекте не используется.
- Maven с `maven.compiler.release=21`.
- PostgreSQL 18 в локальном Docker Compose.
- Flyway для версионирования схемы БД.
- Spring Data JPA для основной persistence-логики.
- `JdbcTemplate` для агрегирующего запроса статистики.
- Java `HttpClient` для Wildberries и Telegram Bot API.
- Docker Compose для локального запуска.
- JUnit 5 и Testcontainers для автоматизированных проверок.

Java preview features не используются и не должны включаться.

## Архитектура

PriceRadar — **модульный монолит**. Это один Maven-проект, один Spring Boot
процесс и одна PostgreSQL, а не репозиторий с отдельными backend- и bot-сервисами.
Telegram-бот является входным адаптером того же backend-приложения.

```text
Telegram user
     |
     v
telegram module -----> product/tracking/statistics application services
     |                              |
     |                              v
     |                        persistence ports
     |                              |
     v                              v
Telegram Bot API              PostgreSQL

product/scheduler ---> MarketplaceProvider ---> Wildberries internal endpoint

scheduler ---> notification decision ---> outbox ---> Telegram delivery worker
```

### Почему выбран модульный монолит

Для MVP отдельные микросервисы добавили бы сетевые контракты, service discovery,
распределённые транзакции, несколько deployment units и более сложную диагностику,
но не дали бы полезного бизнес-преимущества. Текущий объём нагрузки спокойно
обслуживается одним процессом.

При этом код разделён по предметным пакетам, а внешние системы спрятаны за
интерфейсами. Поэтому границы остаются понятными, классы можно тестировать отдельно,
а конкретную интеграцию при необходимости можно заменить без переписывания всей
системы.

### Внутренние слои

Внутри большинства модулей используются три смысловых уровня:

- `domain` — бизнес-типы и состояния без зависимости от Spring.
- `application` — сценарии, правила и порты, через которые сценарий обращается
  к инфраструктуре.
- `infrastructure` — Spring-конфигурация, JPA/JDBC, HTTP clients и scheduled workers.

Это не отдельные deployable-модули. Граница поддерживается package structure и
направлением зависимостей.

## Основные пользовательские сценарии

### 1. Получение текущей карточки цены

1. `TelegramLongPollingWorker` получает update через `getUpdates`.
2. `TelegramUpdateDispatcher` передаёт текст в `TelegramCurrentQuoteHandler`.
3. `UserProfileService` находит пользователя или создаёт профиль с настройками
   по умолчанию. Выбранный город профиля преобразуется в `PriceContext` для
   обращения к Wildberries.
4. `ProductUrlParser` проверяет URL и извлекает `nmId` и optional `size`.
5. `ResolvedQuoteService` вызывает `MarketplaceProvider`.
6. Wildberries adapter получает и нормализует ответ.
7. `VariantResolutionService` фиксирует вариант товара.
8. `PriceSemanticsService` определяет смысл полей цены.
9. В одной транзакции сохраняются или обновляются `Product`, `WatchTarget` и
   trustworthy `PriceSnapshot`.
10. Бот отправляет карточку с подписанными inline-кнопками.

Запрос пользователя действительно получает новое provider observation. Это
отличается от действия `SHOW_LAST_KNOWN`, которое читает только БД.

### 2. Создание подписки

Inline-кнопка содержит action, UUID конкретного quote snapshot и короткую HMAC
подпись. Она не содержит цену, provider JSON, cookies или токены. Подпись также
привязана к Telegram user id, поэтому другой пользователь не может применить
чужую кнопку.

Quote действует 15 минут. При создании подписки приложение повторно загружает
конкретный snapshot из БД и проверяет:

- snapshot существует;
- его время не находится в будущем;
- с момента наблюдения прошло не больше 15 минут;
- у пользователя ещё нет активной подписки на этот `WatchTarget`;
- число активных подписок меньше 50.
- `PriceContext` quote совпадает с текущим городом пользователя.

Истёкшая кнопка просит отправить URL заново. Она не вызывает provider и не
обходит rate limit.

Для режима `ANY_DECREASE` valid regular price из quote становится начальной
notification reference price этой подписки. Для `TARGET_PRICE` начальное состояние
порога также определяется по конкретному свежему quote.

### 3. Проверка цены по расписанию

Планировщик выбирает только due `WatchTarget`, для которых существует хотя бы
одна активная подписка. Один `WatchTarget` может использоваться несколькими
пользователями, поэтому один provider request и один snapshot переиспользуются
для всех соответствующих подписок.

После успешной проверки следующая назначается примерно через:

```text
6 часов + случайный jitter от 0 до 30 минут
```

Jitter не даёт всем товарам постоянно попадать в одну секунду. После ошибки
обычный retry планировщика назначается через 5 минут, если provider не вернул
более поздний `retryNotBefore`.

### 4. Создание и доставка уведомления

Для каждого подписчика shared observation обрабатывается отдельно:

1. `NotificationDecisionService` сравнивает observation с состоянием конкретной
   подписки.
2. Обновление состояния подписки и запись в `notification_outbox` выполняются
   транзакционно.
3. Idempotency key не позволяет повторно поставить то же уведомление для той же
   подписки и snapshot.
4. Отдельный delivery worker забирает due outbox rows и отправляет Telegram message.
5. Временные ошибки получают exponential backoff, постоянные завершаются статусом
   `FAILED`, успешные — статусом `SENT`.

Так бизнес-транзакция проверки цены не зависит от доступности Telegram в эту же
миллисекунду.

### 5. Просмотр последней цены

`SHOW_LAST_KNOWN` находит последний сохранённый snapshot активной подписки и
показывает его вместе со временем наблюдения. Этот сценарий принципиально:

- не вызывает Wildberries;
- не обновляет provider cache;
- не меняет cooldown;
- не меняет `nextCheckAt`;
- не является manual refresh.

### 6. Статистика

Статистика считается только для активной подписки, принадлежащей пользователю.
Начало периода равно более позднему из:

- даты создания текущей подписки;
- начала выбранного окна: 7, 30 или 365 дней.

`ALL_TIME` означает всё время текущей подписки, а не всю накопленную историю
`WatchTarget`. Если подписку удалить и создать снова, новый период начинается
заново.

## Модули проекта

### `configuration`

Общая техническая конфигурация. Сейчас здесь создаётся безопасно настроенный
Jackson `ObjectMapper`.

Модуль намеренно небольшой: предметные настройки находятся рядом с соответствующей
интеграцией, а не собираются в один глобальный configuration package.

### `pricing`

Отвечает за смысл цены, но ничего не знает о Telegram, HTTP или БД.

Основные типы:

- `RubleAmount` — неотрицательная сумма в копейках (`minorUnits`). Валюта не
  хранится, потому что MVP работает только с RUB.
- `PriceContext` — provider context: `cityName`, `dest`, `spp`.
- `ProviderPriceFields` — нормализованные поля, полученные от provider adapter.
- `InterpretedPrice` — business interpretation observation.
- `PriceSemanticsService` — правила `PRODUCT`, `BASIC_FALLBACK`, `UNAVAILABLE`
  и `NO_PRICE`.
- `WalletEstimateService` — оценка цены с WB Кошельком.
- `RublePriceFormatter` — отображение суммы целыми рублями с математическим
  округлением (`HALF_UP`) без копеек.

Почему суммы хранятся целым числом копеек: это исключает ошибки двоичной
арифметики `double` и соответствует `BIGINT` в схеме БД.

### `region`

Хранит фиксированный каталог поддерживаемых городов и выбранный город пользователя.
Пользователь работает с названием города, а внутреннее значение Wildberries
destination остаётся инфраструктурной деталью. Оно не считается официальным city ID
и может измениться вместе с неофициальным контрактом Wildberries.

Новый и существующий профиль по умолчанию использует Москву. Изменить город можно
только при отсутствии активных подписок: существующие `WatchTarget` и их история
не переносятся и не смешиваются с ценами другого города. Product URL и shared basket
используют текущий город профиля, а scheduler продолжает использовать `PriceContext`,
который уже сохранён в конкретном `WatchTarget`.

Каталог MVP: Москва, Санкт-Петербург, Екатеринбург, Новосибирск, Красноярск,
Иркутск, Братск и Чита. Автоматический поиск города или ПВЗ пока не реализован.

### `product`

Отвечает за переход от пользовательского URL к persisted quote.

- `ProductUrlParser` принимает только canonical HTTPS URL
  `www.wildberries.ru/catalog/{nmId}/detail.aspx` и optional query parameter
  `size`.
- `VariantResolutionService` выбирает стабильный вариант.
- `ResolvedQuoteService` координирует parser, provider, variant resolution,
  price semantics и persistence.
- `ProductQuoteStore` и `ResolvedQuoteStore` — application ports.
- JPA adapters сохраняют продукт, цель наблюдения и snapshot.

Если `size` явно указан в URL и существует в provider response, сохраняется
именно он, даже если сейчас недоступен: недоступность является состоянием
snapshot, а не поводом незаметно выбрать другой размер.

Если `size` не указан, первый доступный вариант с valid regular price выбирается
один раз. Его stable key сохраняется в `WatchTarget`; будущий scheduler не
перевыбирает размер.

Для товара без размерной сетки используется `NO_VARIANT`.

### `marketplace`

Это граница внешнего marketplace provider.

`MarketplaceProvider` описывает два действия:

- resolve товара при обработке пользовательской ссылки;
- получение observation для уже зафиксированного `WatchKey`.

Application layer получает `MarketplaceProductDetails` или typed
`MarketplaceProviderFailure`, а не сырой Wildberries JSON.

`ProviderAccessCoordinator` обеспечивает:

- максимум один in-flight Wildberries request в текущем процессе;
- минимальную паузу между сетевыми вызовами;
- проверку persisted cooldown;
- безопасную блокировку запросов, если состояние cooldown не удаётся прочитать;
- сохранение `last_request_at` и cooldown.

### `marketplace.wildberries`

Конкретный adapter Wildberries:

- `WildberriesCardDetailUrlBuilder` строит разрешённый URI.
- `WildberriesMarketplaceProvider` выполняет HTTP-запросы, retry/backoff,
  обрабатывает HTTP status и поддерживает небольшой in-memory cache.
- `WildberriesCardMapper` парсит структуру `cards/v4/detail`.
- `WildberriesMappedProduct` и mapping failure types остаются внутри adapter.
- `WildberriesProviderProperties` содержит safety configuration.
- `WildberriesSharedBasketProvider` читает shared basket и batch-resolves товары,
  строго сопоставляя `chrtId` с `size.optionId`.

Cache хранит ответ по `(nmId, dest, spp)` пять минут и ограничен 10 000
элементами. Он нужен, чтобы повторные одинаковые действия рядом по времени не
создавали лишний запрос к Wildberries. Cache:

- находится только в памяти процесса;
- очищается после перезапуска;
- не заменяет `PriceSnapshot`;
- не является общей cache для нескольких экземпляров приложения;
- сохраняет исходное `observedAt`, поэтому повторное использование cache не
  изображает новое сетевое наблюдение.

### `user`

Хранит связь внутреннего UUID с Telegram identity:

- `telegram_user_id` определяет пользователя;
- `telegram_chat_id` определяет личный чат для ответов и уведомлений;
- `region_code` связывает профиль с фиксированным каталогом городов;
- `UserPricePreferences` хранит процент оценки WB Кошелька.

Новый пользователь получает город Москва и wallet discount 3%. Город можно
изменить в Telegram при отсутствии активных отслеживаний. `PriceContext` строится
из выбранной записи каталога; технический WB destination пользователю не показывается.

### `tracking`

Отвечает за `WatchKey`, подписку и её жизненный цикл.

`WatchKey` объединяет:

- marketplace;
- внешний id товара;
- stable variant key;
- `dest`;
- `spp`.

Именно поэтому два пользователя с одинаковым товаром, вариантом и городским
контекстом могут использовать один `WatchTarget` и shared price history.

`Subscription` принадлежит одному пользователю и одному `WatchTarget`. Она
содержит notification mode, notification reference/threshold state, `createdAt`
и optional `endedAt`. Завершённая подписка не оживляется: повторное добавление
создаёт новую строку и новый статистический период.

### `telegram`

Входной и выходной adapter Telegram Bot API.

- `TelegramBotApiClient` выполняет `getUpdates`, `sendMessage` и
  `answerCallbackQuery`.
- `TelegramLongPollingWorker` периодически получает updates.
- `TelegramPollingStateStore` сохраняет последний подтверждённый update id.
- `TelegramUpdateDispatcher` выбирает handler.
- Quote, tracking, tracked-list, last-known и statistics handlers реализуют UX.
- `TrackingCallbackCodec` подписывает callback HMAC-SHA256.
- `PendingTargetPriceStore` сохраняет короткий двухшаговый сценарий ввода
  целевой цены.
- `TelegramSharedBasketHandler` показывает preview импорта и требует повторное
  подтверждение перед завершением отсутствующих в корзине подписок.

### `sharedbasket`

Изолированный сценарий импорта shared basket. Пользователь явно отправляет новую
ссылку; фонового перечитывания старого `shareId` нет. Сервис удаляет точные
дубликаты с сохранением исходного порядка, разрешает только вариант с
`size.optionId == chrtId` и сохраняет результат в короткоживущую persisted session
на 15 минут. Callback содержит только session id, action и HMAC-подпись.

Доступны два действия:

- «Добавить новые» сохраняет существующие подписки без изменений и создаёт новые
  в режиме новой минимальной цены;
- «Синхронизировать» приводит активный набор к первым 50 корректным товарам
  корзины. Подписки вне набора завершаются только после второго подтверждения,
  исторические snapshots физически не удаляются.

Импорт не получает доступ к аккаунту Wildberries: cookies, Authorization и
пользовательские credentials не используются и не сохраняются. Shared basket API
не документирован и может измениться.

Long polling выбран для простого локального и VPS-запуска: ему не нужны домен,
HTTPS certificate или публичный webhook endpoint.

Update подтверждается в БД только после обработки. Временная ошибка останавливает
текущую batch, чтобы update был повторён. После пяти постоянных неудач poison
update пропускается с записью ошибки в лог, иначе он навсегда заблокировал бы
обработку последующих сообщений.

### `scheduler`

Фоновое обновление цен:

- `WatchTargetSchedulerWorker` запускает выборку due targets.
- `JpaDueWatchTargetReader` выбирает только targets с active subscriptions.
- `WatchTargetCheckService` вызывает provider и интерпретирует observation.
- `WatchTargetCheckTransaction` отдельно коммитит shared snapshot и scheduling
  state.
- `NotificationFanOutService` распространяет observation по подпискам.
- `SubscriptionNotificationProcessor` обрабатывает каждую подписку в отдельной
  транзакции `REQUIRES_NEW`.

Разделение shared snapshot и per-subscription обработки важно: ошибка одной
подписки не должна откатить реальное наблюдение цены и результаты для всех
остальных пользователей.

### `notification`

Содержит две независимые части:

- decision/state machine;
- outbox delivery.

Для `ANY_DECREASE` notification reference price принадлежит подписке и означает
минимальную valid regular price текущего периода отслеживания. Рост цены не повышает
reference. Отдельный timestamp последнего обработанного valid observation защищает
state machine от replay и out-of-order snapshots.

Для `TARGET_PRICE` уведомление отправляется при переходе к цене меньше или равной
порогу. Новое уведомление возможно только после того, как цена сначала стала
выше порога и тем самым re-arm состояние.

Outbox использует claim timeout. Это позволяет другому циклу снова забрать запись,
если worker завершился после claim. Доставка имеет семантику at-least-once:
крайне редкий crash после фактической отправки Telegram message, но до фиксации
`SENT` в БД, теоретически может привести к повторной отправке.

### `statistics`

`SubscriptionStatisticsService` проверяет ownership активной подписки и определяет
границы периода. `JdbcPriceStatisticsStore` одним SQL-запросом получает first/latest,
minimum с timestamp, maximum, average и count. Из first/latest рассчитываются
абсолютное и процентное изменение, а из latest/minimum — расстояние до минимума.

JDBC выбран здесь вместо загрузки snapshots через JPA: агрегаты эффективнее и
понятнее считать непосредственно в PostgreSQL.

### `operations`

Публикуются только Actuator `health`, `info` и `metrics`. Они защищены отдельным
in-memory пользователем с ролью `ACTUATOR` и HTTP Basic. Любой другой входящий
HTTP endpoint запрещён второй security chain.

Telegram flow не использует Spring Security, потому что Telegram identity и
подпись callback проверяются внутри Telegram adapter.

## Модель данных

Основные связи:

```text
user_profiles 1 ----- N subscriptions N ----- 1 watch_targets
                                             |
products 1 ------------------------------- N |
                                             |
                                             1
                                             |
                                             N
                                      price_snapshots

subscriptions 1 ---------------------------- N notification_outbox
```

### Таблицы

| Таблица | Назначение |
|---|---|
| `user_profiles` | Telegram identity, выбранный город и пользовательская wallet preference |
| `marketplace_regions` | Фиксированный каталог городов и opaque WB destination |
| `products` | Уникальный товар marketplace |
| `watch_targets` | Товар + фиксированный вариант + `dest/spp` + расписание |
| `price_snapshots` | Наблюдения цены и доступности |
| `subscriptions` | Пользовательский период отслеживания и notification state |
| `notification_outbox` | Надёжная очередь исходящих уведомлений |
| `provider_states` | Persisted cooldown и время обращения provider |
| `telegram_polling_state` | Persisted Telegram update offset и poison-update state |
| `telegram_pending_target_prices` | Короткоживущий шаг ожидания целевой цены |
| `pending_shared_basket_imports` | Короткоживущая persisted session preview импорта |
| `pending_shared_basket_import_items` | Упорядоченные серверно разрешённые targets session |

Отдельной таблицы `pending_quotes` нет. Quote identity — UUID конкретного
`price_snapshots`, а TTL проверяется по его `observed_at`.

### Как пользователь связан с товаром

Связь проходит через подписку:

```text
user_profiles -> subscriptions -> watch_targets -> products
```

Пример диагностического запроса:

```sql
SELECT
    u.telegram_user_id,
    s.id AS subscription_id,
    s.status,
    s.notification_mode,
    p.external_product_id AS nm_id,
    p.title,
    wt.variant_kind,
    wt.variant_value,
    wt.city_name,
    s.created_at,
    s.ended_at
FROM subscriptions s
JOIN user_profiles u ON u.id = s.user_id
JOIN watch_targets wt ON wt.id = s.watch_target_id
JOIN products p ON p.id = wt.product_id
ORDER BY s.created_at DESC;
```

## Правила работы с ценой

### Поля Wildberries

- `price.product` — обычная наблюдаемая цена (`regularPrice`).
- `price.basic` — marketing/crossed-out base price, а не доказанная предыдущая
  цена товара.
- Если есть только `price.basic`, snapshot получает статус `BASIC_FALLBACK`.
- Недоступный вариант получает `UNAVAILABLE`.
- Доступный вариант без распознаваемой цены получает `NO_PRICE`.

Только `REGULAR_PRICE` с source `PRODUCT` участвует в notification reference,
уведомлениях и статистике. `BASIC_FALLBACK`, `UNAVAILABLE` и `NO_PRICE` сохраняются
как полезные business observations, но не искажают аналитику.

### WB Кошелёк

Endpoint не предоставляет гарантированную точную цену WB Кошелька. MVP считает
оценку:

```text
walletPriceEstimate =
    floor(regularPriceMinor * (100 - walletDiscountPercent) / 100)
```

Default discount равен 3%, source — `ESTIMATED_BY_PERCENT`. Сначала вычисление
выполняется в копейках с округлением вниз по формуле, а пользовательское
отображение затем математически округляется до целых рублей без копеек.

## Интеграция с Wildberries

Wildberries endpoint считается нестабильным. Adapter должен корректно переживать:

- HTTP 403;
- HTTP 429 и `Retry-After`;
- HTTP 5xx;
- network timeout или разрыв соединения;
- пустой ответ;
- malformed JSON;
- missing fields и schema drift;
- отсутствие товара или зафиксированного варианта.

Safety defaults:

| Настройка | Значение |
|---|---:|
| Minimum delay | 3 секунды |
| Одновременные запросы | 1 |
| Request timeout | 10 секунд |
| Max attempts | 3 |
| Base backoff | 2 секунды |
| Max backoff | 1 минута |
| Cache TTL | 5 минут |
| Max response body | 2 MiB |
| Max accepted `Retry-After` | 24 часа |

Приложение не запрашивает и не хранит Wildberries cookies, authorization tokens,
browser session data или данные личного аккаунта.

## Telegram-интеграция

### Команды и действия

- `/start` — главное меню.
- `/tracked` — мои товары.
- `/add` — добавить один товар.
- `/import` — импорт общей корзины Wildberries.
- `/help` — помощь.
- URL Wildberries — получить quote.
- Inline actions — начать отслеживание, задать threshold, показать последнее
  observation, статистику или завершить подписку.
- «Город» — выбрать один из фиксированных поддерживаемых городов.
- «Очистить все» — завершить все активные подписки после повторного подтверждения;
  historical snapshots при этом сохраняются.

Список команд регистрируется через Telegram Bot API и доступен через стандартную
кнопку Menu в Telegram.

Обрабатываются только личные чаты. Это упрощает ownership: Telegram user id и chat
id относятся к одному пользователю.

### Секреты

- `TELEGRAM_BOT_TOKEN` выдаёт BotFather.
- `TELEGRAM_CALLBACK_SECRET` — отдельный случайный секрет минимум 32 байта.

Эти значения нельзя коммитить, писать в README реальными значениями или включать
в callback data.

## Планировщик и уведомления

В приложении работают три основных scheduled loop:

| Worker | Default interval | Назначение |
|---|---:|---|
| Telegram polling | 1 секунда между long polls | Получение user updates |
| Notification delivery | 5 секунд | Доставка due outbox rows |
| Price scheduler | 60 секунд | Поиск due watch targets |

Частый poll scheduler не означает запрос к Wildberries каждую минуту. Он только
ищет targets, у которых наступил `next_check_at`; нормальный refresh interval
самого target — 6 часов плюс jitter.

## Конфигурация

### Обязательные environment variables

| Переменная | Назначение |
|---|---|
| `POSTGRES_PASSWORD` | Пароль локальной PostgreSQL в Compose |
| `ACTUATOR_USERNAME` | Пользователь защищённых Actuator endpoints |
| `ACTUATOR_PASSWORD` | Пароль Actuator |
| `TELEGRAM_BOT_ENABLED` | `true` для реального бота, `false` для запуска без него |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token; обязателен при enabled bot |
| `TELEGRAM_CALLBACK_SECRET` | Секрет подписи callback, минимум 32 байта |

Приложение внутри Compose получает `DB_URL`, `DB_USERNAME` и `DB_PASSWORD`
автоматически из параметров PostgreSQL service.

### Важные non-secret defaults

Они определены в `src/main/resources/application.yml`:

- город нового пользователя: Москва; каталог городов и технические WB destination
  хранятся в PostgreSQL;
- wallet discount: 3%;
- provider delay: 3 секунды;
- quote TTL: 15 минут в application logic;
- refresh: 6 часов + jitter до 30 минут;
- active subscription limit: 50.

## Локальный запуск через Docker Compose

### 1. Требования

- Docker Desktop или Docker Engine с Compose plugin.
- Доступ к `api.telegram.org` и `card.wb.ru` из Docker network.
- Telegram bot token от BotFather.

JDK и Maven на host не нужны, если используется только Docker build. Dockerfile
собирает проект на Java 21 и запускает его на Java 21 JRE.

### 2. Создать локальный `.env`

Файл `.env` должен находиться рядом с `compose.yaml` и не должен попадать в Git:

```dotenv
POSTGRES_DB=priceradar
POSTGRES_USER=priceradar
POSTGRES_PASSWORD=replace-with-a-long-random-password
POSTGRES_PORT=5432

ACTUATOR_USERNAME=admin
ACTUATOR_PASSWORD=replace-with-another-long-random-password

TELEGRAM_BOT_ENABLED=true
TELEGRAM_BOT_TOKEN=replace-with-token-from-botfather
TELEGRAM_CALLBACK_SECRET=replace-with-random-secret-at-least-32-bytes

APP_PORT=8080
```

Сгенерировать callback secret в PowerShell можно так:

```powershell
[Convert]::ToBase64String(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

### 3. Собрать и запустить

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

При первом запуске Flyway создаст схему. Hibernate после этого проверит, что JPA
mappings соответствуют БД.

### 4. Проверить Telegram

Отправьте боту `/start`, затем canonical Wildberries URL:

```text
https://www.wildberries.ru/catalog/123456/detail.aspx
```

Успешные long polling запросы могут не создавать отдельную строку на каждый poll,
поэтому главная проверка — бот отвечает на `/start` и в логах нет повторяющейся
ошибки Telegram.

Проверить доступность Bot API именно из app container:

```powershell
docker compose exec app sh -c 'wget -qO- "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe"'
```

Команда должна вернуть JSON с `"ok":true`. Не публикуйте команду с уже
подставленным токеном и не сохраняйте её вывод в публичные логи.

### 5. Проверить Actuator

Без credentials запрос должен быть отклонён:

```powershell
curl.exe -i http://localhost:8080/actuator/health
```

С credentials:

```powershell
curl.exe -u "$env:ACTUATOR_USERNAME`:$env:ACTUATOR_PASSWORD" `
    http://localhost:8080/actuator/health
```

### 6. Остановка

```powershell
docker compose down
```

Данные PostgreSQL сохранятся в named volume. Команда ниже удаляет и контейнеры,
и данные БД, поэтому используйте её только для сознательного полного сброса:

```powershell
docker compose down -v
```

## Подключение к PostgreSQL

Параметры для HeidiSQL, IntelliJ IDEA или другого клиента:

| Поле | Значение по умолчанию |
|---|---|
| Network type | PostgreSQL |
| Host | `127.0.0.1` |
| Port | `5432` или `POSTGRES_PORT` |
| User | `priceradar` или `POSTGRES_USER` |
| Password | значение `POSTGRES_PASSWORD` |
| Database | `priceradar` или `POSTGRES_DB` |

Посмотреть таблицы через CLI:

```powershell
docker compose exec postgres psql -U priceradar -d priceradar
```

Полезные команды `psql`:

```text
\dt
\d subscriptions
\d price_snapshots
```

Для production PostgreSQL port не следует публиковать в интернет. Разрешайте
доступ только локально, через firewall, private network или SSH tunnel.

## Проверка и тесты

Полная локальная проверка:

```powershell
mvn clean verify
```

Docker-конфигурация:

```powershell
docker compose config
docker compose build
```

Автоматические тесты используют local HTTP stubs и не должны обращаться к
реальному Wildberries или отправлять настоящие Telegram messages. Persistence
smoke test использует Testcontainers PostgreSQL и требует работающий Docker.

Основные покрытые зоны:

- URL parsing и stable variant resolution;
- Wildberries JSON mapping;
- provider cache, delay, retry и cooldown;
- price semantics;
- quote TTL и callback signature;
- subscription limit и lifecycle;
- notification state machine и outbox;
- scheduler/fan-out;
- subscription-scoped statistics;
- Telegram API error handling;
- Flyway/JPA persistence smoke.

## Ресурсы и рекомендуемый сервер

Локальный замер работающего Compose после запуска бота:

| Container | CPU в момент замера | RAM | Image |
|---|---:|---:|---:|
| `price-radar-app-1` | около 0.2% | около 381 MiB | 128 MB |
| `price-radar-postgres-1` | около 0% | около 77 MiB | 119 MB |
| **Итого** | менее 1% в простое | **около 458 MiB** | **247 MB** |

Размер тестовой БД с одним пользователем, товаром, snapshot и подпиской составлял
около 8.3 MiB. Это не load test: при сборке, старте JVM, vacuum PostgreSQL и
network spikes потребление будет выше.

Для личного использования и примерно 10 активных пользователей:

| Вариант | Конфигурация | Оценка |
|---|---|---|
| Технический минимум | 2 vCPU, 2 GB RAM, 25–40 GB SSD | Запустится, но мало запаса для Docker, JVM, PostgreSQL и обновлений |
| Рекомендуемый | 2 vCPU, 4 GB RAM, 40–60 GB NVMe | Нормальный запас для MVP, логов, backup и кратких пиков |
| С запасом | 4 vCPU, 8 GB RAM | Нужен только при заметном росте нагрузки или дополнительных сервисах |

Для 10 пользователей bottleneck — не CPU, а намеренно последовательный доступ к
Wildberries. При лимите 50 подписок на пользователя теоретический максимум для
10 пользователей — 500 подписок. Даже если все watch targets различны и
проверяются каждые 6 часов, это в среднем около 84 provider checks в час. Однако
минимальная пауза 3 секунды и один in-flight request ограничат bursts; расписание
с jitter распределяет их во времени.

Для одного MVP-инстанса достаточно одного VPS с приложением и PostgreSQL. Это не
high availability: отказ VPS остановит и bot, и DB до восстановления.

## Ограничения и потенциальные проблемы

### Нестабильный Wildberries endpoint

Endpoint неофициальный. Даже корректный код не гарантирует, что завтра структура
ответа или правила доступа не изменятся. Симптомами будут `SCHEMA_VIOLATION`,
`MALFORMED_RESPONSE`, HTTP 403/429 или отсутствие ожидаемого варианта.

### Приблизительность цены

Система не видит персональный аккаунт пользователя. Она не знает точную скидку,
способ оплаты и условия для выбранного города. Wallet price — только estimate.

### Фиксированный каталог городов

MVP не ищет города или ПВЗ автоматически и не принимает произвольный город.
Для каждого доступного города используется одно вручную проверенное WB destination.
Пользователь должен завершить активные отслеживания перед сменой города.

### Один application instance

In-memory semaphore provider coordinator, in-memory cache и Telegram long polling
рассчитаны на один экземпляр приложения. Persisted cooldown переживает restart,
но не является полноценным distributed lock. Запуск двух replicas без
дополнительной координации может:

- нарушить глобальное ограничение one in-flight provider request;
- дублировать polling одного Telegram bot;
- одновременно выбрать одинаковые due targets.

Вертикального scaling одного VPS для текущего MVP достаточно.

### Нет exactly-once доставки Telegram

Outbox защищает от потери intent и большинства дублей. Но Telegram API не даёт
transactional send вместе с нашей PostgreSQL. Crash в узком окне между успешным
`sendMessage` и `markSent` может привести к повторной доставке.

### Рост истории snapshots

Каждая trustworthy scheduled observation остаётся в `price_snapshots`. Retention
policy и архивация в MVP отсутствуют. Для 10 пользователей рост будет небольшим,
но размер таблицы и индексов нужно наблюдать.

### Compose — не полная production-платформа

Текущий Compose не настраивает:

- TLS и reverse proxy;
- firewall;
- автоматические backup и проверку restore;
- log rotation и централизованный мониторинг;
- CPU/RAM limits;
- deployment без downtime;
- high availability PostgreSQL.

Кроме того, PostgreSQL port публикуется на host для удобства локальной разработки.
На VPS его нельзя оставлять доступным всему интернету.

### Нет CI

GitHub Actions workflow пока отсутствует. Перед merge необходимо вручную запускать
`mvn clean verify`; настройка CI остаётся отдельной задачей.

## Что ещё нужно перед публичным production

Для portfolio/demo и небольшой закрытой группы текущая функциональность близка к
готовому MVP. Перед публичным размещением рекомендуется:

1. Завершить review текущих bug fixes и закоммитить их.
2. Добавить GitHub Actions на JDK 21.
3. Выполнить полный manual smoke test на отдельном Telegram bot.
4. Ограничить доступ firewall: наружу обычно не нужны ни `5432`, ни `8080`.
5. Настроить регулярный `pg_dump`, хранение backup вне VPS и тест восстановления.
6. Добавить Docker resource limits и disk/log monitoring.
7. Настроить уведомление о недоступности приложения и заполнении диска.
8. Зафиксировать production secrets вне Git.
9. Проверить доступ VPS к Telegram и Wildberries из места размещения сервера.
10. Продумать retention policy snapshots до существенного роста истории.

Не следует добавлять микросервисы, Kubernetes или отдельную cache database только
ради deployment: для текущей нагрузки это усложнение без практической пользы.
