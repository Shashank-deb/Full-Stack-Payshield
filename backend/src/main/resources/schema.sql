SET MODE=PostgreSQL;

-- Create tenant table
CREATE TABLE IF NOT EXISTS tenant (
                                      id UUID PRIMARY KEY,
                                      name TEXT NOT NULL UNIQUE,
                                      created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create users table
CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     email VARCHAR(320) NOT NULL UNIQUE,
                                     password_hash VARCHAR(100) NOT NULL,
                                     tenant_id UUID NOT NULL,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                     mfa_enforced BOOLEAN NOT NULL DEFAULT FALSE,
                                     last_mfa_setup_at TIMESTAMPTZ,
                                     mfa_backup_codes_count INTEGER NOT NULL DEFAULT 0,
                                     CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

-- Create user_roles table
CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id UUID NOT NULL,
                                          role VARCHAR(32) NOT NULL,
                                          PRIMARY KEY (user_id, role),
                                          CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create vendor table
CREATE TABLE IF NOT EXISTS vendor (
                                      id UUID PRIMARY KEY,
                                      tenant_id UUID NOT NULL,
                                      name TEXT NOT NULL,
                                      email_domain TEXT,
                                      current_bank_last4 TEXT,
                                      email_domain_encrypted TEXT,
                                      current_bank_last4_encrypted TEXT,
                                      CONSTRAINT uk_vendor_tenant_name UNIQUE(tenant_id, name),
                                      CONSTRAINT fk_vendor_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

-- Create invoice table
CREATE TABLE IF NOT EXISTS invoice (
                                       id UUID PRIMARY KEY,
                                       tenant_id UUID NOT NULL,
                                       vendor_id UUID NOT NULL,
                                       received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       amount DECIMAL(18,2),
                                       currency VARCHAR(3),
                                       bank_iban TEXT,
                                       bank_swift TEXT,
                                       bank_last4 TEXT,
                                       bank_iban_encrypted TEXT,
                                       bank_swift_encrypted TEXT,
                                       bank_iban_hash VARCHAR(64),
                                       source_message_id TEXT,
                                       file_sha256 TEXT,
                                       CONSTRAINT fk_invoice_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
                                       CONSTRAINT fk_invoice_vendor FOREIGN KEY (vendor_id) REFERENCES vendor(id) ON DELETE CASCADE
);

-- Create case_workflow table
CREATE TABLE IF NOT EXISTS case_workflow (
                                             id UUID PRIMARY KEY,
                                             tenant_id UUID NOT NULL,
                                             invoice_id UUID NOT NULL,
                                             state TEXT NOT NULL,
                                             created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             CONSTRAINT fk_case_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
                                             CONSTRAINT fk_case_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id) ON DELETE CASCADE
);

-- Create outbox table
CREATE TABLE IF NOT EXISTS outbox (
                                      event_id UUID PRIMARY KEY,
                                      tenant_id UUID NOT NULL,
                                      type TEXT NOT NULL,
                                      payload_json TEXT NOT NULL,
                                      occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      processed_at TIMESTAMPTZ,
                                      CONSTRAINT fk_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

-- Create encryption_metadata table
CREATE TABLE IF NOT EXISTS encryption_metadata (
                                                   id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                                                   key_version INTEGER NOT NULL DEFAULT 1,
                                                   algorithm VARCHAR(50) NOT NULL DEFAULT 'AES-256-GCM',
                                                   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   retired_at TIMESTAMPTZ,
                                                   is_active BOOLEAN NOT NULL DEFAULT TRUE,
                                                   CONSTRAINT uk_encryption_key_version UNIQUE(key_version)
);

-- Create encryption_audit table
CREATE TABLE IF NOT EXISTS encryption_audit (
                                                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                                                tenant_id UUID NOT NULL,
                                                table_name VARCHAR(50) NOT NULL,
                                                record_id UUID NOT NULL,
                                                field_name VARCHAR(50) NOT NULL,
                                                operation VARCHAR(20) NOT NULL,
                                                key_version INTEGER NOT NULL,
                                                performed_by VARCHAR(255) NOT NULL,
                                                performed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- MFA Tables
CREATE TABLE IF NOT EXISTS mfa_configuration (
                                                 user_id UUID PRIMARY KEY,
                                                 tenant_id UUID NOT NULL,
                                                 encrypted_secret TEXT,
                                                 secret_hash VARCHAR(64),
                                                 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                                 is_setup_complete BOOLEAN NOT NULL DEFAULT FALSE,
                                                 setup_completed_at TIMESTAMPTZ,
                                                 last_used_at TIMESTAMPTZ,
                                                 failed_attempts INTEGER NOT NULL DEFAULT 0,
                                                 locked_until TIMESTAMPTZ,
                                                 backup_codes_remaining INTEGER NOT NULL DEFAULT 0,
                                                 backup_codes_generated_at TIMESTAMPTZ,
                                                 encryption_key_version INTEGER NOT NULL DEFAULT 1,
                                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 CONSTRAINT fk_mfa_config_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                                 CONSTRAINT fk_mfa_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mfa_backup_codes (
                                                id UUID PRIMARY KEY DEFAULT UUID_RANDOM(),
                                                user_id UUID NOT NULL,
                                                tenant_id UUID NOT NULL,
                                                encrypted_code TEXT NOT NULL,
                                                code_hash VARCHAR(64) NOT NULL,
                                                is_used BOOLEAN NOT NULL DEFAULT FALSE,
                                                used_at TIMESTAMPTZ,
                                                used_from_ip VARCHAR(45),
                                                encryption_key_version INTEGER NOT NULL DEFAULT 1,
                                                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                CONSTRAINT fk_mfa_backup_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                                CONSTRAINT fk_mfa_backup_codes_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mfa_trusted_devices (
                                                   id UUID PRIMARY KEY DEFAULT UUID_RANDOM(),
                                                   user_id UUID NOT NULL,
                                                   tenant_id UUID NOT NULL,
                                                   device_fingerprint VARCHAR(128) NOT NULL,
                                                   device_name VARCHAR(100),
                                                   user_agent TEXT,
                                                   ip_address VARCHAR(45),
                                                   location VARCHAR(100),
                                                   is_trusted BOOLEAN NOT NULL DEFAULT TRUE,
                                                   trusted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   expires_at TIMESTAMPTZ,
                                                   last_seen_at TIMESTAMPTZ,
                                                   revoked_at TIMESTAMPTZ,
                                                   revoked_by UUID,
                                                   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   CONSTRAINT fk_mfa_trusted_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                                   CONSTRAINT fk_mfa_trusted_devices_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mfa_auth_attempts (
                                                 id UUID PRIMARY KEY DEFAULT UUID_RANDOM(),
                                                 user_id UUID,
                                                 tenant_id UUID,
                                                 email VARCHAR(320),
                                                 attempt_type VARCHAR(20) NOT NULL,
                                                 success BOOLEAN NOT NULL,
                                                 provided_code VARCHAR(20),
                                                 ip_address VARCHAR(45),
                                                 user_agent TEXT,
                                                 location VARCHAR(100),
                                                 failure_reason VARCHAR(100),
                                                 device_fingerprint VARCHAR(128),
                                                 is_trusted_device BOOLEAN DEFAULT FALSE,
                                                 attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 CONSTRAINT fk_mfa_auth_attempts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
                                                 CONSTRAINT fk_mfa_auth_attempts_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE SET NULL
);

-- Indexes (avoid DESC in H2 index defs)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_vendor_tenant ON vendor(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoice_tenant ON invoice(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoice_iban_hash ON invoice(tenant_id, bank_iban_hash);
CREATE INDEX IF NOT EXISTS idx_case_tenant ON case_workflow(tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbox_processed ON outbox(processed_at);
CREATE INDEX IF NOT EXISTS idx_mfa_backup_codes_hash ON mfa_backup_codes(code_hash);
CREATE INDEX IF NOT EXISTS idx_mfa_backup_codes_user ON mfa_backup_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_trusted_devices_user ON mfa_trusted_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_trusted_devices_fingerprint ON mfa_trusted_devices(device_fingerprint);
CREATE INDEX IF NOT EXISTS idx_mfa_auth_attempts_user ON mfa_auth_attempts(user_id);
CREATE INDEX IF NOT EXISTS idx_encryption_audit_tenant_time ON encryption_audit(tenant_id, performed_at);

-- Seed default tenant (idempotent)
MERGE INTO tenant (id, name, created_at)
    KEY (id)
VALUES ('00000000-0000-0000-0000-000000000001', 'Test Tenant', CURRENT_TIMESTAMP);

-- Initial encryption metadata (idempotent)
MERGE INTO encryption_metadata (key_version, algorithm, is_active, created_at)
    KEY (key_version)
VALUES (1, 'AES-256-GCM', TRUE, CURRENT_TIMESTAMP);
