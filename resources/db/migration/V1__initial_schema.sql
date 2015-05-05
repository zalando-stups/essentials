CREATE SCHEMA ze_data;
SET search_path TO ze_data;

CREATE TABLE resource_type (
  rt_id              TEXT NOT NULL PRIMARY KEY,
  rt_name            TEXT NOT NULL,
  rt_description     TEXT,
  rt_resource_owners TEXT NOT NULL DEFAULT ''
);

COMMENT ON COLUMN resource_type.rt_resource_owners IS 'Comma-separated list';

CREATE TABLE scope (
  s_id                      TEXT     NOT NULL,
  s_resource_type_id        TEXT     NOT NULL,
  s_summary                 TEXT     NOT NULL,
  s_criticality_level       SMALLINT NOT NULL,
  s_is_resource_owner_scope BOOLEAN  NOT NULL,
  s_description             TEXT,
  s_user_information        TEXT,

  PRIMARY KEY (s_id, s_resource_type_id),
  FOREIGN KEY (s_resource_type_id) REFERENCES resource_type (rt_id) ON DELETE CASCADE
);

CREATE INDEX scope_resource_type_idx
          ON scope (s_resource_type_id);
