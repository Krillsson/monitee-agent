ALTER TABLE CheckEntity
    ADD COLUMN dnsHostname VARCHAR(255) NULL;

ALTER TABLE CheckEntity
    ADD COLUMN dnsResolver VARCHAR(255) NULL;

ALTER TABLE CheckEntity
    ADD COLUMN dnsRecordType VARCHAR(255) NULL;

ALTER TABLE CheckEntity
    ADD COLUMN dnsExpectedValues TEXT NULL;

ALTER TABLE CheckResultEntity
    ADD COLUMN resolvedValues TEXT NULL;
