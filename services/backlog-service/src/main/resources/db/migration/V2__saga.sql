-- Состояние саги удаления задачи. Хранится в БД, а не в памяти: иначе
-- перезапуск между шагами оставил бы задачу в DELETING без способа восстановить процесс.
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
