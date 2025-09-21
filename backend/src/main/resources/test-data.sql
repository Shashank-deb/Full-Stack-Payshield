-- Test data for integration tests (H2 friendly, idempotent)

-- Ensure test tenant exists (H2 compatible MERGE)
MERGE INTO tenant (id, name, created_at) 
KEY(id) 
VALUES ('00000000-0000-0000-0000-000000000001', 'Test Tenant', CURRENT_TIMESTAMP);

-- Clean up any existing test rows (order matters for FKs)
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@example.com');
DELETE FROM mfa_backup_codes WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@example.com');
DELETE FROM mfa_trusted_devices WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@example.com');
DELETE FROM mfa_auth_attempts WHERE email LIKE '%@example.com';
DELETE FROM users WHERE email LIKE '%@example.com';
DELETE FROM case_workflow WHERE tenant_id = '00000000-0000-0000-0000-000000000001';
DELETE FROM invoice WHERE tenant_id = '00000000-0000-0000-0000-000000000001';
DELETE FROM vendor WHERE tenant_id = '00000000-0000-0000-0000-000000000001';

-- Insert test vendor (H2 compatible MERGE)
MERGE INTO vendor(id, tenant_id, name, email_domain, current_bank_last4)
KEY(id)
VALUES ('11111111-1111-1111-1111-111111111111',
        '00000000-0000-0000-0000-000000000001',
        'Test Vendor Corp',
        'test.com',
        '1234');
