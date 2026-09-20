-- Таблицы технического слоя, общие для всех сервисов, публикующих или
-- потребляющих события. Секционированы по времени: растут линейно, очищаются
-- отбрасыванием партиции, а не DELETE с последующим VACUUM.

CREATE TABLE outbox (
    id             bigserial   NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     varchar(64) NOT NULL,
    topic          varchar(64) NOT NULL,
    payload        jsonb       NOT NULL,
    headers        jsonb,
    created_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Партиция по умолчанию избавляет от необходимости заводить новую вручную:
-- нарезка по месяцам подключается регламентным заданием при эксплуатации.
CREATE TABLE outbox_default PARTITION OF outbox DEFAULT;

CREATE INDEX outbox_pending_idx ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_event (
    event_id     uuid        NOT NULL,
    consumer     varchar(64) NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
