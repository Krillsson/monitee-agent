package com.krillsson.sysapi.persistence

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

class ReadOnlyTransactionRoutingDataSourceTest {

    private val dataSource = ReadOnlyTransactionRoutingDataSource(
        writer = mockk<DataSource>(),
        reader = mockk<DataSource>()
    )

    @AfterEach
    fun tearDown() {
        TransactionSynchronizationManager.clear()
    }

    @ParameterizedTest
    @CsvSource(
        "true, true, READ",
        "true, false, WRITE",
        "false, false, WRITE",
        "false, true, WRITE"
    )
    fun `sends only read-only transactions to the readers`(
        transactionActive: Boolean,
        readOnly: Boolean,
        expected: DataSourceRoute
    ) {
        // Given
        TransactionSynchronizationManager.setActualTransactionActive(transactionActive)
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly)

        // When
        val route = dataSource.determineCurrentLookupKey()

        // Then
        route shouldBe expected
    }
}
