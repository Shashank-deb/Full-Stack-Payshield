-- ==============================================================================
-- V7: Fix Tenant Creation and Bootstrap Issues
-- File: backend/src/main/resources/db/migration/V7__fix_tenant_creation.sql
-- ==============================================================================

-- First, update the tenant table to add created_at column if missing
ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Ensure the default tenant exists with proper timestamp
INSERT INTO tenant (id, name, created_at)
VALUES (
           '00000000-0000-0000-0000-000000000001'::uuid,
           'Default Tenant',
           NOW()
       )
ON CONFLICT (id) DO UPDATE SET
                               name = EXCLUDED.name,
                               created_at = COALESCE(tenant.created_at, NOW());

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_tenant_created_at ON tenant(created_at);

-- Drop and recreate foreign key constraints to ensure they're properly set
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_tenant;
ALTER TABLE vendor DROP CONSTRAINT IF EXISTS fk_vendor_tenant;
ALTER TABLE invoice DROP CONSTRAINT IF EXISTS fk_invoice_tenant;
ALTER TABLE case_workflow DROP CONSTRAINT IF EXISTS fk_case_tenant;

-- Recreate with proper CASCADE behavior
ALTER TABLE users
    ADD CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE;

ALTER TABLE vendor
    ADD CONSTRAINT fk_vendor_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE;

ALTER TABLE invoice
    ADD CONSTRAINT fk_invoice_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE;

ALTER TABLE case_workflow
    ADD CONSTRAINT fk_case_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE;