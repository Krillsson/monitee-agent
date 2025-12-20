-- SQLite-compatible migration: add SmartData snapshot columns to DiskLoad
-- One column per ALTER TABLE statement (SQLite limitation)

-- Discriminator
ALTER TABLE DiskLoad ADD COLUMN deviceType VARCHAR(16);

-- Common
ALTER TABLE DiskLoad ADD COLUMN temperatureCelsius INT;
ALTER TABLE DiskLoad ADD COLUMN powerOnHours BIGINT;
ALTER TABLE DiskLoad ADD COLUMN powerCycleCount BIGINT;

-- HDD
ALTER TABLE DiskLoad ADD COLUMN hddReallocatedSectors BIGINT;
ALTER TABLE DiskLoad ADD COLUMN hddPendingSectors BIGINT;
ALTER TABLE DiskLoad ADD COLUMN hddUncorrectableSectors BIGINT;
ALTER TABLE DiskLoad ADD COLUMN hddOfflineUncorrectable BIGINT;
ALTER TABLE DiskLoad ADD COLUMN hddSpinRetryCount BIGINT;
ALTER TABLE DiskLoad ADD COLUMN hddSeekErrorRate BIGINT;
ALTER TABLE DiskLoad ADD COLUMN hddUdmaCrcErrors BIGINT;

-- SATA SSD
ALTER TABLE DiskLoad ADD COLUMN ssdPercentageUsed INT;
ALTER TABLE DiskLoad ADD COLUMN ssdWearLevelingCount INT;
ALTER TABLE DiskLoad ADD COLUMN ssdAvailableReservedSpace INT;
ALTER TABLE DiskLoad ADD COLUMN ssdTotalWriteGib BIGINT;
ALTER TABLE DiskLoad ADD COLUMN ssdTotalReadGib BIGINT;
ALTER TABLE DiskLoad ADD COLUMN ssdMediaErrors BIGINT;
ALTER TABLE DiskLoad ADD COLUMN ssdUncorrectableErrors BIGINT;
ALTER TABLE DiskLoad ADD COLUMN ssdUdmaCrcErrors BIGINT;

-- NVMe
ALTER TABLE DiskLoad ADD COLUMN nvmePercentageUsed INT;
ALTER TABLE DiskLoad ADD COLUMN nvmeDataUnitsRead BIGINT;
ALTER TABLE DiskLoad ADD COLUMN nvmeDataUnitsWritten BIGINT;
ALTER TABLE DiskLoad ADD COLUMN nvmeMediaErrors BIGINT;
ALTER TABLE DiskLoad ADD COLUMN nvmeNumErrLogEntries BIGINT;
ALTER TABLE DiskLoad ADD COLUMN nvmeUnsafeShutdowns BIGINT;
ALTER TABLE DiskLoad ADD COLUMN nvmeControllerBusyTimeMinutes BIGINT;

ALTER TABLE DiskLoad ADD COLUMN healthStatus TEXT;
ALTER TABLE DiskLoad ADD COLUMN healthMessages TEXT;

-- Optional helpful index on DiskLoad(historyId) if not already present (SQLite supports CREATE INDEX IF NOT EXISTS)
-- CREATE INDEX IF NOT EXISTS idx_diskload_historyId ON DiskLoad(historyId);
