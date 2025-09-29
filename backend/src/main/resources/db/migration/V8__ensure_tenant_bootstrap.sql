-- ==============================================================================
-- V8: Critical Fix - Ensure Tenant Bootstrap Works
-- File: backend/src/main/resources/db/migration/V8__ensure_tenant_bootstrap.sql
-- ==============================================================================

-- Ensure tenant table has all required columns
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Update any existing tenants to have created_at if NULL
UPDATE tenant SET created_at = NOW() WHERE created_at IS NULL;

-- Ensure the default tenant exists (this is CRITICAL for bootstrap)
INSERT INTO tenant (id, name, created_at)
VALUES (
           '00000000-0000-0000-0000-000000000001'::uuid,
           'Default Tenant',
           NOW()
       )
ON CONFLICT (id) DO UPDATE SET
    created_at = COALESCE(tenant.created_at, NOW());

-- Add a check constraint to ensure tenant always has created_at
ALTER TABLE tenant ADD CONSTRAINT check_tenant_created_at
    CHECK (created_at IS NOT NULL);

-- Create a function to auto-insert default tenant if needed
CREATE OR REPLACE FUNCTION ensure_default_tenant()
    RETURNS TRIGGER AS $$
BEGIN
    -- If inserting a user and default tenant doesn't exist, create it
    IF NOT EXISTS (SELECT 1 FROM tenant WHERE id = '00000000-0000-0000-0000-000000000001'::uuid) THEN
        INSERT INTO tenant (id, name, created_at)
        VALUES (
                   '00000000-0000-0000-0000-000000000001'::uuid,
                   'Default Tenant',
                   NOW()
               );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to ensure default tenant exists before user insert
DROP TRIGGER IF EXISTS trigger_ensure_default_tenant ON users;
CREATE TRIGGER trigger_ensure_default_tenant
    BEFORE INSERT ON users
    FOR EACH ROW
EXECUTE FUNCTION ensure_default_tenant();

-- Add helpful comments
COMMENT ON TABLE tenant IS 'Multi-tenant isolation - each organization has a unique tenant';
COMMENT ON COLUMN tenant.created_at IS 'When this tenant was created in the system';
COMMENT ON FUNCTION ensure_default_tenant() IS 'Auto-creates default tenant if missing during user insertion';