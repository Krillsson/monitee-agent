CREATE TABLE MetricSeriesBucketEntity
(
    id          char(36)     NOT NULL,
    metric      VARCHAR(255) NOT NULL,
    itemId      VARCHAR(255) NOT NULL,
    resolution  VARCHAR(255) NOT NULL,
    bucketStart datetime     NOT NULL,
    samples     INT          NOT NULL,
    minValue    DOUBLE       NOT NULL,
    avgValue    DOUBLE       NOT NULL,
    maxValue    DOUBLE       NOT NULL,
    lastValue   DOUBLE       NOT NULL,
    CONSTRAINT pk_metricseriesbucketentity PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS MetricSeriesBucketBySeriesResolutionAndStart
    ON MetricSeriesBucketEntity (metric, itemId, resolution, bucketStart);
