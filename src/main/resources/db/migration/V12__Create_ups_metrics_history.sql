-- Flyway migration for UPS metrics history (SQLite, matching container history datatypes)
CREATE TABLE UpsMetricsHistory (
    id char(36) PRIMARY KEY,
    upsId VARCHAR(255) NOT NULL,
    timestamp datetime NOT NULL,
    batteryCapacity DOUBLE,
    batteryChargePercent BIGINT,
    batteryRuntimeSeconds BIGINT,
    batteryVoltage DOUBLE,
    batteryVoltageNominal DOUBLE,
    batteryChargerStatus VARCHAR(255),
    inputCurrent DOUBLE,
    inputFrequency DOUBLE,
    inputVoltage DOUBLE,
    outputCurrent DOUBLE,
    outputFrequency DOUBLE,
    outputPowerFactor DOUBLE,
    outputVoltage DOUBLE,
    loadPercent BIGINT,
    realPowerLoadWatts BIGINT,
    powerLoadVA BIGINT,
    upsStatus VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS upsMetricsHistoryIndex ON upsMetricsHistory (upsId, timestamp);
