@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.mindorbit.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindorbit.data.*
import java.time.*
import java.time.format.DateTimeFormatter

fun formatDate(at: Long, settings: OrbitPreferences = OrbitPreferences()): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(settings.dateFormat + if (settings.time24) "  HH:mm" else "  h:mm a"))
fun categoryIcon(id: String): ImageVector = when(id) {
    "work", "deadline" -> Icons.Outlined.WorkOutline
    "email" -> Icons.Outlined.MailOutline
    "subscription" -> Icons.Outlined.Subscriptions
    "bill", "investment", "fd", "stock-ipo" -> Icons.Outlined.AccountBalanceWallet
    "purchase", "warranty" -> Icons.Outlined.ShoppingBag
    "birthday", "anniversary" -> Icons.Outlined.Cake
    "health" -> Icons.Outlined.FavoriteBorder
    "document" -> Icons.Outlined.Description
    "vehicle" -> Icons.Outlined.DirectionsCar
    "daily-thought" -> Icons.Outlined.Lightbulb
    "personal" -> Icons.Outlined.PersonOutline
    else -> Icons.Outlined.BookmarkBorder
}
@Composable fun PageHeader(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }, actions = actions)
}
@Composable fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
@Composable fun Choice(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text(selected, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onSelect(item); expanded = false }) } }
        }
    }
}
@Composable fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange) }
}
@Composable fun DateButton(label: String, date: LocalDate, unset: Boolean = false, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.CalendarToday, null); Spacer(Modifier.width(8.dp)); Text(if (unset) label else "$label: ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}") }
}
@Composable fun TimeButton(time: LocalTime, time24: Boolean, onChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> onChange(LocalTime.of(h, m)) }, time.hour, time.minute, time24).show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(8.dp)); Text(time.format(DateTimeFormatter.ofPattern(if (time24) "HH:mm" else "h:mm a"))) }
}
@Composable fun EmptyState(title: String, add: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.TaskAlt, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        if (add != null) TextButton(onClick = add) { Icon(Icons.Outlined.Add, null); Text("Add reminder") }
    }
}
@Composable fun ReminderCard(record: ReminderRecord, categories: List<Category>, settings: OrbitPreferences, open: () -> Unit, complete: () -> Unit, options: () -> Unit) {
    val r = record.reminder
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = open, onLongClick = options), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(r.categoryId), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("${categories.firstOrNull { it.id == r.categoryId }?.name ?: "Custom"}  /  ${r.priority}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(r.dueAt, settings), style = MaterialTheme.typography.bodySmall, color = if (r.dueAt < System.currentTimeMillis() && r.status == "Active") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r.status == "Active") IconButton(onClick = complete) { Icon(Icons.Outlined.RadioButtonUnchecked, "Complete ${r.title}") } else Icon(Icons.Outlined.CheckCircle, r.status, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable fun SnoozeDialog(onDismiss: () -> Unit, initial: Int = 10, onSnooze: (Int) -> Unit) {
    var custom by remember { mutableStateOf(initial.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Snooze reminder") }, text = {
        Column { listOf(5,10,15,30,60).forEach { minutes -> TextButton(onClick = { onSnooze(minutes) }, modifier = Modifier.fillMaxWidth()) { Text(if (minutes == 60) "1 hour" else "$minutes minutes") } }; OutlinedTextField(custom, { custom = it.filter(Char::isDigit).take(5) }, label = { Text("Custom minutes") }, singleLine = true) }
    }, confirmButton = { TextButton(onClick = { custom.toIntOrNull()?.takeIf { it in 1..10080 }?.let(onSnooze) }, enabled = custom.toIntOrNull() in 1..10080) { Text("Snooze") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
