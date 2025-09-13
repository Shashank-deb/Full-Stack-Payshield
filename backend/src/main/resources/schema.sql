-- backend/src/test/resources/schema.sql
-- H2 Database Schema for Tests

-- Enable H2 compatibility mode
SET MODE PostgreSQL;

-- Create tenant table
CREATE TABLE IF NOT EXISTS tenant (
                                      id UUID PRIMARY KEY,
                                      name TEXT NOT NULL UNIQUE,
                                      created_at TIMESTAMP DEFAULT NOW()
);

-- Create users table
CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     email VARCHAR(320) NOT NULL UNIQUE,
                                     password_hash VARCHAR(100) NOT NULL,
                                     tenant_id UUID NOT NULL,
                                     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                     mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                     mfa_enforced BOOLEAN NOT NULL DEFAULT FALSE,
                                     last_mfa_setup_at TIMESTAMP,
                                     mfa_backup_codes_count INTEGER NOT NULL DEFAULT 0,
                                     FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

-- Create user_roles table
CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id UUID NOT NULL,
                                          role VARCHAR(32) NOT NULL,
                                          PRIMARY KEY (user_id, role),
                                          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create vendor table
CREATE TABLE IF NOT EXISTS vendor (
                                      id UUID PRIMARY KEY,
                                      tenant_id UUID NOT NULL,
                                      name TEXT NOT NULL,
                                      email_domain TEXT,
                                      current_bank_last4 TEXT,
                                      UNIQUE(tenant_id, name),
                                      FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

-- Create invoice table
CREATE TABLE IF NOT EXISTS invoice (
                                       id UUID PRIMARY KEY,
                                       tenant_id UUID NOT NULL,
                                       vendor_id UUID NOT NULL,
                                       received_at TIMESTAMP NOT NULL DEFAULT NOW(),
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
                                       FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
                                       FOREIGN KEY (vendor_id) REFERENCES vendor(id) ON DELETE CASCADE
);

-- Create case_workflow table
CREATE TABLE IF NOT EXISTS case_workflow (
                                             id UUID PRIMARY KEY,
                                             tenant_id UUID NOT NULL,
                                             invoice_id UUID NOT NULL,
                                             state TEXT NOT NULL,
                                             created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                             FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
                                             FOREIGN KEY (invoice_id) REFERENCES invoice(id) ON DELETE CASCADE
);

-- Create outbox table
CREATE TABLE IF NOT EXISTS outbox (
                                      event_id UUID PRIMARY KEY,
                                      tenant_id UUID NOT NULL,
                                      type TEXT NOT NULL,
                                      payload_json TEXT NOT NULL,
                                      occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                      processed_at TIMESTAMP,
                                      FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

-- MFA Tables
CREATE TABLE IF NOT EXISTS mfa_configuration (
                                                 user_id UUID PRIMARY KEY,
                                                 tenant_id UUID NOT NULL,
                                                 encrypted_secret TEXT,
                                                 secret_hash VARCHAR(64),
                                                 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                                 is_setup_complete BOOLEAN NOT NULL DEFAULT FALSE,
                                                 setup_completed_at TIMESTAMP,
                                                 last_used_at TIMESTAMP,
                                                 failed_attempts INTEGER NOT NULL DEFAULT 0,
                                                 locked_until TIMESTAMP,
                                                 backup_codes_remaining INTEGER NOT NULL DEFAULT 0,
                                                 backup_codes_generated_at TIMESTAMP,
                                                 encryption_key_version INTEGER NOT NULL DEFAULT 1,
                                                 created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                                 updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                                 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                                 FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mfa_backup_codes (
                                                id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
                                                user_id UUID NOT NULL,
                                                tenant_id UUID NOT NULL,
                                                encrypted_code TEXT NOT NULL,
                                                code_hash VARCHAR(64) NOT NULL,
                                                is_used BOOLEAN NOT NULL DEFAULT FALSE,
                                                used_at TIMESTAMP,
                                                used_from_ip VARCHAR(45),
                                                encryption_key_version INTEGER NOT NULL DEFAULT 1,
                                                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                                FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mfa_trusted_devices (
                                                   id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
                                                   user_id UUID NOT NULL,
                                                   tenant_id UUID NOT NULL,
                                                   device_fingerprint VARCHAR(128) NOT NULL,
                                                   device_name VARCHAR(100),
                                                   user_agent TEXT,
                                                   ip_address VARCHAR(45),
                                                   location VARCHAR(100),
                                                   is_trusted BOOLEAN NOT NULL DEFAULT TRUE,
                                                   trusted_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                                   expires_at TIMESTAMP,
                                                   last_seen_at TIMESTAMP,
                                                   revoked_at TIMESTAMP,
                                                   revoked_by UUID,
                                                   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                                   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                                   FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mfa_auth_attempts (
                                                 id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
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
                                                 attempted_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                                 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
                                                 FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE SET NULL
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_vendor_tenant ON vendor(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoice_tenant ON invoice(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoice_iban_hash ON invoice(tenant_id, bank_iban_hash);
CREATE INDEX IF NOT EXISTS idx_case_tenant ON case_workflow(tenant_id);
CREATE INDEX IF NOT EXISTS idx_mfa_backup_codes_hash ON mfa_backup_codes(code_hash);

-- Insert test data
INSERT INTO tenant (id, name, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Test Tenant', NOW())
ON DUPLICATE KEY UPDATE name = name;