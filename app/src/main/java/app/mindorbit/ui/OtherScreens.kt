@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.mindorbit.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mindorbit.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun CalendarScreen(vm: OrbitViewModel, open: (String) -> Unit, add: () -> Unit) {
    val records by vm.reminders.collectAsStateWithLifecycle(); val categories by vm.categories.collectAsStateWithLifecycle(); val settings by vm.settings.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now()) }; var selected by remember { mutableStateOf(LocalDate.now()) }
    val byDate = remember(records) { records.filter { it.reminder.status == "Active" }.groupBy { Instant.ofEpochMilli(it.reminder.dueAt).atZone(ZoneId.systemDefault()).toLocalDate() } }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }; Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "Next month") } } }
        item {
            val offset = if (settings.mondayFirst) month.atDay(1).dayOfWeek.value - 1 else month.atDay(1).dayOfWeek.value % 7
            Column {
                Row { (0..6).forEach { i -> val day = if (settings.mondayFirst) DayOfWeek.of(i + 1) else DayOfWeek.of(if (i == 0) 7 else i); Box(Modifier.weight(1f).heightIn(min = 40.dp), contentAlignment = Alignment.Center) { Text(day.name.take(1), style = MaterialTheme.typography.labelMedium) } } }
                (0 until (offset + month.lengthOfMonth() + 6) / 7).forEach { week -> Row { (0..6).forEach { weekday ->
                    val number = week * 7 + weekday - offset + 1
                    Box(Modifier.weight(1f).heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                        if (number in 1..month.lengthOfMonth()) {
                            val day = month.atDay(number)
                            TextButton(onClick = { selected = day }, colors = ButtonDefaults.textButtonColors(containerColor = if (selected == day) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background), contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(number.toString()); if (byDate.containsKey(day)) Icon(Icons.Outlined.Circle, "Reminders on $day", Modifier.size(5.dp)) else Spacer(Modifier.height(5.dp)) } }
                        }
                    }
                } } }
            }
        }
        item { SectionTitle(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))) }
        if (byDate[selected].isNullOrEmpty()) item { EmptyState("No reminders on this day.", add) }
        items(byDate[selected].orEmpty(), key = { it.reminder.id }) { r -> ReminderCard(r, categories, settings, { open(r.reminder.id) }, { vm.run { vm.repository.complete(r.reminder.id) } }, { open(r.reminder.id) }) }
    }
}

@Composable fun DetailScreen(vm: OrbitViewModel, id: String, back: () -> Unit, edit: (String) -> Unit, open: (String) -> Unit, snooze: (String) -> Unit) {
    val records by vm.reminders.collectAsStateWithLifecycle(); val categories by vm.categories.collectAsStateWithLifecycle(); val settings by vm.settings.collectAsStateWithLifecycle()
    val record = records.firstOrNull { it.reminder.id == id }
    var delete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Reminder", back, actions = { IconButton(onClick = { edit(id) }) { Icon(Icons.Outlined.Edit, "Edit") } })
        if (record == null) { EmptyState("Reminder not found."); return@Column }
        val r = record.reminder
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(categoryIcon(r.categoryId), null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(r.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(categories.firstOrNull { it.id == r.categoryId }?.name.orEmpty(), color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            DetailRow("Status", r.status); DetailRow("Date & time", formatDate(r.dueAt, settings)); DetailRow("Repeat", "${r.repeat}${if (r.interval > 1) " / ${r.interval}" else ""}"); DetailRow("Priority", r.priority); DetailRow("Delivery", r.scheduleState)
            if (r.repeat == "Custom days") DetailRow("Days", r.weekdays.split(',').mapNotNull { it.toIntOrNull() }.joinToString { DayOfWeek.of(it).name })
            r.endAt?.let { DetailRow("Until", formatDate(it, settings)) }; r.maxOccurrences?.let { DetailRow("Occurrences", "${r.occurrenceIndex} of $it") }
            if (r.notes.isNotBlank()) { SectionTitle("Notes"); Text(r.notes) }
            if (r.amount.isNotBlank()) DetailRow("Amount", r.amount)
            if (r.reference.isNotBlank()) DetailRow("Reference", r.reference)
            record.fields.forEach { DetailRow(it.name, it.value) }
            if (record.tags.isNotEmpty()) DetailRow("Tags", record.tags.joinToString { it.name })
            SectionTitle("Activity"); DetailRow("Created", formatDate(r.createdAt, settings)); DetailRow("Last modified", formatDate(r.updatedAt, settings))
            if (r.status == "Active") { Button(onClick = { vm.run { vm.repository.complete(id) } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Check, null); Text("Complete") }; OutlinedButton(onClick = { snooze(id) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Snooze, null); Text("Snooze") } }
            OutlinedButton(onClick = { vm.run { open(vm.repository.duplicate(id)) } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.ContentCopy, null); Text("Duplicate") }
            if (r.status != "Archived") TextButton(onClick = { vm.run { vm.repository.archive(id) } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Archive, null); Text("Archive") }
            TextButton(onClick = { delete = true }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.DeleteOutline, null); Text("Delete") }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete reminder?") }, text = { Text("This reminder and its scheduled alert will be removed.") }, confirmButton = { TextButton(onClick = { vm.run { vm.repository.delete(id); back() }; delete = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}
@Composable private fun DetailRow(label: String, value: String) { Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge) } }

@Composable fun CategoriesScreen(vm: OrbitViewModel, back: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle(); var name by remember { mutableStateOf("") }
    Column { PageHeader("Categories", back); LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(name, { name = it.take(50) }, Modifier.weight(1f), label = { Text("New category") }); IconButton(onClick = { vm.run { vm.repository.addCategory(name); name = "" } }) { Icon(Icons.Outlined.Add, "Create category") } } }; items(categories, key = { it.id }) { category -> ListItem(headlineContent = { Text(category.name) }, leadingContent = { Icon(categoryIcon(category.id), null) }, supportingContent = { Text(if (category.builtIn) "Built-in" else "Custom") }) } } }
}

@Composable fun HistoryScreen(vm: OrbitViewModel, back: () -> Unit) {
    val events by vm.history.collectAsStateWithLifecycle(); val settings by vm.settings.collectAsStateWithLifecycle(); var filter by remember { mutableStateOf("All") }; var clear by remember { mutableStateOf(false) }
    Column { PageHeader("History", back, actions = { IconButton(onClick = { clear = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear history") } }); LazyColumn(contentPadding = PaddingValues(20.dp)) { item { Choice("Event", filter, listOf("All", "Completed", "Missed", "Snoozed", "Archived", "Delivered")) { filter = it } }; val filtered = events.filter { filter == "All" || it.event == filter }; if (filtered.isEmpty()) item { EmptyState("No history yet.") }; items(filtered, key = { it.id }) { event -> ListItem(headlineContent = { Text(event.title) }, supportingContent = { Text("${event.event}\n${formatDate(event.at, settings)}") }, leadingContent = { Icon(Icons.Outlined.History, null) }) } } }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear history?") }, text = { Text("Reminders will be kept. Activity records will be permanently removed.") }, confirmButton = { TextButton(onClick = { vm.run { vm.repository.dao.clearHistory() }; clear = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
