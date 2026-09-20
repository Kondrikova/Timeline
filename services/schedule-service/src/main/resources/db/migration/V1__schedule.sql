CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE sprint (
    id         uuid PRIMARY KEY,
    number     int          NOT NULL UNIQUE,
    name       varchar(128) NOT NULL,
    start_date date         NOT NULL,
    end_date   date         NOT NULL,
    state      varchar(16)  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT sprint_period CHECK (end_date >= start_date)
);

-- Прямое выполнение требования «спринты: валидация» на самом надёжном уровне.
-- Ограничение нельзя обойти гонкой двух параллельных запросов.
ALTER TABLE sprint
    ADD CONSTRAINT sprint_no_overlap
        EXCLUDE USING gist (daterange(start_date, end_date, '[]') WITH &&);

-- Хранятся только отклонения от правила «будни рабочие»: праздники и переносы.
CREATE TABLE calendar_day (
    day        date PRIMARY KEY,
    is_working boolean      NOT NULL,
    comment    varchar(256)
);

CREATE TABLE saga_instance (
    id             uuid PRIMARY KEY,
    type           varchar(64) NOT NULL,
    state          varchar(16) NOT NULL,
    payload        jsonb       NOT NULL,
    failure_reason text,
    started_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX saga_instance_state_idx ON saga_instance (state, started_at);
