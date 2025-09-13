-- backend/src/test/resources/schema.sql
-- H2 Database Schema for Tests

CREATE TABLE IF NOT EXISTS tenant (
                                      id UUID PRIMARY KEY,
                                      name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     email VARCHAR(320) NOT NULL UNIQUE,
                                     password_hash VARCHAR(100) NOT NULL,
                                     tenant_id UUID NOT NULL,
                                     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                     mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                     mfa_enforced BOOLEAN NOT NULL DEFAULT FALSE,
                                     last_mfa_setup_at TIMESTAMP,
                                     mfa_backup_codes_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id UUID NOT NULL,
                                          role VARCHAR(32) NOT NULL,
                                          PRIMARY KEY (user_id, role)
);

CREATE TABLE IF NOT EXISTS vendor (
                                      id UUID PRIMARY KEY,
                                      tenant_id UUID NOT NULL,
                                      name TEXT NOT NULL,
                                      email_domain TEXT,
                                      current_bank_last4 TEXT,
                                      UNIQUE(tenant_id, name)
);

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
                                       file_sha256 TEXT
);

CREATE TABLE IF NOT EXISTS case_workflow (
                                             id UUID PRIMARY KEY,
                                             tenant_id UUID NOT NULL,
                                             invoice_id UUID NOT NULL,
                                             state TEXT NOT NULL,
                                             created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS outbox (
                                      event_id UUID PRIMARY KEY,
                                      tenant_id UUID NOT NULL,
                                      type TEXT NOT NULL,
                                      payload_json TEXT NOT NULL,
                                      occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                      processed_at TIMESTAMP
);

-- Insert test tenant
INSERT INTO tenant (id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Test Tenant')
ON DUPLICATE KEY UPDATE name = name;