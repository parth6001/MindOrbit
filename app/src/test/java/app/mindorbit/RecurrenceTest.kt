package app.mindorbit

import app.mindorbit.data.Reminder
import app.mindorbit.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecurrenceTest {
    private fun r(date: String, repeat: String, zone: String = "Asia/Kolkata") = LocalDateTime.parse(date).atZone(ZoneId.of(zone)).let { Reminder(title = "Test", dueAt = it.toInstant().toEpochMilli(), localDateTime = date, zoneId = zone, repeat = repeat, anchorDay = it.dayOfMonth, anchorMonth = it.monthValue) }
    private fun date(r: Reminder?) = r?.let { Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.of(it.zoneId)).toLocalDateTime().toString() }
    @Test fun onceHasNoNext() { assertNull(Recurrence.next(r("2026-01-01T10:00", "Once"))) }
    @Test fun daily() { assertEquals("2026-01-02T10:00", date(Recurrence.next(r("2026-01-01T10:00", "Every day")))) }
    @Test fun weekdaysSkipWeekend() { assertEquals("2026-01-05T10:00", date(Recurrence.next(r("2026-01-02T10:00", "Weekdays")))) }
    @Test fun weekendsSkipWeek() { assertEquals("2026-01-10T10:00", date(Recurrence.next(r("2026-01-04T10:00", "Weekends")))) }
    @Test fun weeklyInterval() { assertEquals("2026-01-15T10:00", date(Recurrence.next(r("2026-01-01T10:00", "Weekly").copy(interval = 2)))) }
    @Test fun xDays() { assertEquals("2026-01-04T10:00", date(Recurrence.next(r("2026-01-01T10:00", "Every X days").copy(interval = 3)))) }
    @Test fun customInterval() { assertEquals("2026-02-10T10:00", date(Recurrence.next(r("2026-01-01T10:00", "Custom interval").copy(interval = 40)))) }
    @Test fun monthEndClamps() { assertEquals("2026-02-28T10:00", date(Recurrence.next(r("2026-01-31T10:00", "Monthly")))) }
    @Test fun monthEndRestoresAnchor() { assertEquals("2026-03-31T10:00", date(Recurrence.next(Recurrence.next(r("2026-01-31T10:00", "Monthly"))!!))) }
    @Test fun yearlyLeapClamps() { assertEquals("2025-02-28T10:00", date(Recurrence.next(r("2024-02-29T10:00", "Yearly")))) }
    @Test fun leapAnchorRestores() { var reminder = r("2024-02-29T10:00", "Yearly"); repeat(4) { reminder = Recurrence.next(reminder)!! }; assertEquals("2028-02-29T10:00", date(reminder)) }
    @Test fun customWeekdays() { assertEquals("2026-01-06T10:00", date(Recurrence.next(r("2026-01-01T10:00", "Custom days").copy(weekdays = "2")))) }
    @Test fun occurrenceLimit() { assertNull(Recurrence.next(r("2026-01-01T10:00", "Every day").copy(maxOccurrences = 1))) }
    @Test fun endDateLimit() { val reminder = r("2026-01-01T10:00", "Every day"); assertNull(Recurrence.next(reminder.copy(endAt = reminder.dueAt + 1))) }
    @Test fun daylightSavingPreservesWallClock() { val reminder = r("2026-03-07T09:00", "Every day", "America/New_York"); val next = Recurrence.next(reminder)!!; assertEquals("2026-03-08T09:00", date(next)); assertEquals(23 * 3600000L, next.dueAt - reminder.dueAt) }
    @Test fun validMinimalReminder() { Validation.validate(Reminder(title = "Task")) }
    @Test(expected = IllegalArgumentException::class) fun blankTitleRejected() { Validation.validate(Reminder(title = " ")) }
    @Test(expected = IllegalArgumentException::class) fun invalidIntervalRejected() { Validation.validate(Reminder(title = "Task", interval = 0)) }
    @Test(expected = IllegalArgumentException::class) fun negativeAmountRejected() { Validation.validate(Reminder(title = "Task", amount = "-1")) }
    @Test(expected = IllegalArgumentException::class) fun badAmountRejected() { Validation.validate(Reminder(title = "Task", amount = "NaN")) }
    @Test(expected = IllegalArgumentException::class) fun invalidCustomDaysRejected() { Validation.validate(Reminder(title = "Task", repeat = "Custom days", weekdays = "8")) }
    @Test(expected = IllegalArgumentException::class) fun unsafeIdRejected() { Validation.validate(Reminder(id = "../../x", title = "Task")) }
}
