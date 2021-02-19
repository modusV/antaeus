package io.pleo.antaeus.core.services

import io.mockk.impl.annotations.MockK
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.db.createDb
import io.pleo.antaeus.models.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceServiceTest {

    @MockK
    private val db = createDb("test", false)

    private val invoiceService = InvoiceService(dal = db)

    @BeforeEach
    fun init() {

        invoiceService.deleteAll()

        invoiceService
            .create(
                Money(BigDecimal(10), Currency.EUR),
                Customer(0, Currency.EUR),
                InvoiceStatus.PENDING
            )

        invoiceService
            .create(
                Money(BigDecimal(20), Currency.GBP),
                Customer(1, Currency.GBP),
                InvoiceStatus.PAID
            )
    }


    @Test
    fun `will throw if invoice is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }


    @Test
    fun `will throw if fetchByStatus fails`() {
        val pending : List<Invoice> = invoiceService.fetchByStatus(InvoiceStatus.PENDING)
        assertEquals(pending.size, 1)
        assertEquals(pending[0].customerId, 0)
        assertEquals(pending[0].status, InvoiceStatus.PENDING)
    }
}
