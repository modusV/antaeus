package io.pleo.antaeus.core.services

import io.mockk.impl.annotations.MockK
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.db.createDb
import io.pleo.antaeus.models.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoicePaymentServiceTest {

    @MockK
    private val db = createDb("test_payment", false)

    private val invoiceService = InvoiceService(dal = db)

    private val customerService = CustomerService(dal = db)

    private val billingService = BillingService(object : PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            val choice = (0..10).random()
            if (choice < 6) {
                return Random.nextBoolean()
            } else {
                throw NetworkException()
            }
        }
    })

    @MockK
    private val invoicePaymentService = InvoicePaymentService(
        invoiceService = invoiceService,
        customerService = customerService,
        billingService = billingService
    )

    @BeforeEach
    fun init() {

        invoiceService.deleteAll()
        customerService.deleteAll()

        val customers = (1..3).mapNotNull {
            customerService.create(
                currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
            )
        }

        customers.forEach { customer ->
            (1..5).forEach {
                invoiceService.create(
                    amount = Money(
                        value = BigDecimal(it),
                        currency = customer.currency
                    ),
                    customer = customer,
                    status = if (it <= 3) InvoiceStatus.PENDING else InvoiceStatus.PAID
                )
            }
        }
    }


    @Test
    fun `pay all pending no errors`() {
        runBlocking {
            println("Start paying ...")
            val res = GlobalScope.async {
                invoicePaymentService.performAllPaymentsByStatus()
            }

            val failed = res.await()
            println(failed)
            println("Finished paying ${failed.size} requests")

            assertEquals(
                failed.filter { it.second == InvoiceStatus.FAILED_NETWORK }.size,
                invoiceService.fetchByStatus(InvoiceStatus.FAILED_NETWORK).size
            )

            assertEquals(invoiceService.fetchByStatus(InvoiceStatus.PENDING).size, 0)
        }
    }



}