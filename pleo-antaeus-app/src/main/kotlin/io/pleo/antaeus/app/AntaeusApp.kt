/*
    Defines the main() entry point of the app.
    Configures the database and sets up the REST web service.
 */

@file:JvmName("AntaeusApp")

package io.pleo.antaeus.app

import getPaymentProvider
import io.pleo.antaeus.core.schedulers.InvoicePaymentScheduler
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoicePaymentService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.db.createDb
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.rest.AntaeusRest
import kotlinx.coroutines.runBlocking
import setupInitialData
import java.time.temporal.ChronoUnit


fun main() {

    val dal = createDb()

    // Insert example data in the database.
    setupInitialData(dal = dal)

    // Get third parties
    val paymentProvider = getPaymentProvider()

    // Create core services
    val invoiceService = InvoiceService(dal = dal)
    val customerService = CustomerService(dal = dal)

    // This is _your_ billing service to be included where you see fit
    val billingService = BillingService(paymentProvider = paymentProvider)

    val invoicePaymentService = InvoicePaymentService(
        invoiceService = invoiceService,
        billingService = billingService,
        customerService = customerService
    )

    val invoicePaymentScheduler = InvoicePaymentScheduler(invoicePaymentService)

    // To assess the crash case, at startup reschedule all the requests failed due to network problems.

    runBlocking {
        invoicePaymentScheduler.schedulePaymentsWithDelay(
            status = InvoiceStatus.FAILED_NETWORK,
            periodic = false,
            quantity = 0,
            unit = ChronoUnit.SECONDS
        )
    }

    /**
     * Here we should start also a job that checks for PAYING requests.
     * If there are any, we are recovering from a crash event.
     *
     *
    runBlocking {
    invoicePaymentScheduler.schedulePaymentsWithDelay(
    status = InvoiceStatus.PAYING,
    periodic = false,
    quantity = 0,
    unit = ChronoUnit.SECONDS
    )
    }
     */

    // Starts the monthly job schedule. If the app crashes, the task is scheduled again at startup.
    invoicePaymentScheduler.schedulePaymentsWithDelay()


    // Create REST web service
    AntaeusRest(
        invoiceService = invoiceService,
        customerService = customerService,
        invoicePaymentScheduler = invoicePaymentScheduler
    ).run()
}
