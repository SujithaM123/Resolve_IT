-- ResolveIT - add the SUPER_ADMIN role and its development account (Oracle)
--
-- seed-data-oracle.sql only runs against EMPTY RESOLVE_ tables. This script is
-- the incremental version for a database that is already seeded and running.
--
-- Run once, connected as the application's database user:
--   sqlplus -S -L 'USER/PASSWORD@//host:1521/SERVICE' @add-super-admin-oracle.sql
--
-- Touches only RESOLVE_ROLE and RESOLVE_USER, and only by INSERT. No existing
-- row is updated or deleted, so USER and SUPPORT accounts are unaffected.
-- Safe to re-run: both inserts are guarded by NOT EXISTS.
--
-- Skip this script entirely on a fresh database - seed-data-oracle.sql already
-- contains both rows.

-- 1. The SUPER_ADMIN role. AuthService resolves roles by name, case-insensitively.
INSERT INTO RESOLVE_ROLE (role_name)
SELECT 'SUPER_ADMIN' FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM RESOLVE_ROLE WHERE UPPER(role_name) = 'SUPER_ADMIN');

-- 2. The development super admin.
--    Email:    admin@resolve.com
--    Password: admin123
--    Stored below only as its BCrypt hash ($2a, cost 10), generated with the same
--    BCryptPasswordEncoder the application uses. The plain text is never stored.
--    team_id is NULL - a super admin never handles incidents.
INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
SELECT 'Super Admin',
       'admin@resolve.com',
       '$2a$10$bcQupgyhbTjdCqQWCxn7c.7gQffnqLRFwrjbmWg73ZuFgEVkN5V8m',
       (SELECT role_id FROM RESOLVE_ROLE WHERE UPPER(role_name) = 'SUPER_ADMIN'),
       NULL
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM RESOLVE_USER WHERE UPPER(email) = 'ADMIN@RESOLVE.COM');

COMMIT;

-- Verify: this must return exactly one row, with role_name = SUPER_ADMIN.
SELECT u.user_id, u.name, u.email, r.role_name
FROM RESOLVE_USER u
JOIN RESOLVE_ROLE r ON r.role_id = u.role_id
WHERE UPPER(u.email) = 'ADMIN@RESOLVE.COM';
