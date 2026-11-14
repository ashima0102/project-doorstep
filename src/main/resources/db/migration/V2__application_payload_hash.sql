-- Idempotency for redelivered events: application_id is already the primary key (the unique
-- constraint that makes a second insert fail); payload_hash tells an exact redelivery from a conflict.
alter table loan_application add column payload_hash varchar(64);
