package io.pleo.antaeus.core.schedulers

import io.mockk.impl.annotations.MockK
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.runnables.PayInvoices
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.db.createDb
import io.pleo.antaeus.models.Invoice
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.random.Random


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayInvoicesSchedulerTest {

    @MockK
    private val db = createDb("test", false)
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

    private val payInvoicesScheduler :
            PayInvoicesScheduler = PayInvoicesScheduler(
                PayInvoices(
                    billingService = billingService,
                    customerService = customerService,
                    invoiceService = invoiceService
                )
            )


    @Test
    fun `test schedule single task`() {
        println("Executing service ... ")

        /*
        payInvoicesScheduler.scheduledTask(
            PayInvoicesRunnable(Runnable {
                println("In execution")
                Thread.sleep(1000)
                println("Task executed")
                Thread.sleep(1000)
                println("Task completed")
            }
            ) {
                println("Callback called")
            }
        )
        */


        println("Function returned")
        Thread.sleep(10000)
    }


}