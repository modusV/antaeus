package io.pleo.antaeus.core.services

import io.mockk.impl.annotations.MockK
import io.pleo.antaeus.db.createDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingServiceTest {

    @MockK
    private val db = createDb("test", false)

}