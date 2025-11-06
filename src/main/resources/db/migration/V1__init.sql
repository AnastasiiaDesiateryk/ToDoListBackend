-- V1__init.sql

-- === extensions ===
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- === enum types ===
CREATE TYPE task_priority AS ENUM ('LOW','MED','HIGH');
CREATE TYPE task_status   AS ENUM ('TODO','DONE');
CREATE TYPE share_role    AS ENUM ('viewer','editor');

-- === app_user ===
CREATE TABLE app_user (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email        TEXT UNIQUE NOT NULL,
  display_name TEXT,
  created_at   TIMESTAMPTZ DEFAULT now()
);

-- === task ===
CREATE TABLE task (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  title       TEXT NOT NULL,
  description TEXT,
  category    TEXT,
  priority    task_priority NOT NULL DEFAULT 'MED',
  due_at      TIMESTAMPTZ,
  status      task_status   NOT NULL DEFAULT 'TODO',
  source      TEXT NOT NULL DEFAULT 'user',
  metadata    TEXT,  -- LOB / @Lob TEXT в entity
  version     INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT now(),
  updated_at  TIMESTAMPTZ DEFAULT now()
);

-- === task_tags (ElementCollection) ===
CREATE TABLE task_tags (
  task_id UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
  tag     TEXT NOT NULL,
  PRIMARY KEY (task_id, tag)
);

CREATE INDEX idx_task_tags_tag ON task_tags(tag);

-- === task_share (EmbeddedId) ===
CREATE TABLE task_share (
  task_id UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  role    share_role NOT NULL,
  PRIMARY KEY (task_id, user_id)
);

-- === indexes ===
CREATE INDEX idx_task_owner    ON task(owner_id);
CREATE INDEX idx_task_status   ON task(status);
CREATE INDEX idx_task_priority ON task(priority);

-- === trigger updated_at ===
CREATE FUNCTION task_updated_at_trigger() RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_task_updated_at
BEFORE UPDATE ON task
FOR EACH ROW
EXECUTE FUNCTION task_updated_at_trigger();
