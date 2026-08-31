-- ResolveIT - reference seed data (Oracle)
--
-- Loads the RESOLVE_ tables only. It never writes to any table that is not
-- prefixed RESOLVE_, so the shared tables in this schema are not affected.
--
-- Run AFTER schema-oracle.sql, and only against empty RESOLVE_ tables:
--   sqlplus -S -L 'USER/PASSWORD@//host:1521/SERVICE' @seed-data-oracle.sql
--
-- The three RESOLVE_ROLE rows are REQUIRED - AuthService looks the USER role up
-- on every registration and the SUPPORT role up whenever a SUPER_ADMIN creates a
-- support engineer through POST /api/support-users. Everything after them is demo
-- data, but the historical incidents are what give OpsAI something to compare against.
--
-- Every account below has the password password123, except the SUPER_ADMIN in
-- section 3a, whose password is admin123. Only BCrypt hashes are stored here -
-- no plain-text password is ever written to the database.

-- 1. Roles. Exactly three; no fourth application role may be added.
--    SUPER_ADMIN exists only to provision SUPPORT accounts - it reports no
--    incidents and is never auto-assigned any work.
INSERT INTO RESOLVE_ROLE (role_name) VALUES ('USER');
INSERT INTO RESOLVE_ROLE (role_name) VALUES ('SUPPORT');
INSERT INTO RESOLVE_ROLE (role_name) VALUES ('SUPER_ADMIN');

-- 2. Teams and the service each one owns. INCIDENT.team_id is resolved from
--    the service name the reporter confirms, so service_name values must be
--    the strings the classifier and the client will send.
--    The DESCRIPTION of each row is the keyword vocabulary ClassificationService
--    matches incident text against, so adding a word here teaches the classifier.
INSERT INTO RESOLVE_TEAM_SERVICE (team_name, service_name, department, description)
VALUES ('Payment Support Team', 'Payment Service', 'Commerce',
        'Handles payment, payments, refund, refunds, transaction, transactions, '
        || 'checkout, billing, charge, charges, card, invoice, purchase, order, '
        || 'declined and failed payment reports');

INSERT INTO RESOLVE_TEAM_SERVICE (team_name, service_name, department, description)
VALUES ('Identity Support Team', 'Login Service', 'Platform',
        'Handles login, signin, signon, authentication, authenticate, password, '
        || 'passwords, account, accounts, access, session, sessions, credential, '
        || 'credentials, otp, mfa, locked and unauthorized reports');

INSERT INTO RESOLVE_TEAM_SERVICE (team_name, service_name, department, description)
VALUES ('Notification Support Team', 'Notification Service', 'Platform',
        'Handles email, emails, mail, sms, push, notification, notifications, '
        || 'alert, alerts, reminder, digest, inbox, delivery, delivered, '
        || 'undelivered and received reports');

INSERT INTO RESOLVE_TEAM_SERVICE (team_name, service_name, department, description)
VALUES ('Network Support Team', 'Network Service', 'Platform',
        'Handles network, vpn, zscaler, zscalar, connectivity, connection, connections, '
        || 'connect, disconnected, disconnection, dropping, dropped, internet, wifi, '
        || 'ethernet, lan, wan, dns, firewall, proxy, gateway, bandwidth, unreachable, '
        || 'unstable and packet loss reports');

INSERT INTO RESOLVE_TEAM_SERVICE (team_name, service_name, department, description)
VALUES ('Infrastructure Support Team', 'Infrastructure Service', 'Platform',
        'Handles server, servers, database, databases, cpu, memory, ram, disk, storage, '
        || 'deployment, deploy, release, cluster, node, container, kubernetes, docker, '
        || 'infrastructure, hardware, capacity, restart, rebooted, crashed and outage reports');

-- 3. Reporting users.
INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
VALUES ('Arjun', 'user@example.com',
        '$2a$10$sUDflG3MqRQMUEbZHZ6ScOG/5QL/uBtk5D8SSAAUwLzlnU258V4FS',
        (SELECT role_id FROM RESOLVE_ROLE WHERE role_name = 'USER'), NULL);

INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
VALUES ('Meera', 'meera@example.com',
        '$2a$10$b6DcumNDXuFrGpFFAupa2OjVOI0axJwgzizVafN0utbouhwcgITFO',
        (SELECT role_id FROM RESOLVE_ROLE WHERE role_name = 'USER'), NULL);

-- 3a. Super admin. The only account allowed to call POST /api/support-users.
--     Password is admin123, stored here purely as its BCrypt hash. It owns no
--     team, because it never handles incidents.
INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
VALUES ('Super Admin', 'admin@resolve.com',
        '$2a$10$bcQupgyhbTjdCqQWCxn7c.7gQffnqLRFwrjbmWg73ZuFgEVkN5V8m',
        (SELECT role_id FROM RESOLVE_ROLE WHERE role_name = 'SUPER_ADMIN'), NULL);

-- 4. Support engineers. Each belongs to the team that owns their service,
--    which is what makes them eligible for automatic assignment.
INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
VALUES ('Priya', 'support@example.com',
        '$2a$10$yx/ZlOHMtVfQlJ.XjUfd1.WycxN1opfZmjUUna7IUbPw8Yv5izXCy',
        (SELECT role_id FROM RESOLVE_ROLE WHERE role_name = 'SUPPORT'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Payment Service'));

INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
VALUES ('Rahul', 'rahul@example.com',
        '$2a$10$RjfiGwZm1XNWxX8Ynr2ygOrCIti6F239uH0piEy1SbEny68y4XgDy',
        (SELECT role_id FROM RESOLVE_ROLE WHERE role_name = 'SUPPORT'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Payment Service'));

INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
VALUES ('Kavya', 'kavya@example.com',
        '$2a$10$1xI8ODI1pUtOizHsD.0h9O5iPEAF7oLUZygmROlvjgi7MuaPd3Qp2',
        (SELECT role_id FROM RESOLVE_ROLE WHERE role_name = 'SUPPORT'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Login Service'));

INSERT INTO RESOLVE_USER (name, email, password_hash, role_id, team_id)
VALUES ('Dev', 'dev@example.com',
        '$2a$10$VZnyVpQ6emHDSaEubz6jS.FQSgMeOH57Ft6p9sxDlyrpKqoO5Vhzm',
        (SELECT role_id FROM RESOLVE_ROLE WHERE role_name = 'SUPPORT'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Notification Service'));

-- 5. Resolved historical incidents. These are what give OpsAI something to
--    learn from and what makes similar-incident experience scoring meaningful
--    on a fresh database. Without history, every engineer scores neutrally.
INSERT INTO RESOLVE_INCIDENT (incident_code, title, description, category, severity, priority, status,
                      reported_by, team_id, root_cause, resolution, created_at, resolved_at)
VALUES ('INC-0651', 'Payment Failure', 'Payment fails after clicking the Pay button at checkout.',
        'Payment Failure', 'HIGH', 'P1', 'RESOLVED',
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'user@example.com'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Payment Service'),
        'Database connection exhaustion',
        'Connection pool was increased and payment transactions were verified.',
        SYSTIMESTAMP - 30, SYSTIMESTAMP - 30 + INTERVAL '4' HOUR);

INSERT INTO RESOLVE_INCIDENT (incident_code, title, description, category, severity, priority, status,
                      reported_by, team_id, root_cause, resolution, created_at, resolved_at)
VALUES ('INC-0742', 'Payment Failure', 'Payment declined repeatedly during checkout.',
        'Payment Failure', 'HIGH', 'P1', 'RESOLVED',
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'meera@example.com'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Payment Service'),
        'Database connection exhaustion',
        'Check active database connections. Increase the connection pool. Verify payment transactions.',
        SYSTIMESTAMP - 20, SYSTIMESTAMP - 20 + INTERVAL '3' HOUR);

INSERT INTO RESOLVE_INCIDENT (incident_code, title, description, category, severity, priority, status,
                      reported_by, team_id, root_cause, resolution, created_at, resolved_at)
VALUES ('INC-0812', 'Payment Failure', 'Payment is failing after clicking Pay, three attempts failed.',
        'Payment Failure', 'HIGH', 'P1', 'RESOLVED',
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'user@example.com'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Payment Service'),
        'Database connection exhaustion',
        'Payment service was restarted after the pool was resized. Transactions verified successfully.',
        SYSTIMESTAMP - 10, SYSTIMESTAMP - 10 + INTERVAL '2' HOUR);

INSERT INTO RESOLVE_INCIDENT (incident_code, title, description, category, severity, priority, status,
                      reported_by, team_id, root_cause, resolution, created_at, resolved_at)
VALUES ('INC-0900', 'Login Issue', 'Unable to log in, credentials are rejected.',
        'Login Issue', 'MEDIUM', 'P3', 'RESOLVED',
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'meera@example.com'),
        (SELECT team_id FROM RESOLVE_TEAM_SERVICE WHERE service_name = 'Login Service'),
        'Session token expiry misconfiguration',
        'Token lifetime was corrected and affected sessions were cleared.',
        SYSTIMESTAMP - 15, SYSTIMESTAMP - 15 + INTERVAL '5' HOUR);

-- 6. Assignment history. This is what the experience and idle-time factors
--    read, so the three payment incidents are credited to Priya.
INSERT INTO RESOLVE_INCIDENT_ASSIGNMENT (incident_id, support_user_id, assignment_score, assigned_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0651'),
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'support@example.com'), 88.50, SYSTIMESTAMP - 30);

INSERT INTO RESOLVE_INCIDENT_ASSIGNMENT (incident_id, support_user_id, assignment_score, assigned_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0742'),
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'support@example.com'), 91.25, SYSTIMESTAMP - 20);

INSERT INTO RESOLVE_INCIDENT_ASSIGNMENT (incident_id, support_user_id, assignment_score, assigned_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'support@example.com'), 92.00, SYSTIMESTAMP - 10);

INSERT INTO RESOLVE_INCIDENT_ASSIGNMENT (incident_id, support_user_id, assignment_score, assigned_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0900'),
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'kavya@example.com'), 84.00, SYSTIMESTAMP - 15);

-- 7. A short historical conversation, so OpsAI SUMMARIZE has real input.
INSERT INTO RESOLVE_INCIDENT_MESSAGE (incident_id, sender_id, message_text, sent_at, is_read)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'user@example.com'),
        'Payment is failing again.', SYSTIMESTAMP - 10, 1);

INSERT INTO RESOLVE_INCIDENT_MESSAGE (incident_id, sender_id, message_text, sent_at, is_read)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'support@example.com'),
        'I am checking the issue now.', SYSTIMESTAMP - 10 + INTERVAL '5' MINUTE, 1);

