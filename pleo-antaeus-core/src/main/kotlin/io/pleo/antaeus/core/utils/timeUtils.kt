package io.pleo.antaeus.core.schedulers

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit


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

internal fun millisToNextDay(days : Long) : Long {
    val now = ZonedDateTime.now()
    val res = now
        .plusDays(days)
        .withHour(0)
        .withMinute(0)
        .withSecond(0)

    return now.until(res, ChronoUnit.MILLIS)
}

internal fun millisToNextSecond(seconds : Long) : Long {
    val now = ZonedDateTime.now()
    val res = now
        .withSecond(now.second)
        .plusSeconds(seconds)

    return now.until(res, ChronoUnit.MILLIS)
}