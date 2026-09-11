package app.mindorbit.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mindorbit.MindOrbitApp
import app.mindorbit.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun SettingsScreen(vm: OrbitViewModel, privacy: () -> Unit, history: () -> Unit, categories: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Appearance")
            Choice("Theme", settings.theme, listOf("System", "Light", "Dark")) { vm.run { vm.preferences.text("theme", it) } }
            Toggle("Device colors", settings.dynamicColor) { vm.run { vm.preferences.flag("dynamic", it) } }
            Toggle("24-hour time", settings.time24) { vm.run { vm.preferences.flag("time24", it) } }
            Toggle("Week starts on Monday", settings.mondayFirst) { vm.run { vm.preferences.flag("monday", it) } }
            Choice("Date format", settings.dateFormat, listOf("dd MMM yyyy", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd")) { vm.run { vm.preferences.text("date", it) } }
            SectionTitle("Reminder defaults")
            Choice("Default sound", settings.sound, Catalog.sounds) { vm.run { vm.preferences.text("sound", it) } }
            Toggle("Vibration", settings.vibrate) { vm.run { vm.preferences.flag("vibrate", it) } }
            Toggle("Alarm mode", settings.alarm) { vm.run { vm.preferences.flag("alarm", it) } }
            Choice("Default snooze", "${settings.snooze} minutes", listOf(5,10,15,30,60).map { "$it minutes" }) { vm.run { vm.preferences.number("snooze", it.substringBefore(' ').toInt()) } }
            PermissionControls(vm)
            SectionTitle("Your data")
            TextButton(onClick = categories) { Icon(Icons.Outlined.GridView, null); Text("Categories") }
            TextButton(onClick = history) { Icon(Icons.Outlined.History, null); Text("History") }
            BackupControls(vm)
            TextButton(onClick = privacy) { Icon(Icons.Outlined.Shield, null); Text("Privacy") }
            Text("MindOrbit 1.0.0", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun PermissionControls(vm: OrbitViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as MindOrbitApp
    var explanation by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> refresh++; if (!granted) vm.error.value = "Notifications are off. Reminders are saved, but alerts cannot appear until you enable notifications."; vm.run { vm.repository.reschedule() } }
    val settingsRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++; vm.run { vm.repository.reschedule() } }
    SectionTitle("Permissions")
    val allowed = remember(refresh) { app.scheduler.notificationsAllowed() }
    Text(if (allowed) "Notifications are enabled." else "Notifications are off. Your reminders are saved, but alerts cannot appear.", style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { explanation = "notifications" }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.NotificationsNone, null); Text("Notification access") }
    val exact = remember(refresh) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    Text(if (exact) "Precise reminder timing is enabled." else "Reminders use approximate timing. Android may delay alerts.", style = MaterialTheme.typography.bodyMedium)
    if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { explanation = "exact" }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Alarm, null); Text("Precise timing") }
    if (Build.VERSION.SDK_INT >= 34) OutlinedButton(onClick = { explanation = "fullscreen" }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Fullscreen, null); Text("Full-screen alarm access") }
    if (explanation != null) AlertDialog(onDismissRequest = { explanation = null }, title = { Text(when(explanation) { "exact" -> "Precise reminders"; "fullscreen" -> "Full-screen alarms"; else -> "Reminder notifications" }) }, text = { Text(when(explanation) { "exact" -> "Allow Alarms & reminders in Android settings for precise timing. Without access, MindOrbit uses approximate alerts that Android may delay."; "fullscreen" -> "Android decides whether an alarm may open over the lock screen. Allow this only if you want full-screen alarm reminders. Otherwise, alarms use a prominent notification."; else -> "Allow notifications so MindOrbit can show reminders and their Done and Snooze actions. You can continue using all lists and saved reminders without this permission." }) }, confirmButton = { TextButton(onClick = {
        val kind = explanation; explanation = null
        runCatching {
            when {
                kind == "notifications" && Build.VERSION.SDK_INT >= 33 && !allowed -> notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                kind == "exact" && Build.VERSION.SDK_INT >= 31 -> settingsRequest.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                kind == "fullscreen" && Build.VERSION.SDK_INT >= 34 -> settingsRequest.launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                else -> settingsRequest.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }.onFailure { vm.error.value = "Open Android Settings, then Apps, MindOrbit, to change this permission." }
    }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { explanation = null }) { Text("Not now") } })
}

@Composable fun BackupControls(vm: OrbitViewModel) {
    val resolver = LocalContext.current.contentResolver
    var pending by remember { mutableStateOf<Backup?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.run { withContext(Dispatchers.IO) { val dao = vm.repository.dao; val text = BackupCodec.encode(Backup(dao.allCategories(), dao.all(), dao.allHistory())); requireNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot open this file." }.bufferedWriter().use { it.write(text) } }; vm.error.value = "Backup exported." } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.run { withContext(Dispatchers.IO) { val dao = vm.repository.dao; val text = BackupCodec.csv(dao.all(), dao.allCategories()); requireNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot open this file." }.bufferedWriter().use { it.write(text) } }; vm.error.value = "CSV exported." } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.run { pending = withContext(Dispatchers.IO) { requireNotNull(resolver.openInputStream(uri)) { "Cannot read this file." }.use { BackupCodec.decode(BackupCodec.read(it)) } } } }
    OutlinedButton(onClick = { export.launch("MindOrbit-backup.json") }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload, null); Text("Export backup") }
    OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileUpload, null); Text("Import backup") }
    OutlinedButton(onClick = { csv.launch("MindOrbit-reminders.csv") }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.TableChart, null); Text("Export reminders (CSV)") }
    pending?.let { backup -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Restore ${backup.records.size} reminders?") }, text = { Text("Matching reminder IDs will be replaced. Other reminders will be kept. Alerts will be rescheduled. Choose backups only from a source you trust.") }, confirmButton = { TextButton(onClick = { vm.run { vm.repository.restore(backup); pending = null; vm.error.value = "Backup restored." } }) { Text("Restore") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }) }
}

@Composable fun PrivacyScreen(vm: OrbitViewModel, back: () -> Unit) {
    var delete by remember { mutableStateOf(false) }
    Column { PageHeader("Privacy", back); Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        listOf("Your reminder data stays on this device.", "Internet access is not required.", "No account is required.", "No analytics.", "No advertising.", "No cloud synchronization.").forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
        Text("Exports contain your reminder data. Choose a local folder and keep backups somewhere private. Android document providers may offer cloud destinations; MindOrbit does not control those providers.")
        BackupControls(vm)
        TextButton(onClick = { delete = true }) { Icon(Icons.Outlined.DeleteForever, null); Text("Delete all data") }
    } }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete all MindOrbit data?") }, text = { Text("All reminders, categories, tags, history, and preferences will be permanently removed. Files you previously exported are not deleted.") }, confirmButton = { TextButton(onClick = { vm.run { vm.repository.clearAll(); vm.preferences.clear(); delete = false; vm.error.value = "All app data deleted." } }) { Text("Delete all data") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}
