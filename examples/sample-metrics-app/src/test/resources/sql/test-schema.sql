CREATE SCHEMA IF NOT EXISTS business;
CREATE SCHEMA IF NOT EXISTS workflow;

CREATE TABLE IF NOT EXISTS business.transaction_view (
    client_code     VARCHAR(32) NOT NULL,
    document_status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow.payment_view (
    payment_state VARCHAR(32) NOT NULL
);
