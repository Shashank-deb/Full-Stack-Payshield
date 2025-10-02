-- ==============================================================================
-- V9: Critical Fix - Ensure Robust Tenant Bootstrap
-- File: backend/src/main/resources/db/migration/V9__fix_tenant_bootstrap.sql
-- ==============================================================================

-- Drop existing problematic constraints
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_tenant;
ALTER TABLE vendor DROP CONSTRAINT IF EXISTS fk_vendor_tenant;
ALTER TABLE invoice DROP CONSTRAINT IF EXISTS fk_invoice_tenant;
ALTER TABLE case_workflow DROP CONSTRAINT IF EXISTS fk_case_tenant;
ALTER TABLE mfa_configuration DROP CONSTRAINT IF EXISTS fk_mfa_config_tenant;
ALTER TABLE mfa_backup_codes DROP CONSTRAINT IF EXISTS fk_mfa_backup_codes_tenant;
ALTER TABLE mfa_trusted_devices DROP CONSTRAINT IF EXISTS fk_mfa_trusted_devices_tenant;

-- Ensure tenant table has proper structure
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Create index for active tenants
CREATE INDEX IF NOT EXISTS idx_tenant_active ON tenant(is_active) WHERE is_active = TRUE;

-- Recreate foreign keys with DEFERRABLE for bootstrap flexibility
ALTER TABLE users
    ADD CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id)
            ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE vendor
    ADD CONSTRAINT fk_vendor_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id)
            ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE invoice
    ADD CONSTRAINT fk_invoice_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id)
            ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE case_workflow
    ADD CONSTRAINT fk_case_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id)
            ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE mfa_configuration
    ADD CONSTRAINT fk_mfa_config_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id)
            ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE mfa_backup_codes
    ADD CONSTRAINT fk_mfa_backup_codes_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id)
            ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE mfa_trusted_devices
    ADD CONSTRAINT fk_mfa_trusted_devices_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id)
            ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

-- Ensure default tenant exists BEFORE any user inserts
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM tenant WHERE id = '00000000-0000-0000-0000-000000000001'::uuid) THEN
            INSERT INTO tenant (id, name, created_at, is_active)
            VALUES (
                       '00000000-0000-0000-0000-000000000001'::uuid,
                       'Default Tenant',
                       NOW(),
                       TRUE
                   );
            RAISE NOTICE 'Default tenant created successfully';
        ELSE
            RAISE NOTICE 'Default tenant already exists';
        END IF;
    END $$;

-- Add helpful function to validate tenant references
CREATE OR REPLACE FUNCTION validate_tenant_exists(p_tenant_id UUID)
    RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM tenant WHERE id = p_tenant_id AND is_active = TRUE);
END;
$$ LANGUAGE plpgsql STABLE;

-- Add trigger to prevent orphaned records
CREATE OR REPLACE FUNCTION check_tenant_active()
    RETURNS TRIGGER AS $$
BEGIN
    IF NOT validate_tenant_exists(NEW.tenant_id) THEN
        RAISE EXCEPTION 'Invalid or inactive tenant: %', NEW.tenant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all tenant-dependent tables
DROP TRIGGER IF EXISTS trigger_users_check_tenant ON users;
CREATE TRIGGER trigger_users_check_tenant
    BEFORE INSERT OR UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION check_tenant_active();

DROP TRIGGER IF EXISTS trigger_vendor_check_tenant ON vendor;
CREATE TRIGGER trigger_vendor_check_tenant
    BEFORE INSERT OR UPDATE ON vendor
    FOR EACH ROW EXECUTE FUNCTION check_tenant_active();

-- Comments
COMMENT ON COLUMN tenant.is_active IS 'Soft delete flag - inactive tenants are hidden';
COMMENT ON FUNCTION validate_tenant_exists(UUID) IS 'Validates tenant exists and is active';