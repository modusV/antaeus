package io.pleo.antaeus.core.recurring

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.*
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.lang.Runnable
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class PaymentScheduler (
        private val billingService : BillingService,
        private val customerService: CustomerService,
        private val invoiceService: InvoiceService
    ) {

    /**
    override fun run() {
        // time perform Payments here
        logger.debug { "Thread has run" }
    }
    */

    fun payInvoice(invoice : Invoice) : Pair<Int, InvoiceStatus> {
        try {
            invoiceService.updateStatusById(invoice.id, InvoiceStatus.PAYING)
            val res = billingService.payInvoice(invoice)
            return if (res) {
                invoiceService.updateStatusById(invoice.id, InvoiceStatus.PAID)
                Pair(invoice.id, InvoiceStatus.PAID)
            } else {
                invoiceService.updateStatusById(invoice.id, InvoiceStatus.FAILED_EMPTY_ACCOUNT)
                Pair(invoice.id, InvoiceStatus.FAILED_EMPTY_ACCOUNT)
            }
        }
        catch (e: NetworkException) {
            invoiceService.updateStatusById(invoice.id, InvoiceStatus.FAILED_NETWORK)
            return Pair(invoice.id, InvoiceStatus.FAILED_NETWORK)
        }
        catch (e: CurrencyMismatchException) {
            invoiceService.updateStatusById(invoice.id, InvoiceStatus.FAILED_CURRENCY_MISMATCH)
            return Pair(invoice.id, InvoiceStatus.FAILED_CURRENCY_MISMATCH)
        }
        catch (e: CustomerNotFoundException) {
            invoiceService.updateStatusById(invoice.id, InvoiceStatus.FAILED_CUSTOMER_NOT_FOUND)
            return Pair(invoice.id, InvoiceStatus.FAILED_CUSTOMER_NOT_FOUND)
        }
    }


    suspend fun performPayments() : List<Pair<Int, InvoiceStatus>> {
        val toPay : List<Invoice> = invoiceService.fetchByStatus(InvoiceStatus.PENDING)
        val scope = CoroutineScope(Dispatchers.IO)

        val jobs =
            toPay.map { invoice ->
                scope.async {
                    payInvoice(invoice)
                }
            }

        val results = jobs.awaitAll()
        scope.cancel()
        return results
    }


    /**
     * Use the BillingService to pay the invoices that are due.
     * In case some of them fail due to network errors, reschedule
     * this function to run again in t time.
     */
    suspend fun syncroTest(many: Int) {
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val status =
            (1..many)
                .map {
                    scope.async {
                        try {
                            val res = billingService.payInvoice(
                                Invoice(
                                    10, 1,
                                    Money(BigDecimal.ONE, Currency.GBP),
                                    InvoiceStatus.PENDING
                                )
                            )
                            println("Result of $it is $res")
                            0
                        }
                        catch (e : NetworkException) {
                            println("Network Exception")
                            1
                        }
                    }
                }

        println("${status.awaitAll().sum()} requests failed due to network problems")
        println("All payed")
        scope.cancel()
    }

    suspend fun test(v : Int) {
        delay(1000L)
        println("test $v completed")
    }


    init {
        /*
            Get time distance from here to next of the month and schedule
            the performPayments function.
        */
    }
}