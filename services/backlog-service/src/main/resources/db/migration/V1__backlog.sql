CREATE TABLE epic (
    id          uuid PRIMARY KEY,
    epic_key    varchar(32)  NOT NULL UNIQUE,
    name        varchar(256) NOT NULL,
    color       varchar(16),
    order_index int          NOT NULL DEFAULT 0
);

CREATE TABLE task (
    id             uuid PRIMARY KEY,
    task_key       varchar(32)  NOT NULL UNIQUE,
    title          varchar(512) NOT NULL,
    epic_id        uuid REFERENCES epic (id),
    status         varchar(16)  NOT NULL,
    priority       int          NOT NULL DEFAULT 0,
    description    text,
    source         varchar(16)  NOT NULL,
    jira_id        varchar(64),
    jira_synced_at timestamptz,
    state          varchar(16)  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0
);

CREATE INDEX task_epic_idx ON task (epic_id);
CREATE INDEX task_status_idx ON task (status);

-- Оценка задачи — вектор по дисциплинам: без разбиения невозможно сопоставить
-- загрузку с ёмкостью роли. NULL означает «роль задействована, оценки нет».
CREATE TABLE task_estimate (
    id            uuid PRIMARY KEY,
    task_id       uuid           NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    discipline_id uuid           NOT NULL,
    estimate_sp   numeric(10, 2) CHECK (estimate_sp IS NULL OR estimate_sp >= 0),
    CONSTRAINT task_estimate_unique UNIQUE (task_id, discipline_id)
);

-- Временных лагов нет: планирование ведётся в спринтах, связь выражает порядок
-- или совместность. Ацикличность и отсутствие направленной связи внутри группы
-- совместности проверяются на свёрнутом графе в транзакции добавления.
CREATE TABLE task_link (
    id           uuid PRIMARY KEY,
    from_task_id uuid        NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    to_task_id   uuid        NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    type         varchar(16) NOT NULL,
    hardness     varchar(8)  NOT NULL,
    CONSTRAINT task_link_not_self CHECK (from_task_id <> to_task_id),
    CONSTRAINT task_link_unique UNIQUE (from_task_id, to_task_id, type)
);

CREATE INDEX task_link_from_idx ON task_link (from_task_id);
CREATE INDEX task_link_to_idx ON task_link (to_task_id);

CREATE TABLE task_comment (
    id             uuid PRIMARY KEY,
    task_id        uuid         NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    author_user_id varchar(128) NOT NULL,
    body           text         NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX task_comment_task_idx ON task_comment (task_id);
