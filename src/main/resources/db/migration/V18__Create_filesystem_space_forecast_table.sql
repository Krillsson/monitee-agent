CREATE TABLE FileSystemSpaceForecastEntity (
    filesystemId VARCHAR(255) PRIMARY KEY,
    computedAt datetime NOT NULL,
    growthBytesPerDay DOUBLE NOT NULL,
    daysUntilFull DOUBLE NOT NULL,
    daysUntilFullLow DOUBLE NOT NULL,
    daysUntilFullHigh DOUBLE NOT NULL,
    projectedFullDate datetime NOT NULL,
    daysOfHistoryUsed DOUBLE NOT NULL,
    history TEXT
);
