package io.pleo.antaeus.core.schedulers


import io.pleo.antaeus.core.runnables.PayInvoices
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class PayInvoicesScheduler (
        private val payInvoices: PayInvoices
    ) {

    //private val client : RedissonClient = Redisson.create()
    //private val executor = client.getExecutorService("job_executor")
    /**
     * A single thread is enough at the moment, more can be added if
     * more recurring services are needed
     */
    private val executor = Executors.newSingleThreadScheduledExecutor()


    private fun millisToNextMonth() : Long {
        val now = ZonedDateTime.now()
        val res = now
            .withDayOfMonth(1)
            .plusMonths(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)//.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

        return now.until(res, ChronoUnit.MILLIS)
    }

    private fun payAll () : Runnable {
        return Runnable {
            val res = runBlocking {
                payInvoices.performAllPaymentsByStatus()
            }

            // Here I can manage the results however I want
            println(res)

            // reschedule task
            scheduleMonthly()
        }
    }


    fun scheduleMonthly() {
        executor.schedule(
            payAll(),
            millisToNextMonth(),
            TimeUnit.MILLISECONDS
        )
    }


}