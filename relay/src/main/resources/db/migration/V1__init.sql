-- 중계 서버 스키마. 운행·비용 테이블은 없다 (원본은 차량 Room).
CREATE TABLE organizations (org_id UUID PRIMARY KEY, name TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT now());
CREATE TABLE admins (admin_id UUID PRIMARY KEY, org_id UUID REFERENCES organizations, email TEXT UNIQUE, password_hash TEXT);
CREATE TABLE vehicles (vehicle_id UUID PRIMARY KEY, org_id UUID, plate_no TEXT, model TEXT, vehicle_code TEXT UNIQUE,
  device_token_hash TEXT, registered_at TIMESTAMPTZ, last_inbox_at TIMESTAMPTZ);
CREATE TABLE connect_codes (code CHAR(6) PRIMARY KEY, vehicle_id UUID, expires_at TIMESTAMPTZ, used_at TIMESTAMPTZ);
CREATE TABLE qr_tokens (token TEXT PRIMARY KEY, vehicle_id UUID, expires_at TIMESTAMPTZ, used_at TIMESTAMPTZ);
CREATE TABLE users (user_id UUID PRIMARY KEY, org_id UUID, name TEXT, dept TEXT, email TEXT);
CREATE TABLE places (place_id UUID PRIMARY KEY, org_id UUID, label TEXT, kind TEXT, lat DOUBLE PRECISION, lng DOUBLE PRECISION, radius_m INT DEFAULT 150, default_purpose TEXT);
CREATE TABLE schedules (schedule_id UUID PRIMARY KEY, org_id UUID, vehicle_id UUID, user_id UUID, starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, dest_place_id UUID, purpose_category TEXT, purpose_detail TEXT);
CREATE TABLE recipients (id BIGSERIAL PRIMARY KEY, org_id UUID, email TEXT, name TEXT);
CREATE TABLE receipts (receipt_id UUID PRIMARY KEY, vehicle_id UUID, sha256 CHAR(64), storage_key TEXT, thumb_key TEXT, taken_at TIMESTAMPTZ,
  ocr_status TEXT, ocr_fields JSONB, ocr_confidence JSONB, arith_ok BOOLEAN, created_at TIMESTAMPTZ DEFAULT now(), expires_at TIMESTAMPTZ);
CREATE UNIQUE INDEX ON receipts (vehicle_id, sha256);
CREATE TABLE inbox (id BIGSERIAL PRIMARY KEY, vehicle_id UUID, kind TEXT, payload JSONB, created_at TIMESTAMPTZ DEFAULT now(), expires_at TIMESTAMPTZ);
CREATE INDEX ON inbox (vehicle_id, created_at);
