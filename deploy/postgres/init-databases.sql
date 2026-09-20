-- База и пользователь на каждый сервис. Права выданы только на собственную базу,
-- поэтому обращение к чужим данным невозможно даже при ошибке в коде.

CREATE USER team     WITH PASSWORD 'team';
CREATE USER backlog  WITH PASSWORD 'backlog';
CREATE USER schedule WITH PASSWORD 'schedule';
CREATE USER planning WITH PASSWORD 'planning';

CREATE DATABASE team     OWNER team;
CREATE DATABASE backlog  OWNER backlog;
CREATE DATABASE schedule OWNER schedule;
CREATE DATABASE planning OWNER planning;

REVOKE CONNECT ON DATABASE team     FROM PUBLIC;
REVOKE CONNECT ON DATABASE backlog  FROM PUBLIC;
REVOKE CONNECT ON DATABASE schedule FROM PUBLIC;
REVOKE CONNECT ON DATABASE planning FROM PUBLIC;

GRANT CONNECT ON DATABASE team     TO team;
GRANT CONNECT ON DATABASE backlog  TO backlog;
GRANT CONNECT ON DATABASE schedule TO schedule;
GRANT CONNECT ON DATABASE planning TO planning;

-- btree_gist нужен ограничениям исключения: непересечение отпусков сотрудника и
-- спринтов. Расширение создаёт суперпользователь, владельцу базы прав не хватит.
\connect team
CREATE EXTENSION IF NOT EXISTS btree_gist;

\connect schedule
CREATE EXTENSION IF NOT EXISTS btree_gist;
