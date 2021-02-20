package io.pleo.antaeus.models

enum class InvoiceStatus {
    PENDING,
    PAID,
    PAYING,
    FAILED_NETWORK,
    FAILED_EMPTY_ACCOUNT,
    FAILED_CUSTOMER_NOT_FOUND,
    FAILED_CURRENCY_MISMATCH
}
