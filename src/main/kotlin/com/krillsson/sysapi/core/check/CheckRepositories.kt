package com.krillsson.sysapi.core.check

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface CheckRepository : JpaRepository<CheckEntity, UUID>

@Repository
interface CheckResultRepository : JpaRepository<CheckResultEntity, UUID> {
    fun findFirstByCheckIdOrderByTimestampDesc(checkId: UUID): Optional<CheckResultEntity>

    fun findFirstByCheckIdOrderByTimestampAsc(checkId: UUID): Optional<CheckResultEntity>

    fun findFirstByCheckIdAndTimestampLessThanOrderByTimestampDesc(
        checkId: UUID,
        before: Instant
    ): Optional<CheckResultEntity>

    fun findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
        checkId: UUID,
        from: Instant,
        to: Instant
    ): List<CheckResultEntity>

    @Query("select distinct r.checkId from CheckResultEntity r")
    fun findDistinctCheckIds(): List<UUID>

    @Modifying
    @Query("delete from CheckResultEntity r where r.checkId = :checkId")
    fun deleteAllByCheckId(@Param("checkId") checkId: UUID): Int

    @Modifying
    @Query("delete from CheckResultEntity r where r.timestamp < :before")
    fun deleteOlderThan(@Param("before") before: Instant): Int
}

@Repository
interface CheckResultBucketRepository : JpaRepository<CheckResultBucketEntity, UUID> {
    fun findByCheckIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        checkId: UUID,
        resolution: BucketResolution,
        from: Instant,
        to: Instant
    ): List<CheckResultBucketEntity>

    fun findByCheckIdAndResolutionAndBucketStartIn(
        checkId: UUID,
        resolution: BucketResolution,
        bucketStarts: Collection<Instant>
    ): List<CheckResultBucketEntity>

    fun findFirstByCheckIdAndResolutionOrderByBucketStartDesc(
        checkId: UUID,
        resolution: BucketResolution
    ): Optional<CheckResultBucketEntity>

    @Query("select distinct b.checkId from CheckResultBucketEntity b where b.resolution = :resolution")
    fun findDistinctCheckIds(@Param("resolution") resolution: BucketResolution): List<UUID>

    @Modifying
    @Query("delete from CheckResultBucketEntity b where b.checkId = :checkId")
    fun deleteAllByCheckId(@Param("checkId") checkId: UUID): Int

    @Modifying
    @Query("delete from CheckResultBucketEntity b where b.resolution = :resolution and b.bucketStart < :before")
    fun deleteOlderThan(@Param("resolution") resolution: BucketResolution, @Param("before") before: Instant): Int
}
