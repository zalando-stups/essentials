-- name: read-resource-types
SELECT rt_id,
       rt_name
  FROM ze_data.resource_type
 ORDER BY rt_id ASC;

-- name: read-resource-type
SELECT rt_id,
       rt_name,
       rt_description,
       rt_resource_owners
  FROM ze_data.resource_type
 WHERE rt_id = :resource_type_id;

-- name: create-or-update-resource-type!
WITH resource_type_update AS (
     UPDATE ze_data.resource_type
        SET rt_name = :name,
            rt_description = :description,
            rt_resource_owners = :resource_owners
      WHERE rt_id = :resource_type_id
  RETURNING rt_id
)
INSERT INTO ze_data.resource_type (
            rt_id,
            rt_name,
            rt_description,
            rt_resource_owners )
     SELECT :resource_type_id,
            :name,
            :description,
            :resource_owners
      WHERE NOT EXISTS(SELECT 1 FROM resource_type_update);

-- name: delete-resource-type!
DELETE FROM ze_data.resource_type WHERE rt_id = :resource_type_id;

-- name: read-scopes
SELECT s_id,
       s_summary,
       s_description,
       s_user_information,
       s_is_resource_owner_scope,
       s_criticality_level
  FROM ze_data.scope
 WHERE s_resource_type_id = :resource_type_id
 ORDER BY s_id ASC;

-- name: read-scope
SELECT s_id,
       s_summary,
       s_description,
       s_user_information,
       s_is_resource_owner_scope,
       s_criticality_level
  FROM ze_data.scope
 WHERE s_resource_type_id = :resource_type_id
   AND s_id = :scope_id;

-- name: create-or-update-scope!
WITH scope_update AS (
     UPDATE ze_data.scope
        SET s_summary = :summary,
            s_description = :description,
            s_user_information = :user_information,
            s_is_resource_owner_scope = :is_resource_owner_scope
            s_criticality_level = :criticality_level
      WHERE s_resource_type_id = :resource_type_id
        AND s_id = :scope_id
  RETURNING *
)
INSERT INTO ze_data.scope (
            s_id,
            s_resource_type_id,
            s_summary,
            s_is_resource_owner_scope,
            s_description,
            s_user_information
            s_criticality_level)
     SELECT :scope_id,
            :resource_type_id,
            :summary,
            :is_resource_owner_scope,
            :description,
            :user_information
            :criticality_level

      WHERE NOT EXISTS(SELECT 1 FROM scope_update);

-- name: delete-scope!
DELETE FROM ze_data.scope
      WHERE s_id = :scope_id
        AND s_resource_type_id = :resource_type_id;
