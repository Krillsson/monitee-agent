ALTER TABLE CpuLoad ADD COLUMN oneMinutes DOUBLE NOT NULL DEFAULT 0.0;
ALTER TABLE CpuLoad ADD COLUMN fiveMinutes DOUBLE NOT NULL DEFAULT 0.0;
ALTER TABLE CpuLoad ADD COLUMN fifteenMinutes DOUBLE NOT NULL DEFAULT 0.0;