CREATE SCHEMA IF NOT EXISTS documents;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS telemetry;

CREATE TABLE IF NOT EXISTS documents.transaction_view (
    client_code     VARCHAR(32) NOT NULL,
    document_status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS payments.payment_view (
    payment_state VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS telemetry.shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at  TIMESTAMP NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

INSERT INTO documents.transaction_view (client_code, document_status) VALUES ('A', 'CREATED');
INSERT INTO documents.transaction_view (client_code, document_status) VALUES ('A', 'CREATED');
INSERT INTO documents.transaction_view (client_code, document_status) VALUES ('B', 'SENT');

INSERT INTO payments.payment_view (payment_state) VALUES ('NEW');
INSERT INTO payments.payment_view (payment_state) VALUES ('NEW');
INSERT INTO payments.payment_view (payment_state) VALUES ('PAID');
