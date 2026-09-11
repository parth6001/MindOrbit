package app.mindorbit

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.mindorbit.data.*
import app.mindorbit.platform.Sounds
import app.mindorbit.ui.*
import kotlinx.coroutines.*

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var timeout: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val id = intent.getStringExtra("id")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) } ?: run { finish(); return }
        val app = application as MindOrbitApp
        lifecycleScope.launch {
            val r = app.repository.dao.get(id)?.reminder ?: run { finish(); return@launch }
            if (!r.alarm || r.status != "Active") { finish(); return@launch }
            val due = intent.getLongExtra("due", r.dueAt)
            val prefs = app.preferences.flow
            runCatching {
                Sounds.uri(this@AlarmActivity, r.sound, true)?.let { uri -> ringtone = RingtoneManager.getRingtone(this@AlarmActivity, uri).apply { audioAttributes = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build(); if (Build.VERSION.SDK_INT >= 28) isLooping = true; play() } }
                if (r.vibrate) { vibrator = getSystemService(Vibrator::class.java); vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 700), 0)) }
            }
            timeout = lifecycleScope.launch { delay(60000); stopSound() }
            setContent {
                val settings by prefs.collectAsState(initial = OrbitPreferences())
                var snooze by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
                OrbitTheme(settings) { Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Alarm, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("MindOrbit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp))
                        Text(r.title, style = MaterialTheme.typography.headlineLarge)
                        Text(r.notes, Modifier.padding(vertical = 24.dp))
                        Button(onClick = { stopSound(); lifecycleScope.launch { runCatching { app.repository.complete(id, due) }.onSuccess { finish() }.onFailure { error = "Could not dismiss this reminder. Please try again." } } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Dismiss") }
                        OutlinedButton(onClick = { stopSound(); snooze = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Snooze") }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    if (snooze) SnoozeDialog({ snooze = false }, settings.snooze) { minutes -> lifecycleScope.launch { runCatching { app.repository.snooze(id, minutes, due) }.onSuccess { finish() }.onFailure { error = "Could not snooze. Please try again."; snooze = false } } }
                } }
            }
        }
    }
    private fun stopSound() { ringtone?.stop(); vibrator?.cancel() }
    override fun onStop() { stopSound(); super.onStop() }
    override fun onDestroy() { timeout?.cancel(); stopSound(); super.onDestroy() }
}
