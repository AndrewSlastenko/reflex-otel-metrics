CREATE SCHEMA IF NOT EXISTS documents;
CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE IF NOT EXISTS documents.transaction_view (
    client_code     VARCHAR(32) NOT NULL,
    document_status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS payments.payment_view (
    payment_state VARCHAR(32) NOT NULL
);
