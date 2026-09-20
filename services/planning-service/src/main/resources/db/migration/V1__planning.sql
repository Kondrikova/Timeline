-- Аллокации и конфликты — собственные данные. Остальное — реплики чужих
-- контекстов: целостность через границу обеспечивают саги, а не внешние ключи.

CREATE TABLE plan_version (
    id         uuid PRIMARY KEY,
    name       varchar(128) NOT NULL,
    state      varchar(16)  NOT NULL DEFAULT 'CURRENT',
    created_at timestamptz  NOT NULL DEFAULT now()
);

INSERT INTO plan_version (id, name, state)
VALUES ('00000000-0000-0000-0000-000000000001', 'Current', 'CURRENT');

CREATE TABLE allocation (
    id               uuid PRIMARY KEY,
    plan_version_id  uuid           NOT NULL REFERENCES plan_version (id),
    task_id          uuid           NOT NULL,
    sprint_id        uuid           NOT NULL,
    discipline_id    uuid           NOT NULL,
    planned_sp       numeric(10, 2) NOT NULL CHECK (planned_sp >= 0),
    version          bigint         NOT NULL DEFAULT 0,
    CONSTRAINT allocation_unique UNIQUE (plan_version_id, task_id, sprint_id, discipline_id)
);

CREATE INDEX allocation_task_idx ON allocation (task_id);
CREATE INDEX allocation_sprint_idx ON allocation (sprint_id);

CREATE TABLE plan_conflict (
    id               uuid PRIMARY KEY,
    plan_version_id  uuid         NOT NULL REFERENCES plan_version (id),
    type             varchar(32)  NOT NULL,
    severity         varchar(16)  NOT NULL,
    sprint_id        uuid,
    discipline_id    uuid,
    task_id          uuid,
    details          text         NOT NULL,
    detected_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX plan_conflict_plan_idx ON plan_conflict (plan_version_id);

-- Реплики. Наполняются событиями, читаются только планированием.

CREATE TABLE ref_discipline (
    id                     uuid PRIMARY KEY,
    code                   varchar(32)    NOT NULL,
    name                   varchar(128)   NOT NULL,
    velocity_sp_per_sprint numeric(10, 2) NOT NULL
);

CREATE TABLE ref_member (
    id            uuid PRIMARY KEY,
    discipline_id uuid         NOT NULL,
    full_name     varchar(256),
    active_from   date         NOT NULL,
    active_to     date
);

CREATE TABLE ref_vacation (
    id         uuid PRIMARY KEY,
    member_id  uuid NOT NULL,
    start_date date NOT NULL,
    end_date   date NOT NULL
);

CREATE INDEX ref_vacation_member_idx ON ref_vacation (member_id);

CREATE TABLE ref_sprint (
    id         uuid PRIMARY KEY,
    number     int          NOT NULL,
    name       varchar(128) NOT NULL,
    start_date date         NOT NULL,
    end_date   date         NOT NULL,
    state      varchar(16)  NOT NULL
);

CREATE TABLE ref_calendar_day (
    day        date PRIMARY KEY,
    is_working boolean NOT NULL
);

CREATE TABLE ref_task (
    id      uuid PRIMARY KEY,
    task_key varchar(32)  NOT NULL,
    title   varchar(512) NOT NULL,
    epic_id uuid,
    status  varchar(16)  NOT NULL
);

CREATE TABLE ref_task_estimate (
    id            uuid PRIMARY KEY,
    task_id       uuid NOT NULL,
    discipline_id uuid NOT NULL,
    estimate_sp   numeric(10, 2),
    CONSTRAINT ref_task_estimate_unique UNIQUE (task_id, discipline_id)
);

CREATE TABLE ref_task_link (
    id           uuid PRIMARY KEY,
    from_task_id uuid        NOT NULL,
    to_task_id   uuid        NOT NULL,
    type         varchar(16) NOT NULL,
    hardness     varchar(8)  NOT NULL
);

CREATE TABLE sprint_capacity (
    sprint_id             uuid           NOT NULL,
    discipline_id         uuid           NOT NULL,
    available_person_days int            NOT NULL,
    vacation_person_days  int            NOT NULL,
    capacity_sp           numeric(10, 2) NOT NULL,
    calculated_at         timestamptz    NOT NULL,
    PRIMARY KEY (sprint_id, discipline_id)
);
