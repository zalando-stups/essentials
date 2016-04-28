ALTER TABLE ze_data.scope
 ADD COLUMN s_criticality_level SMALLINT DEFAULT 2 CHECK (s_criticality_level >= 1 AND
                                                          s_criticality_level <= 3);
