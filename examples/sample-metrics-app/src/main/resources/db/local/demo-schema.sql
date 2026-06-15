-- Только для локального H2 (профиль local). Схема ShedLock должна совпадать с app.metrics-lock.schema.
CREATE SCHEMA IF NOT EXISTS business;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS telemetry;

CREATE TABLE IF NOT EXISTS business.transaction_view (
    client_code     VARCHAR(32) NOT NULL,
    document_status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow.payment_view (
    payment_state VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS telemetry.shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at  TIMESTAMP NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

INSERT INTO business.transaction_view (client_code, document_status) VALUES ('A', 'CREATED');
INSERT INTO business.transaction_view (client_code, document_status) VALUES ('A', 'CREATED');
INSERT INTO business.transaction_view (client_code, document_status) VALUES ('B', 'SENT');

INSERT INTO workflow.payment_view (payment_state) VALUES ('NEW');
INSERT INTO workflow.payment_view (payment_state) VALUES ('NEW');
INSERT INTO workflow.payment_view (payment_state) VALUES ('PAID');
