package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice

class BillingService(
    private val paymentProvider: PaymentProvider
) {

    /**
     * Uses the payment provider API to charge a customer
     */
    fun payInvoice(invoice : Invoice) : Boolean {
        return paymentProvider.charge(invoice)
    }

}
