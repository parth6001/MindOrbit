package app.mindorbit.domain

import app.mindorbit.data.*
import java.time.*

object Recurrence {
    fun next(r: Reminder): Reminder? {
        if (r.repeat == "Once" || (r.maxOccurrences != null && r.occurrenceIndex >= r.maxOccurrences)) return null
        val zone = ZoneId.of(r.zoneId)
        val current = Instant.ofEpochMilli(r.dueAt).atZone(zone)
        val anchor = if (r.anchorDay > 0) r.anchorDay else current.dayOfMonth
        val next = when (r.repeat) {
            "Every day" -> current.plusDays(1)
            "Weekly" -> current.plusWeeks(r.interval.toLong())
            "Every X days", "Custom interval" -> current.plusDays(r.interval.toLong())
            "Monthly" -> current.plusMonths(r.interval.toLong()).let { it.withDayOfMonth(minOf(anchor, it.toLocalDate().lengthOfMonth())) }
            "Yearly" -> current.plusYears(r.interval.toLong()).withMonth(if (r.anchorMonth > 0) r.anchorMonth else current.monthValue).let { it.withDayOfMonth(minOf(anchor, it.toLocalDate().lengthOfMonth())) }
            else -> {
                val days = when (r.repeat) { "Weekdays" -> setOf(1,2,3,4,5); "Weekends" -> setOf(6,7); else -> r.weekdays.split(',').mapNotNull { it.toIntOrNull() }.toSet() }
                require(days.isNotEmpty()) { "Choose at least one repeat day." }
                (1L..7L).map { current.plusDays(it) }.first { it.dayOfWeek.value in days }
            }
        }
        val millis = next.toInstant().toEpochMilli()
        if (r.endAt != null && millis > r.endAt) return null
        return r.copy(dueAt = millis, localDateTime = next.toLocalDateTime().toString(), occurrenceIndex = r.occurrenceIndex + 1, anchorDay = anchor, anchorMonth = if (r.anchorMonth > 0) r.anchorMonth else current.monthValue, scheduleState = "Pending")
    }
}

object Validation {
    fun validate(r: Reminder, fields: List<CustomField> = emptyList()) {
        require(r.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid reminder identifier." }
        require(r.title.isNotBlank() && r.title.length <= 200) { "Enter a title of 1 to 200 characters." }
        require(r.notes.length <= 20000 && r.reference.length <= 2000) { "Notes or reference are too long." }
        require(r.priority in Catalog.priorities && r.repeat in Catalog.repeats && r.sound in Catalog.sounds) { "Invalid reminder options." }
        require(r.status in listOf("Active", "Completed", "Archived")) { "Invalid status." }
        require(r.interval in 1..3650) { "Interval must be 1 to 3650." }
        require(r.maxOccurrences == null || r.maxOccurrences in 1..100000) { "Invalid occurrence limit." }
        require(r.occurrenceIndex in 1..100000 && r.anchorDay in 0..31 && r.anchorMonth in 0..12) { "Invalid recurrence state." }
        require(r.endAt == null || r.endAt >= r.dueAt) { "End date must not precede the reminder." }
        require(r.dueAt in 0..4102444800000L) { "Choose a date before 2100." }
        ZoneId.of(r.zoneId)
        if (r.localDateTime.isNotEmpty()) LocalDateTime.parse(r.localDateTime)
        if (r.repeat == "Custom days") require(r.weekdays.split(',').all { (it.toIntOrNull() ?: 0) in 1..7 }) { "Choose repeat days." }
        require(r.amount.isBlank() || r.amount.toBigDecimalOrNull()?.let { it.signum() >= 0 && it.precision() <= 18 } == true) { "Enter a valid non-negative amount." }
        require(fields.size <= 40 && fields.map { it.name }.distinct().size == fields.size) { "Too many or duplicate custom fields." }
        fields.forEach {
            require(it.name.isNotBlank() && it.name.length <= 100 && it.value.length <= 2000) { "Invalid custom field." }
            require(it.type in listOf("text", "date", "number", "boolean")) { "Invalid field type." }
            if (it.value.isNotBlank()) when (it.type) {
                "date" -> LocalDate.parse(it.value)
                "number" -> require(it.value.toBigDecimalOrNull()?.let { n -> n.signum() >= 0 && n.precision() <= 18 } == true) { "Invalid number: ${it.name}" }
                "boolean" -> require(it.value in listOf("true", "false")) { "Invalid switch value." }
            }
        }
    }
}
