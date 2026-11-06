DROP INDEX IF EXISTS MetricSeriesBucketBySeriesResolutionAndStart;

CREATE UNIQUE INDEX IF NOT EXISTS MetricSeriesBucketByResolutionSeriesAndStart
    ON MetricSeriesBucketEntity (resolution, metric, itemId, bucketStart);

CREATE INDEX IF NOT EXISTS MetricSeriesBucketByResolutionAndStart
    ON MetricSeriesBucketEntity (resolution, bucketStart);
