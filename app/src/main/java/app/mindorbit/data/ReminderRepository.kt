package app.mindorbit.data

import androidx.room.withTransaction
import app.mindorbit.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

interface ReminderScheduler {
    fun schedule(reminder: Reminder): String
    fun cancel(id: String)
    fun dismiss(id: String)
}

class ReminderRepository(val database: OrbitDatabase, private val scheduler: ReminderScheduler, private val now: () -> Long = System::currentTimeMillis) {
    val dao = database.dao()
    val reminders = dao.observe()
    val categories = dao.categories()
    val history = dao.history()
    private val mutex = Mutex()
    suspend fun initialize() { Catalog.categories.forEach { dao.category(it) } }
    suspend fun save(r: Reminder, fields: List<CustomField>, tags: List<String>) = saveBatch(listOf(ReminderRecord(r, fields, tags.map { Tag(it, it) })))
    suspend fun saveBatch(records: List<ReminderRecord>) = mutex.withLock {
        val categoryIds = dao.allCategories().map { it.id }.toSet()
        val normalized = records.map { record ->
            val r = record.reminder
            Validation.validate(r, record.fields)
            require(r.categoryId in categoryIds) { "Choose an existing category." }
            require(record.tags.size <= 30 && record.tags.all { it.name.length in 1..50 }) { "Use up to 30 short tags." }
            record.copy(reminder = r.copy(title = r.title.trim(), updatedAt = now(), scheduleState = "Pending"), tags = record.tags.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().map { Tag(it, it) }, fields = record.fields.filter { it.value.isNotBlank() }.map { it.copy(reminderId = r.id) })
        }
        database.withTransaction {
            normalized.forEach { record ->
                dao.put(record.reminder)
                dao.clearFields(record.reminder.id); dao.clearTags(record.reminder.id)
                dao.fields(record.fields)
                record.tags.forEach { dao.tag(it) }
                dao.links(record.tags.map { ReminderTag(record.reminder.id, it.id) })
            }
        }
        normalized.forEach { scheduler.cancel(it.reminder.id); scheduler.dismiss(it.reminder.id); schedule(it.reminder) }
    }
    private suspend fun schedule(r: Reminder) {
        val state = if (r.status != "Active" || !r.notify) "Off" else try { scheduler.schedule(r) } catch (_: Exception) { "Scheduling failed - reopen app to retry" }
        dao.put(r.copy(scheduleState = state))
    }
    suspend fun complete(id: String, expectedDue: Long? = null) = mutex.withLock {
        val r = dao.get(id)?.reminder ?: return@withLock
        if (expectedDue != null && expectedDue < r.dueAt && r.repeat != "Once") {
            dao.event(History(id = "$id-$expectedDue-complete", reminderId = id, title = r.title, event = "Completed", at = now(), occurrenceAt = expectedDue))
            scheduler.dismiss(id)
            return@withLock
        }
        if (r.status != "Active" || (expectedDue != null && expectedDue != r.dueAt)) return@withLock
        scheduler.cancel(id); scheduler.dismiss(id)
        var next = Recurrence.next(r)
        database.withTransaction {
            dao.event(History(reminderId = id, title = r.title, event = "Completed", at = now(), occurrenceAt = r.dueAt))
            while (next != null && next!!.dueAt < now()) {
                dao.event(History(reminderId = id, title = r.title, event = "Missed", at = now(), occurrenceAt = next!!.dueAt))
                next = Recurrence.next(next!!)
            }
            dao.put(next?.copy(updatedAt = now()) ?: r.copy(status = "Completed", scheduleState = "Off", updatedAt = now()))
        }
        next?.let { schedule(it) }
    }
    suspend fun snooze(id: String, minutes: Int, expectedDue: Long? = null) = mutex.withLock {
        require(minutes in 1..10080) { "Snooze must be between 1 minute and 7 days." }
        val r = dao.get(id)?.reminder ?: return@withLock
        if (expectedDue != null && expectedDue < r.dueAt && r.repeat != "Once") {
            val due = now() + minutes * 60000L
            val copy = r.copy(id = java.util.UUID.nameUUIDFromBytes("$id-$expectedDue-snooze".toByteArray()).toString(), repeat = "Once", dueAt = due, localDateTime = Instant.ofEpochMilli(due).atZone(ZoneId.of(r.zoneId)).toLocalDateTime().toString(), endAt = null, maxOccurrences = null, occurrenceIndex = 1, status = "Active", updatedAt = now())
            database.withTransaction { dao.put(copy); dao.event(History(reminderId = id, title = r.title, event = "Snoozed", at = now(), occurrenceAt = expectedDue)) }
            scheduler.dismiss(id); schedule(copy)
            return@withLock
        }
        if (r.status != "Active" || (expectedDue != null && expectedDue != r.dueAt)) return@withLock
        val due = now() + minutes * 60000L
        val updated = r.copy(dueAt = due, localDateTime = Instant.ofEpochMilli(due).atZone(ZoneId.of(r.zoneId)).toLocalDateTime().toString(), updatedAt = now(), endAt = r.endAt?.coerceAtLeast(due))
        database.withTransaction { dao.event(History(reminderId = id, title = r.title, event = "Snoozed", at = now(), occurrenceAt = r.dueAt)); dao.put(updated) }
        scheduler.cancel(id); scheduler.dismiss(id); schedule(updated)
    }
    suspend fun archive(id: String) = mutex.withLock {
        val r = dao.get(id)?.reminder ?: return@withLock
        scheduler.cancel(id); scheduler.dismiss(id)
        database.withTransaction { dao.put(r.copy(status = "Archived", scheduleState = "Off", updatedAt = now())); dao.event(History(reminderId = id, title = r.title, event = "Archived", at = now())) }
    }
    suspend fun delete(id: String) = mutex.withLock { scheduler.cancel(id); scheduler.dismiss(id); dao.delete(id) }
    suspend fun duplicate(id: String): String {
        val record = requireNotNull(dao.get(id)) { "Reminder no longer exists." }
        val copy = record.reminder.copy(id = java.util.UUID.randomUUID().toString(), title = record.reminder.title.take(190) + " (copy)", status = "Active", occurrenceIndex = 1, createdAt = now(), updatedAt = now())
        save(copy, record.fields, record.tags.map { it.name }); return copy.id
    }
    suspend fun addCategory(name: String) {
        require(name.trim().length in 1..50) { "Enter a category name of 1 to 50 characters." }
        require(dao.allCategories().none { it.name.equals(name.trim(), true) }) { "This category already exists." }
        dao.category(Category(java.util.UUID.randomUUID().toString(), name.trim()))
    }
    suspend fun reschedule(timezoneChanged: Boolean = false) = mutex.withLock {
        dao.all().forEach { record ->
            var r = record.reminder
            if (r.status == "Active") {
                if (timezoneChanged && r.localDateTime.isNotEmpty()) {
                    val local = LocalDateTime.parse(r.localDateTime).atZone(ZoneId.systemDefault())
                    r = r.copy(dueAt = local.toInstant().toEpochMilli(), zoneId = local.zone.id, endAt = r.endAt?.plus(local.toInstant().toEpochMilli() - r.dueAt))
                }
                scheduler.cancel(r.id)
                if (r.scheduleState != "Delivered" || r.dueAt > now()) schedule(r)
            }
        }
    }
    suspend fun deliver(id: String, expectedDue: Long, notify: (Reminder) -> String) = mutex.withLock {
        val r = dao.get(id)?.reminder ?: return@withLock
        if (r.status != "Active" || r.dueAt != expectedDue || r.scheduleState == "Delivered") return@withLock
        scheduler.cancel(id)
        val result = notify(r)
        val state = if (result == "Notifications disabled" || result == "Notification channel disabled") "Delivery blocked: $result" else result
        var next = Recurrence.next(r)
        database.withTransaction {
            dao.event(History(reminderId = id, title = r.title, event = if (now() - r.dueAt > 900000) "Missed" else state, at = now(), occurrenceAt = r.dueAt))
            while (next != null && next!!.dueAt <= now()) {
                dao.event(History(reminderId = id, title = r.title, event = "Missed", at = now(), occurrenceAt = next!!.dueAt))
                next = Recurrence.next(next!!)
            }
            dao.put(next ?: r.copy(scheduleState = state))
        }
        next?.let { schedule(it) }
    }
    suspend fun deliverDue(id: String, expectedDue: Long, notify: (Reminder) -> String) {
        val expected = dao.get(id)?.reminder ?: return
        if (expected.dueAt != expectedDue || expected.status != "Active") return
        val due = dao.all().map { it.reminder }.filter { it.status == "Active" && it.notify && it.dueAt <= now() && it.scheduleState != "Delivered" && !it.scheduleState.startsWith("Delivery blocked:") }
        due.forEach { deliver(it.id, it.dueAt, notify) }
    }
    suspend fun clearAll() = mutex.withLock {
        dao.all().forEach { scheduler.cancel(it.reminder.id); scheduler.dismiss(it.reminder.id) }
        database.withTransaction { dao.clearReminders(); dao.clearHistory(); dao.clearAllTags(); dao.clearCategories(); Catalog.categories.forEach { dao.category(it) } }
    }
    suspend fun restore(backup: Backup) = mutex.withLock {
        val existingCategories = dao.allCategories()
        val categoryMap = backup.categories.associate { imported -> imported.id to (existingCategories.firstOrNull { it.name.equals(imported.name, true) }?.id ?: imported.id) }
        require(backup.categories.none { imported -> existingCategories.any { it.id == imported.id && !it.name.equals(imported.name, true) } }) { "A backup category conflicts with an existing category." }
        database.withTransaction {
            backup.categories.forEach { dao.category(it) }
            backup.records.forEach { record ->
                val r = record.reminder.copy(scheduleState = "Pending", categoryId = categoryMap.getValue(record.reminder.categoryId))
                dao.put(r); dao.clearFields(r.id); dao.clearTags(r.id); dao.fields(record.fields)
                record.tags.forEach { dao.tag(it) }; dao.links(record.tags.map { ReminderTag(r.id, it.id) })
            }
            backup.history.forEach { dao.event(it) }
        }
        backup.records.forEach { scheduler.cancel(it.reminder.id); scheduler.dismiss(it.reminder.id); schedule(it.reminder.copy(categoryId = categoryMap.getValue(it.reminder.categoryId))) }
    }
    suspend fun backup(): Backup = database.withTransaction { Backup(dao.allCategories(), dao.all(), dao.allHistory()) }
}
