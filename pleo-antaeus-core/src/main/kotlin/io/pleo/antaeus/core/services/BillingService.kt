package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice

class BillingService(
    private val paymentProvider: PaymentProvider
) {
// TODO - Add code e.g. here

    fun payInvoice(invoice : Invoice) : Boolean {
        return paymentProvider.charge(invoice)
    }

}
