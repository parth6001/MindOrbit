package app.mindorbit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mindorbit.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

class OrbitViewModel(val repository: ReminderRepository, val preferences: Preferences) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val reminders = repository.reminders.catch { error.value = "Could not read reminders. Please restart MindOrbit."; emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.categories.catch { error.value = "Could not load categories."; emit(Catalog.categories) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Catalog.categories)
    val history = repository.history.catch { error.value = "Could not load history."; emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrbitPreferences())
    fun run(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        try { block() } catch (e: Exception) { if (e is CancellationException) throw e; error.value = e.message?.take(200) ?: "The operation could not be completed." } finally { busy.value = false }
    }
    fun save(r: Reminder, fields: List<CustomField>, tags: List<String>, milestones: Boolean, onSaved: () -> Unit) = run {
        val records = mutableListOf(ReminderRecord(r, fields, tags.map { Tag(it, it) }))
        if (milestones) {
            fields.filter { it.type == "date" && it.value.isNotBlank() && it.name !in listOf("Start date", "Purchase date") }.forEach { field ->
                val time = Instant.ofEpochMilli(r.dueAt).atZone(ZoneId.of(r.zoneId)).toLocalTime()
                var date = LocalDate.parse(field.value)
                if (field.name == "Renewal date") date = date.minusDays(fields.firstOrNull { it.name == "Remind days before" }?.value?.toLongOrNull() ?: 0)
                val local = date.atTime(time)
                val due = local.atZone(ZoneId.of(r.zoneId)).toInstant().toEpochMilli()
                if (due != r.dueAt) {
                    val id = java.util.UUID.nameUUIDFromBytes("${r.id}:${field.name}".toByteArray()).toString()
                    records.add(ReminderRecord(r.copy(id = id, title = "${r.title.take(160)} - ${field.name}", dueAt = due, localDateTime = local.toString(), repeat = "Once", endAt = null, maxOccurrences = null, occurrenceIndex = 1), fields, tags.map { Tag(it, it) }))
                }
            }
        }
        repository.saveBatch(records)
        onSaved()
    }
}

object ReminderFilter {
    fun apply(records: List<ReminderRecord>, categories: List<Category>, query: String = "", filter: String = "All", category: String = "All", priority: String = "All", start: LocalDate? = null, end: LocalDate? = null, today: LocalDate = LocalDate.now(), now: Long = System.currentTimeMillis()): List<ReminderRecord> = records.filter { record ->
        val r = record.reminder
        val date = Instant.ofEpochMilli(r.dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val categoryName = categories.firstOrNull { it.id == r.categoryId }?.name.orEmpty()
        val matches = when(filter) {
            "Today" -> date == today && r.status == "Active"
            "Tomorrow" -> date == today.plusDays(1) && r.status == "Active"
            "Week" -> date >= today && date < today.plusWeeks(1) && r.status == "Active"
            "Month" -> date >= today && date < today.plusMonths(1) && r.status == "Active"
            "Overdue" -> r.dueAt < now && r.status == "Active"
            "Completed", "Archived" -> r.status == filter
            "Important" -> r.priority in listOf("High", "Critical") && r.status == "Active"
            "Upcoming" -> r.dueAt >= now && r.status == "Active"
            else -> r.status == "Active"
        }
        matches && (category == "All" || category == r.categoryId) && (priority == "All" || priority == r.priority) && (start == null || date >= start) && (end == null || date <= end) && (query.isBlank() || listOf(r.title, r.notes, categoryName, record.tags.joinToString(" ") { it.name }).any { it.contains(query.trim(), true) })
    }
}
