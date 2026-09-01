-- Purely a derived cache the daily FileSystemSpaceForecastRecorder repopulates
-- (including once at boot), so dropping and recreating it loses nothing durable.
DROP TABLE FileSystemSpaceForecastEntity;

CREATE TABLE FileSystemSpaceForecastEntity (
    filesystemId VARCHAR(255) PRIMARY KEY,
    computedAt datetime NOT NULL,
    trend VARCHAR(255) NOT NULL,
    growthBytesPerDay DOUBLE NOT NULL,
    daysUntilFull DOUBLE,
    daysUntilFullLow DOUBLE,
    daysUntilFullHigh DOUBLE,
    projectedFullDate datetime,
    daysOfHistoryUsed DOUBLE NOT NULL,
    history TEXT
);
