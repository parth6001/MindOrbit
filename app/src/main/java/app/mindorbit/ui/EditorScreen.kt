@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.mindorbit.ui

import android.media.RingtoneManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mindorbit.data.*
import app.mindorbit.platform.Sounds
import java.time.*

@Composable fun EditorScreen(vm: OrbitViewModel, id: String, back: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var loaded by rememberSaveable(id) { mutableStateOf(id == "new") }
    var original by remember(id) { mutableStateOf<Reminder?>(null) }
    var title by rememberSaveable(id) { mutableStateOf("") }; var notes by rememberSaveable(id) { mutableStateOf("") }
    var category by rememberSaveable(id) { mutableStateOf("general") }; var priority by rememberSaveable(id) { mutableStateOf("Normal") }
    var dateText by rememberSaveable(id) { mutableStateOf(LocalDateTime.now().plusHours(1).toLocalDate().toString()) }; var timeText by rememberSaveable(id) { mutableStateOf(LocalTime.now().plusHours(1).withSecond(0).withNano(0).toString()) }
    var repeat by rememberSaveable(id) { mutableStateOf("Once") }; var interval by rememberSaveable(id) { mutableStateOf("1") }; var weekdays by rememberSaveable(id) { mutableStateOf("1,2,3,4,5") }
    var endEnabled by rememberSaveable(id) { mutableStateOf(false) }; var endText by rememberSaveable(id) { mutableStateOf(LocalDate.now().plusYears(1).toString()) }; var count by rememberSaveable(id) { mutableStateOf("") }
    var notify by rememberSaveable(id) { mutableStateOf(true) }; var alarm by rememberSaveable(id) { mutableStateOf(settings.alarm) }; var vibrate by rememberSaveable(id) { mutableStateOf(settings.vibrate) }; var sound by rememberSaveable(id) { mutableStateOf(settings.sound) }
    var tags by rememberSaveable(id) { mutableStateOf("") }; var amount by rememberSaveable(id) { mutableStateOf("") }; var reference by rememberSaveable(id) { mutableStateOf("") }; var thought by rememberSaveable(id) { mutableStateOf(false) }
    var fieldValues by rememberSaveable(id) { mutableStateOf<Map<String, String>>(hashMapOf()) }
    var customName by rememberSaveable(id) { mutableStateOf("") }; var milestones by rememberSaveable(id) { mutableStateOf(false) }
    var expanded by rememberSaveable(id) { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != "new") vm.run {
            val record = requireNotNull(vm.repository.dao.get(id)) { "Reminder no longer exists." }
            val r = record.reminder; original = r
            if (!loaded) {
            title = r.title; notes = r.notes; category = r.categoryId; priority = r.priority
            val local = Instant.ofEpochMilli(r.dueAt).atZone(ZoneId.systemDefault()); dateText = local.toLocalDate().toString(); timeText = local.toLocalTime().withSecond(0).withNano(0).toString()
            repeat = r.repeat; interval = r.interval.toString(); weekdays = r.weekdays.ifBlank { "1,2,3,4,5" }; endEnabled = r.endAt != null; endText = r.endAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() } ?: endText; count = r.maxOccurrences?.toString().orEmpty()
            notify = r.notify; alarm = r.alarm; vibrate = r.vibrate; sound = r.sound; tags = record.tags.joinToString(", ") { it.name }; amount = r.amount; reference = r.reference; thought = r.randomThought
            fieldValues = HashMap(record.fields.associate { it.name to it.value }); loaded = true
            }
        }
    }
    val context = LocalContext.current
    var ringtone by remember { mutableStateOf<android.media.Ringtone?>(null) }
    val template = Catalog.fields(category)
    DisposableEffect(Unit) { onDispose { ringtone?.stop() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(if (id == "new") "New reminder" else "Edit reminder", back)
        if (!loaded) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = false)
            OutlinedTextField(notes, { notes = it.take(20000) }, Modifier.fillMaxWidth(), label = { Text(if (category == "daily-thought") "Your thought" else "Notes") }, minLines = 3)
            Choice("Category", categories.firstOrNull { it.id == category }?.name ?: "General", categories.map { it.name }) { name -> category = categories.first { it.name == name }.id }
            SectionTitle("Schedule")
            DateButton("Date", LocalDate.parse(dateText)) { dateText = it.toString() }
            TimeButton(LocalTime.parse(timeText), settings.time24) { timeText = it.toString() }
            Choice("Priority", priority, Catalog.priorities) { priority = it }
            Choice("Repeat", repeat, Catalog.repeats) { repeat = it }
            if (repeat != "Once") {
                if (repeat in listOf("Weekly", "Monthly", "Yearly", "Every X days", "Custom interval")) OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text(if (repeat in listOf("Every X days", "Custom interval")) "Interval in days" else "Interval") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (repeat == "Custom days") FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..7).forEach { day -> val selected = day.toString() in weekdays.split(','); FilterChip(selected, { weekdays = weekdays.split(',').filter { it.isNotEmpty() }.toMutableSet().apply { if (selected) remove(day.toString()) else add(day.toString()) }.sorted().joinToString(",") }, { Text(DayOfWeek.of(day).name.take(3)) }) } }
                Toggle("End date", endEnabled) { endEnabled = it }
                if (endEnabled) DateButton("Until", LocalDate.parse(endText)) { endText = it.toString() }
                OutlinedTextField(count, { count = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Occurrence limit (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (template.isNotEmpty()) {
                SectionTitle("${categories.firstOrNull { it.id == category }?.name} details")
                template.forEach { (name, type) ->
                    val value = fieldValues[name].orEmpty()
                    fun update(text: String) { fieldValues = HashMap(fieldValues).apply { put(name, text) } }
                    when(type) {
                        "boolean" -> Toggle(name, value == "true") { update(it.toString()) }
                        "date" -> {
                            Row(Modifier.fillMaxWidth()) { Box(Modifier.weight(1f)) { DateButton(if (value.isBlank()) "$name (optional)" else name, runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.parse(dateText)), unset = value.isBlank()) { update(it.toString()) } }; if (value.isNotBlank()) IconButton(onClick = { update("") }) { Icon(Icons.Outlined.Close, "Clear $name") } }
                        }
                        else -> OutlinedTextField(value, { update(it.take(2000)) }, Modifier.fillMaxWidth(), label = { Text(name) }, keyboardOptions = KeyboardOptions(keyboardType = if (type == "number") KeyboardType.Decimal else KeyboardType.Text))
                    }
                }
                Toggle("Create reminders for entered dates", milestones) { milestones = it }
            }
            if (category == "daily-thought") Toggle("Random local thought", thought) { thought = it }
            SectionTitle("Delivery")
            Toggle("Notification", notify) { notify = it }
            if (notify) {
                Toggle("Alarm mode", alarm) { alarm = it }
                Toggle("Vibration", vibrate) { vibrate = it }
                Choice("Sound", sound, Catalog.sounds) { sound = it; ringtone?.stop() }
                TextButton(onClick = { ringtone?.stop(); runCatching { Sounds.uri(context, sound, alarm)?.let { ringtone = RingtoneManager.getRingtone(context, it); ringtone?.play() } }.onFailure { vm.error.value = "This sound is unavailable on your device." } }) { Icon(Icons.Outlined.PlayArrow, null); Text("Preview sound") }
            }
            TextButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null); Text("More details") }
            if (expanded) {
                OutlinedTextField(tags, { tags = it.take(1500) }, Modifier.fillMaxWidth(), label = { Text("Tags, separated by commas") })
                OutlinedTextField(amount, { amount = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Amount (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(reference, { reference = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("Reference information") })
                fieldValues.keys.filter { key -> template.none { it.first == key } }.forEach { name -> Row { OutlinedTextField(fieldValues[name].orEmpty(), { fieldValues = HashMap(fieldValues).apply { put(name, it) } }, Modifier.weight(1f), label = { Text(name) }); IconButton(onClick = { fieldValues = HashMap(fieldValues).apply { remove(name) } }) { Icon(Icons.Outlined.DeleteOutline, "Remove $name") } } }
                Row { OutlinedTextField(customName, { customName = it.take(100) }, Modifier.weight(1f), label = { Text("New custom field") }); IconButton(onClick = { if (customName.isNotBlank() && fieldValues.size < 40) { fieldValues = HashMap(fieldValues).apply { putIfAbsent(customName.trim(), "") }; customName = "" } }) { Icon(Icons.Outlined.Add, "Add custom field") } }
            }
            Spacer(Modifier.height(20.dp))
        }
        Button(onClick = {
            if (!saving) {
                saving = true
                vm.run {
                    try {
                        val local = LocalDate.parse(dateText).atTime(LocalTime.parse(timeText)); val zone = ZoneId.systemDefault()
                        val base = original ?: Reminder()
                        val r = base.copy(title = title, notes = notes, categoryId = category, priority = priority, dueAt = local.atZone(zone).toInstant().toEpochMilli(), localDateTime = local.toString(), zoneId = zone.id, repeat = repeat, interval = interval.toIntOrNull() ?: 0, weekdays = weekdays, endAt = if (endEnabled && repeat != "Once") LocalDate.parse(endText).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli() else null, maxOccurrences = if (count.isBlank() || repeat == "Once") null else count.toInt(), anchorDay = local.dayOfMonth, anchorMonth = local.monthValue, occurrenceIndex = if (original?.repeat != repeat) 1 else base.occurrenceIndex, notify = notify, alarm = alarm, sound = sound, vibrate = vibrate, amount = amount, reference = reference, randomThought = thought)
                        val fields = fieldValues.map { (name, value) -> CustomField(r.id, name, value, template.firstOrNull { it.first == name }?.second ?: "text") }
                        vm.save(r, fields, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, milestones, back).join()
                    } finally { saving = false }
                }
            }
        }, enabled = title.isNotBlank() && !saving, modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp)) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(8.dp)); Text("Save reminder") }
    }
}
