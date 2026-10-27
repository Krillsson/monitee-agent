ALTER TABLE CheckResultEntity
    ADD COLUMN certificateExpiresAt datetime NULL;

ALTER TABLE CheckResultEntity
    ADD COLUMN certificateIssuedAt datetime NULL;

ALTER TABLE CheckResultEntity
    ADD COLUMN certificateIssuer TEXT NULL;

ALTER TABLE CheckResultEntity
    ADD COLUMN certificateSubject TEXT NULL;
