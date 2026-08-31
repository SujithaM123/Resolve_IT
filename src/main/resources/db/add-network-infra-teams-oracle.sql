-- ResolveIT - expand the service catalogue to five teams (Oracle)
--
-- seed-data-oracle.sql only runs against EMPTY RESOLVE_ tables. This script is the
-- incremental version for a database that is already seeded and running.
--
-- Run once, connected as the application's database user:
--   sqlplus -S -L 'USER/PASSWORD@//host:1521/SERVICE' @add-network-infra-teams-oracle.sql
--
-- What it does:
--   1. Rewrites the DESCRIPTION of the three existing teams so each one carries the
--      keywords ClassificationService matches incident text against. Only the wording
--      of the description changes - team_id, team_name and service_name are untouched,
--      so every existing incident, assignment and user keeps pointing at the same row.
--   2. Adds Network Support Team and Infrastructure Support Team.
--
-- Safe to re-run: the inserts are guarded by NOT EXISTS and the updates are idempotent.
-- No row is ever deleted.
--
-- Skip this script entirely on a fresh database - seed-data-oracle.sql already contains
-- all five teams.

-- 1. Keyword vocabularies for the three original teams. -----------------------------
UPDATE RESOLVE_TEAM_SERVICE
SET description = 'Handles payment, payments, refund, refunds, transaction, transactions, '
                || 'checkout, billing, charge, charges, card, invoice, purchase, order, '
                || 'declined and failed payment reports'
WHERE UPPER(service_name) = 'PAYMENT SERVICE';

UPDATE RESOLVE_TEAM_SERVICE
SET description = 'Handles login, signin, signon, authentication, authenticate, password, '
                || 'passwords, account, accounts, access, session, sessions, credential, '
                || 'credentials, otp, mfa, locked and unauthorized reports'
WHERE UPPER(service_name) = 'LOGIN SERVICE';

UPDATE RESOLVE_TEAM_SERVICE
SET description = 'Handles email, emails, mail, sms, push, notification, notifications, '
                || 'alert, alerts, reminder, digest, inbox, delivery, delivered, '
                || 'undelivered and received reports'
WHERE UPPER(service_name) = 'NOTIFICATION SERVICE';

-- 2. The two new teams. --------------------------------------------------------------
INSERT INTO RESOLVE_TEAM_SERVICE (team_name, service_name, department, description)
SELECT 'Network Support Team',
       'Network Service',
       'Platform',
       'Handles network, vpn, zscaler, zscalar, connectivity, connection, connections, '
       || 'connect, disconnected, disconnection, dropping, dropped, internet, wifi, '
       || 'ethernet, lan, wan, dns, firewall, proxy, gateway, bandwidth, unreachable, '
       || 'unstable and packet loss reports'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM RESOLVE_TEAM_SERVICE WHERE UPPER(service_name) = 'NETWORK SERVICE');

INSERT INTO RESOLVE_TEAM_SERVICE (team_name, service_name, department, description)
SELECT 'Infrastructure Support Team',
       'Infrastructure Service',
       'Platform',
       'Handles server, servers, database, databases, cpu, memory, ram, disk, storage, '
       || 'deployment, deploy, release, cluster, node, container, kubernetes, docker, '
       || 'infrastructure, hardware, capacity, restart, rebooted, crashed and outage reports'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM RESOLVE_TEAM_SERVICE WHERE UPPER(service_name) = 'INFRASTRUCTURE SERVICE');

COMMIT;

-- Verify: five rows, each with a keyword description.
SELECT team_id, team_name, service_name FROM RESOLVE_TEAM_SERVICE ORDER BY team_id;
