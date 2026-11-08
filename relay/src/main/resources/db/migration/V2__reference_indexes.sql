-- 관리자 기준 정보(F-51~54) 조회·중복 방지용 인덱스.
CREATE UNIQUE INDEX recipients_org_email ON recipients (org_id, lower(email));
CREATE INDEX schedules_vehicle_time ON schedules (vehicle_id, starts_at);
CREATE INDEX users_org ON users (org_id);
CREATE INDEX places_org ON places (org_id);
