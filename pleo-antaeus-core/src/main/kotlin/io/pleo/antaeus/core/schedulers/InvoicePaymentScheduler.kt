package io.pleo.antaeus.core.schedulers


import io.pleo.antaeus.core.services.InvoicePaymentService
import kotlinx.coroutines.runBlocking
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class InvoicePaymentScheduler (
        private val invoicePaymentService: InvoicePaymentService
    ) {

    /**
     * A single thread is enough at the moment, more can be added if
     * more recurring services are needed
     */
    private var executor : ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()


    private fun schedulePeriodicWithDelay(
            delay : (Long) -> Long,
            quantity: Long,
            reschedule: Boolean) : ScheduledFuture<*> {

        return executor.schedule(
            Runnable {
                val res = runBlocking {
                    invoicePaymentService.performAllPaymentsByStatus()
                }

                /**
                 * Here I can manage the results [res] however I want.
                 * If it is needed, I can schedule more "single" tasks using
                 * schedulePaymentsWithDelay by passing the 'periodic' parameter to false
                 */
                println(res)

                // reschedule task
                if (reschedule) {
                    schedulePeriodicWithDelay(delay, quantity, reschedule)
                }
            },
            delay(quantity),
            TimeUnit.MILLISECONDS
        )
    }


    /**
     * Schedules a recurring [function] with a specified delay of [quantity] number of [unit]
     */
    fun schedulePaymentsWithDelay(
        quantity : Long,
        unit: ChronoUnit,
        periodic : Boolean = true,
        function : ((Long) -> Long, Long, Boolean)
            -> ScheduledFuture<*> = ::schedulePeriodicWithDelay
        )
    {
        when (unit) {
            ChronoUnit.SECONDS -> {
                function(::millisToNextSecond, quantity, periodic)
            }
            ChronoUnit.DAYS -> {
                function(::millisToNextDay, quantity, periodic)
            }
            ChronoUnit.MONTHS -> {
                function(::millisToNextMonth, quantity, periodic)
            }
            else -> throw NotImplementedError()
        }
    }


    fun stopAll() {
        executor.shutdown()
        executor = Executors.newSingleThreadScheduledExecutor()
    }

}