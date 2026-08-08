CREATE TABLE CheckEntity
(
    id                      char(36)     NOT NULL,
    type                    VARCHAR(255) NOT NULL,
    name                    VARCHAR(255) NULL,
    enabled                 BOOLEAN      NOT NULL,
    intervalSeconds         INT          NOT NULL,
    timeoutSeconds          INT          NOT NULL,
    url                     VARCHAR(255) NULL,
    method                  VARCHAR(255) NULL,
    expectedStatusCodes     VARCHAR(255) NULL,
    keyword                 VARCHAR(255) NULL,
    keywordInverted         BOOLEAN      NOT NULL,
    ignoreCertificateErrors BOOLEAN      NOT NULL,
    followRedirects         BOOLEAN      NOT NULL,
    headers                 TEXT         NULL,
    CONSTRAINT pk_checkentity PRIMARY KEY (id)
);

INSERT INTO CheckEntity (id, type, name, enabled, intervalSeconds, timeoutSeconds, url, method,
                         expectedStatusCodes, keyword, keywordInverted, ignoreCertificateErrors,
                         followRedirects, headers)
SELECT id, 'HTTP', NULL, 1, 30, 20, url, 'GET', '200-299', NULL, 0, 0, 1, NULL
FROM WebserverCheckEntity;

DROP TABLE WebserverCheckEntity;

CREATE TABLE CheckResultEntity
(
    id           char(36)     NOT NULL,
    checkId      char(36)     NOT NULL,
    checkType    VARCHAR(255) NOT NULL,
    timestamp    datetime     NOT NULL,
    successful   BOOLEAN      NOT NULL,
    latencyMs    BIGINT       NOT NULL,
    message      VARCHAR(255) NOT NULL,
    responseCode INT          NULL,
    errorBody    VARCHAR(255) NULL,
    CONSTRAINT pk_checkresultentity PRIMARY KEY (id)
);

INSERT INTO CheckResultEntity (id, checkId, checkType, timestamp, successful, latencyMs, message,
                               responseCode, errorBody)
SELECT id,
       webserverCheckId,
       'HTTP',
       timestamp,
       CASE WHEN responseCode BETWEEN 200 AND 299 THEN 1 ELSE 0 END,
       latencyMs,
       message,
       responseCode,
       errorBody
FROM WebserverCheckHistoryEntity;

DROP TABLE WebserverCheckHistoryEntity;

CREATE INDEX IF NOT EXISTS CheckResultByCheckAndTimestamp ON CheckResultEntity (checkId, timestamp);

CREATE TABLE CheckResultBucketEntity
(
    id              char(36)     NOT NULL,
    checkId         char(36)     NOT NULL,
    resolution      VARCHAR(255) NOT NULL,
    bucketStart     datetime     NOT NULL,
    samples         INT          NOT NULL,
    successful      INT          NOT NULL,
    failed          INT          NOT NULL,
    downtimeSeconds BIGINT       NOT NULL,
    minLatencyMs    BIGINT       NOT NULL,
    avgLatencyMs    BIGINT       NOT NULL,
    maxLatencyMs    BIGINT       NOT NULL,
    p95LatencyMs    BIGINT       NOT NULL,
    lastMessage     VARCHAR(255) NULL,
    CONSTRAINT pk_checkresultbucketentity PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS CheckResultBucketByCheckResolutionAndStart
    ON CheckResultBucketEntity (checkId, resolution, bucketStart);
