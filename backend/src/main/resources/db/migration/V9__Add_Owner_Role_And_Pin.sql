-- 1. Agregar la nueva columna security_pin
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS security_pin TEXT;

-- 2. Eliminar el constraint antiguo de roles y crear el nuevo admitiendo OWNER
-- PostgreSQL requires a DO block to dynamically drop a constraint if it wasn't named explicitly
DO $$
    DECLARE
        rec RECORD;
    BEGIN
        FOR rec IN
            SELECT oid, conname
            FROM pg_constraint
            WHERE conrelid = 'usuarios'::regclass AND contype = 'c'
            LOOP
                -- Look for the check constraint that involves the 'rol' column
                IF pg_get_constraintdef(rec.oid) ILIKE '%rol%' THEN
                    EXECUTE 'ALTER TABLE usuarios DROP CONSTRAINT ' || rec.conname;
                END IF;
            END LOOP;
    END $$;

ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('ADMIN', 'EMPLEADO', 'OWNER'));

-- 3. Asegurar que los IDs del sistema (0) y el administrador original (1) tengan rol ADMIN
UPDATE usuarios SET rol = 'ADMIN' WHERE id IN (0, 1);
