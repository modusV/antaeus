package io.pleo.antaeus.core.schedulers

import io.mockk.impl.annotations.MockK
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.InvoicePaymentService
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.db.createDb
import io.pleo.antaeus.models.Invoice
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch





@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoicePaymentSchedulerTest {

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

    private val invoicePaymentScheduler :
            InvoicePaymentScheduler = InvoicePaymentScheduler(
                InvoicePaymentService(
                    billingService = billingService,
                    customerService = customerService,
                    invoiceService = invoiceService
                )
            )

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var atomicInteger = AtomicInteger()

    @AfterAll
    private fun cleanup() {
        executor.shutdownNow()
    }

    /**
     * Helper function that simulates the payment step.
     */
    private fun testPeriodicDelay(
            delay :(Long) -> Long,
            quantity : Long,
            reschedule : Boolean) : ScheduledFuture<*> {
        return executor.schedule({
                runBlocking {
                    atomicInteger.incrementAndGet()
                    println("Executed at ${ZonedDateTime.now()}")
                }

                // reschedule task
                if (reschedule) {
                    testPeriodicDelay(delay, quantity, true)
                }
            },
            delay(quantity),
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Tests if single scheduling works.
     */
    @Test
    fun `fails if runs more than once` () {
        atomicInteger = AtomicInteger()
        invoicePaymentScheduler.schedulePaymentsWithDelay(
            quantity = 1,
            unit = ChronoUnit.SECONDS,
            periodic = false,
            function = ::testPeriodicDelay
        )
        Thread.sleep(3000)
        assert(atomicInteger.get() == 1)
    }


    /**
     * Tests if periodic scheduling function works.
     */
    @Test
    fun `fails if periodic scheduling has errors` () {
        atomicInteger = AtomicInteger()
        invoicePaymentScheduler.schedulePaymentsWithDelay(
            quantity = 1,
            unit = ChronoUnit.SECONDS,
            periodic = true,
            function = ::testPeriodicDelay
        )
        Thread.sleep(5000)
        assert((atomicInteger.get() == 4) or (atomicInteger.get() == 5))
    }

    /**
     * From here on, you can find several tests that I wrote for development purposes.
     * Test if executor runs jobs asynchronously.
     */
    @Test
    fun `executor should execute asynchronously`() {
        println("Executing service ... ")
        val testObj = object {
            var status :Int = 0
        }

        runBlocking {
            val fut = executor.schedule({
                    testObj.status = 1
                },
                1000,
                TimeUnit.MILLISECONDS
            )
            assertEquals(testObj.status, 0)
            fut.get()
            assertEquals(testObj.status, 1)
        }
        println("Function returned")
    }


    /**
     * Helper function to test multiple asynchronous calls.
     */
    private fun runAsync(
            task: Callable<Int>,
            delay: Long,
            times : Int,
            latch: CountDownLatch) : ScheduledFuture<*> {
        return executor.schedule(
             {
                val res = runBlocking {
                    task.call()
                }

                println("Has been called $res times and will be called other ${times - 1} times")

                if (times > 1) {
                    runAsync(task, delay, times - 1, latch)
                }
                latch.countDown()
            },
            delay,
            TimeUnit.MILLISECONDS
        )
    }


    /**
     * Tests multiple asynchronous call of the same function
     */
    @Test
    fun `fails if is not executed n times` () {

        val rescheduleTimes = 3
        val latch = CountDownLatch(rescheduleTimes)

        val testObj = object {
            var timesCalled : Int = 0
        }

        val step = Callable {
            testObj.timesCalled += 1
            testObj.timesCalled
        }

        runBlocking {
            runAsync(step, 1000, 3, latch).get()
        }


        latch.await(5L, TimeUnit.SECONDS)
        assertEquals(testObj.timesCalled, rescheduleTimes)
        println("Tasks completed")
    }


    /**
     * Tests if the executor can hold two scheduled tasks
     * (whether it has an internal queue or not)
     */
    @Test
    fun `two tasks single executor`() {
        val latch = CountDownLatch(2)

        (3..4).map {
            executor.schedule({
                    latch.countDown()
                    println("$it executed")
                },
                1000 * it.toLong(),
                TimeUnit.MILLISECONDS
            )
        }
        val res = latch.await(5L, TimeUnit.SECONDS)
        assertEquals(res, true)
    }

    /**
     * Tests how the executor behaves when it receives multiple tasks at the same time.
     */
    @Test
    fun `concurrent tasks` () {
        val latch = CountDownLatch(5)
        (1..5).map {
            executor.schedule({
                    latch.countDown()
                    println("$it executed")
                },
                1000,
                TimeUnit.MILLISECONDS
            )
        }
        val res = latch.await(3L, TimeUnit.SECONDS)
        assertEquals(res, true)
    }

}