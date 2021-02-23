package io.pleo.antaeus.core.utils

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit


/**
 * Get milliseconds to the beginning of the [months]th month from now
 */
internal fun millisToNextMonth(months : Long) : Long {
    val now = ZonedDateTime.now()
    val res = now
        .withDayOfMonth(1)
        .plusMonths(months)
        .withHour(0)
        .withMinute(0)
        .withSecond(0)

    return now.until(res, ChronoUnit.MILLIS)
}

/**
 * Get milliseconds to the beginning of the [days]th day from now
 */
internal fun millisToNextDay(days : Long) : Long {
    val now = ZonedDateTime.now()
    val res = now
        .plusDays(days)
        .withHour(0)
        .withMinute(0)
        .withSecond(0)

    return now.until(res, ChronoUnit.MILLIS)
}

/**
 * Get milliseconds to the beginning of the [seconds]th second from now
 */
internal fun millisToNextSecond(seconds : Long) : Long {
    val now = ZonedDateTime.now()
    val res = now
        .withSecond(now.second)
        .plusSeconds(seconds)

    return now.until(res, ChronoUnit.MILLIS)
}