INSERT INTO RESOLVE_INCIDENT_MESSAGE (incident_id, sender_id, message_text, sent_at, is_read)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        (SELECT user_id FROM RESOLVE_USER WHERE email = 'user@example.com'),
        'It happens right after clicking Pay.', SYSTIMESTAMP - 10 + INTERVAL '9' MINUTE, 1);

-- 8. Status history for one historical incident: one row per status held.
INSERT INTO RESOLVE_INCIDENT_LOGS (incident_id, status, changed_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        'REPORTED', SYSTIMESTAMP - 10);

INSERT INTO RESOLVE_INCIDENT_LOGS (incident_id, status, changed_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        'ASSIGNED', SYSTIMESTAMP - 10);

INSERT INTO RESOLVE_INCIDENT_LOGS (incident_id, status, changed_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        'IN PROGRESS', SYSTIMESTAMP - 10 + INTERVAL '10' MINUTE);

INSERT INTO RESOLVE_INCIDENT_LOGS (incident_id, status, changed_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        'ROOT CAUSE IDENTIFIED', SYSTIMESTAMP - 10 + INTERVAL '60' MINUTE);

INSERT INTO RESOLVE_INCIDENT_LOGS (incident_id, status, changed_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        'RESOLUTION IN PROGRESS', SYSTIMESTAMP - 10 + INTERVAL '90' MINUTE);

INSERT INTO RESOLVE_INCIDENT_LOGS (incident_id, status, changed_at)
VALUES ((SELECT incident_id FROM RESOLVE_INCIDENT WHERE incident_code = 'INC-0812'),
        'RESOLVED', SYSTIMESTAMP - 10 + INTERVAL '120' MINUTE);

COMMIT;
