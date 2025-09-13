-- backend/src/test/resources/test-data.sql
-- Test data setup for integration tests

-- Ensure test tenant exists
INSERT INTO tenant (id, name, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Test Tenant', NOW())
ON DUPLICATE KEY UPDATE name = name;

-- Clean up any existing test data
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@example.com');
DELETE FROM users WHERE email LIKE '%@example.com';
DELETE FROM case_workflow WHERE tenant_id = '00000000-0000-0000-0000-000000000001';
DELETE FROM invoice WHERE tenant_id = '00000000-0000-0000-0000-000000000001';
DELETE FROM vendor WHERE tenant_id = '00000000-0000-0000-0000-000000000001';

-- Insert test vendor
INSERT INTO vendor (id, tenant_id, name, email_domain, current_bank_last4) VALUES
    ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', 'Test Vendor Corp', 'test.com', '1234');

-- The test user will be created dynamically in the test setup