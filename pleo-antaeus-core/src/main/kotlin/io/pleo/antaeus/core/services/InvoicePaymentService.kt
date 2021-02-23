package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.models.*
import kotlinx.coroutines.*


class InvoicePaymentService (
        private val billingService : BillingService,
        // Included to deal with currency mismatch
        private val customerService: CustomerService,
        private val invoiceService: InvoiceService
    ) {


    /**
     * Pay a single [invoice]. Returns a pair representing the invoice id and the billing outcome.
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
        catch (e : Exception) {
            println(e)
            throw e
        }
    }


    /**
     * Use the BillingService to pay all the invoices that are due.
     * The operations run in the same thread, because SQLite is not thread-safe.
     * With a more advanced DB we could achieve row-level locks and perform all
     * operations concurrently, for example using the IO Dispatcher.
     * With a "real" payment provider API, we could transform the payInvoice function
     * to a suspend one, so that it can be put on hold while waiting for the
     * network response.
     */
    @ObsoleteCoroutinesApi
    suspend fun performAllPaymentsByStatus(
            status : InvoiceStatus = InvoiceStatus.PENDING)
            : List<Pair<Int, InvoiceStatus>> {

        val toPay : List<Invoice> = invoiceService.fetchByStatus(status)
        //val scope = CoroutineScope(Dispatchers.IO)
        val singleThread = newSingleThreadContext("context")

        val jobs =
            toPay.map { invoice ->
                withContext(singleThread) {
                    async {
                        payInvoice(invoice)
                    }
                }
            }

        return jobs.awaitAll()
    }

    /**
     * Example of function to deal with currency mismatch
     *
     *
        suspend fun manageCurrencyMismatch() : List<Pair<Int, InvoiceStatus>> {
            val toPay : List<Invoice> = invoiceService.fetchByStatus(InvoiceStatus.FAILED_CURRENCY_MISMATCH)
            val singleThread = newSingleThreadContext("context")

            val jobs = toPay.map { invoice ->
                try {
                    val customer: Customer = customerService.fetch(invoice.customerId)
                    withContext(singleThread) {
                        async {
                            // we could slightly modify the payInvoice function to receive an additional
                            // parameter representing the currency of choice (it would call the billingService
                            // bank's function charge(invoice, currency)
                            payInvoiceWithCurrency(invoice, customer.currency)
                        }
                    }
                }
                catch (e: CustomerNotFoundException) {
                    withContext(singleThread) {
                        async {
                            Pair(invoice.customerId, InvoiceStatus.FAILED_CUSTOMER_NOT_FOUND)
                        }
                    }
                }
            }

            val res = jobs.awaitAll()
            // If other CURRENCY_MISMATCH occurs, deal with them somehow, depending on the reasons
            // why this could occurr
            return res
        }
     *
     */


}