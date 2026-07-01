DROP TABLE IF EXISTS GpuLoad;

CREATE TABLE GpuLoad
(
    id             char(36)     NOT NULL,
    historyId      char(36)     NULL,
    deviceId       VARCHAR(255) NULL,
    name           VARCHAR(255) NULL,
    coreLoad       DOUBLE       NOT NULL,
    vramUsedBytes  BIGINT       NOT NULL,
    vramTotalBytes BIGINT       NOT NULL,
    temperature    DOUBLE       NOT NULL,
    fanPercent     DOUBLE       NOT NULL,
    powerDraw      DOUBLE       NOT NULL,
    coreClockMhz   BIGINT       NOT NULL,
    memoryClockMhz BIGINT       NOT NULL,
    CONSTRAINT pk_gpuload PRIMARY KEY (id),
    CONSTRAINT FK_GPULOAD_ON_HISTORYID FOREIGN KEY (historyId) REFERENCES HistorySystemLoadEntity (id)
);
