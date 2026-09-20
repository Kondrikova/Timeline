CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE discipline (
    id                     uuid PRIMARY KEY,
    code                   varchar(32)    NOT NULL UNIQUE,
    name                   varchar(128)   NOT NULL,
    -- SP, которые один сотрудник этой роли закрывает за эталонный спринт.
    velocity_sp_per_sprint numeric(10, 2) NOT NULL CHECK (velocity_sp_per_sprint > 0)
);

CREATE TABLE team_member (
    id            uuid PRIMARY KEY,
    user_id       varchar(128) NOT NULL UNIQUE,
    full_name     varchar(256) NOT NULL,
    discipline_id uuid         NOT NULL REFERENCES discipline (id),
    is_lead       boolean      NOT NULL DEFAULT false,
    active_from   date         NOT NULL,
    active_to     date,
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT team_member_active_period CHECK (active_to IS NULL OR active_to >= active_from)
);

CREATE INDEX team_member_discipline_idx ON team_member (discipline_id);

CREATE TABLE vacation (
    id         uuid PRIMARY KEY,
    member_id  uuid        NOT NULL REFERENCES team_member (id) ON DELETE CASCADE,
    start_date date        NOT NULL,
    end_date   date        NOT NULL,
    type       varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vacation_period CHECK (end_date >= start_date)
);

CREATE INDEX vacation_member_idx ON vacation (member_id);

-- Инвариант непересечения отпусков одного сотрудника. Проверка в коде
-- приложения обходится гонкой двух параллельных запросов, поэтому она здесь.
ALTER TABLE vacation
    ADD CONSTRAINT vacation_no_overlap
        EXCLUDE USING gist (
            member_id WITH =,
            daterange(start_date, end_date, '[]') WITH &&
        );
