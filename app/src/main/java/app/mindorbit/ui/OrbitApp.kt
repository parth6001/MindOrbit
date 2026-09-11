@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.mindorbit.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import app.mindorbit.data.*
import java.time.*

@Composable fun OrbitApp(vm: OrbitViewModel, incoming: Intent?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val largeText = LocalDensity.current.fontScale >= 1.4f
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val records by vm.reminders.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var snoozeId by remember { mutableStateOf<String?>(null) }
    var snoozeDue by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(incoming) {
        val id = incoming?.getStringExtra("id")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }
        if (id != null) { nav.navigate("detail/$id"); if (incoming.getBooleanExtra("snooze", false)) { snoozeId = id; snoozeDue = incoming.getLongExtra("due", -1).takeIf { it >= 0 } } }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.error.value = null } }
    val roots = listOf("home", "list", "calendar", "settings")
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (route in roots) NavigationBar {
            listOf(Triple("home", "Today", Icons.Outlined.Home), Triple("list", "Reminders", Icons.Outlined.Checklist), Triple("calendar", "Calendar", Icons.Outlined.CalendarMonth), Triple("settings", "Settings", Icons.Outlined.Settings)).forEach { (path, label, icon) -> NavigationBarItem(selected = route == path, onClick = { nav.navigate(path) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(icon, label) }, label = if (largeText) null else { { Text(label) } }) }
        }
    }, floatingActionButton = { if (route in listOf("home", "list", "calendar")) { if (largeText) FloatingActionButton(onClick = { nav.navigate("edit/new") }) { Icon(Icons.Outlined.Add, "Add reminder") } else ExtendedFloatingActionButton(onClick = { nav.navigate("edit/new") }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add reminder") }) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavHost(nav, "home", Modifier.weight(1f)) {
                composable("home") { Dashboard(records, categories, settings, { nav.navigate("edit/new") }, { nav.navigate("detail/$it") }, { vm.run { vm.repository.complete(it) } }, { nav.navigate("filtered/$it") }, { nav.navigate("categories") }, { nav.navigate("settings") }) }
                composable("list") { ReminderList(vm, "All", { nav.navigate("detail/$it") }, { nav.navigate("edit/new") }) }
                composable("filtered/{filter}") { backStack -> Column { PageHeader(backStack.arguments?.getString("filter") ?: "Reminders", { nav.popBackStack() }); ReminderList(vm, backStack.arguments?.getString("filter") ?: "All", { nav.navigate("detail/$it") }, { nav.navigate("edit/new") }) } }
                composable("calendar") { CalendarScreen(vm, { nav.navigate("detail/$it") }, { nav.navigate("edit/new") }) }
                composable("edit/{id}") { EditorScreen(vm, it.arguments?.getString("id") ?: "new", { nav.popBackStack() }) }
                composable("detail/{id}") { DetailScreen(vm, it.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, { id -> nav.navigate("edit/$id") }, { id -> nav.navigate("detail/$id") }, { id -> snoozeDue = null; snoozeId = id }) }
                composable("settings") { SettingsScreen(vm, { nav.navigate("privacy") }, { nav.navigate("history") }, { nav.navigate("categories") }) }
                composable("privacy") { PrivacyScreen(vm, { nav.popBackStack() }) }
                composable("history") { HistoryScreen(vm, { nav.popBackStack() }) }
                composable("categories") { CategoriesScreen(vm, { nav.popBackStack() }) }
            }
        }
    }
    snoozeId?.let { id -> SnoozeDialog({ snoozeId = null }, settings.snooze) { minutes -> vm.run { vm.repository.snooze(id, minutes, snoozeDue); snoozeId = null } } }
}

@Composable private fun Dashboard(records: List<ReminderRecord>, categories: List<Category>, settings: OrbitPreferences, add: () -> Unit, open: (String) -> Unit, complete: (String) -> Unit, filter: (String) -> Unit, category: () -> Unit, settingsClick: () -> Unit) {
    val today = ReminderFilter.apply(records, categories, filter = "Today")
    val notificationsAllowed = (LocalContext.current.applicationContext as app.mindorbit.MindOrbitApp).scheduler.notificationsAllowed()
    val next = records.firstOrNull { it.reminder.status == "Active" && it.reminder.dueAt > System.currentTimeMillis() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AllInclusive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)); Text("MindOrbit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("MY REMINDER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { Column(Modifier.padding(vertical = 18.dp)) { Text(when(LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold); Text("${today.size} reminders today", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)); next?.let { Text("Next: ${formatDate(it.reminder.dueAt, settings)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) } } }
        if (!notificationsAllowed) item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notifications are off", Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary); TextButton(onClick = settingsClick) { Text("Enable alerts") } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf("Today", "Upcoming").forEach { name -> SummaryTile(name, ReminderFilter.apply(records, categories, filter = name).size, Modifier.weight(1f), { filter(name) }) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf("Overdue", "Completed").forEach { name -> SummaryTile(name, ReminderFilter.apply(records, categories, filter = name).size, Modifier.weight(1f), { filter(name) }) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = { filter("Important") }) { Icon(Icons.Outlined.StarOutline, null); Text("Important") }; TextButton(onClick = category) { Icon(Icons.Outlined.GridView, null); Text("Categories") } } }
        item { SectionTitle("Your day") }
        if (today.isEmpty()) item { EmptyState("No reminders today.", add) }
        items(today, key = { it.reminder.id }) { record -> ReminderCard(record, categories, settings, { open(record.reminder.id) }, { complete(record.reminder.id) }, { open(record.reminder.id) }) }
    }
}
@Composable private fun SummaryTile(name: String, count: Int, modifier: Modifier, click: () -> Unit) {
    Card(modifier.clickable(onClick = click), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = when(name) { "Today" -> MaterialTheme.colorScheme.primaryContainer; "Overdue" -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surface })) {
        Column(Modifier.padding(18.dp)) { Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold); Text(name, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable fun ReminderList(vm: OrbitViewModel, initialFilter: String, open: (String) -> Unit, add: () -> Unit) {
    val records by vm.reminders.collectAsStateWithLifecycle(); val categories by vm.categories.collectAsStateWithLifecycle(); val settings by vm.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }; var category by rememberSaveable { mutableStateOf("All") }; var priority by rememberSaveable { mutableStateOf("All") }; var advanced by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(false) }; var start by remember { mutableStateOf(LocalDate.now()) }; var end by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    val filtered = remember(records, categories, query, filter, category, priority, range, start, end) { ReminderFilter.apply(records, categories, query, filter, category, priority, if (range) start else null, if (range) end else null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Reminders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search reminders") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { IconButton(onClick = { advanced = !advanced }) { Icon(Icons.Outlined.Tune, "Filters") } }, singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Today", "Tomorrow", "Week", "Month", "Overdue", "Completed", "Archived").forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name) }) } } }
        if (advanced) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Choice("Category", categories.firstOrNull { it.id == category }?.name ?: "All", listOf("All") + categories.map { it.name }) { selected -> category = categories.firstOrNull { it.name == selected }?.id ?: "All" }; Choice("Priority", priority, listOf("All") + Catalog.priorities) { priority = it }; Toggle("Date range", range) { range = it }; if (range) { DateButton("From", start) { start = it; if (end < start) end = start }; DateButton("Until", end) { end = it.coerceAtLeast(start) } } } }
        if (filtered.isEmpty()) item { EmptyState("No matching reminders.", add) }
        items(filtered, key = { it.reminder.id }) { record ->
            val state = rememberSwipeToDismissBoxState()
            LaunchedEffect(state.currentValue) { if (state.currentValue == SwipeToDismissBoxValue.StartToEnd) { if (record.reminder.status == "Active") vm.run { vm.repository.complete(record.reminder.id) }.join(); state.reset() } }
            SwipeToDismissBox(state, enableDismissFromEndToStart = false, backgroundContent = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp), contentAlignment = Alignment.CenterStart) { Icon(Icons.Outlined.Done, "Complete") } }) { ReminderCard(record, categories, settings, { open(record.reminder.id) }, { vm.run { vm.repository.complete(record.reminder.id) } }, { open(record.reminder.id) }) }
        }
    }
}
