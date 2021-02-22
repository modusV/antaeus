package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.models.*
import kotlinx.coroutines.*


class InvoicePaymentService (
        private val billingService : BillingService,
        private val customerService: CustomerService,
        private val invoiceService: InvoiceService
    ) {


    /**
     * Pay a single [invoice]. Returns a pair representing the invoice id and the billing outcome
     */
    private fun payInvoice(invoice : Invoice) : Pair<Int, InvoiceStatus> {
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


    /**
     * Use the BillingService to pay all the invoices that are due.
     * In case some of them fail due to network errors, reschedule
     * this function to run again in t time.
     */
    suspend fun performAllPaymentsByStatus(status : InvoiceStatus = InvoiceStatus.PENDING) : List<Pair<Int, InvoiceStatus>> {
        val toPay : List<Invoice> = invoiceService.fetchByStatus(status)
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

}