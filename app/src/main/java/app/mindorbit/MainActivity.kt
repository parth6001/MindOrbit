package app.mindorbit

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mindorbit.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); incoming = intent
        val app = application as MindOrbitApp
        setContent {
            val vm: OrbitViewModel = viewModel(factory = object : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T { @Suppress("UNCHECKED_CAST") return OrbitViewModel(app.repository, app.preferences) as T } })
            val settings by vm.settings.collectAsStateWithLifecycle()
            val backgroundError by app.backgroundError.collectAsStateWithLifecycle()
            LaunchedEffect(backgroundError) { backgroundError?.let { vm.error.value = it; app.backgroundError.value = null } }
            OrbitTheme(settings) { OrbitApp(vm, incoming) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); incoming = intent }
    override fun onResume() { super.onResume(); val app = application as MindOrbitApp; app.applicationScope.launchReschedule(app) }
}

private fun kotlinx.coroutines.CoroutineScope.launchReschedule(app: MindOrbitApp) = launch { runCatching { app.repository.reschedule() }.onFailure { app.backgroundError.value = "Could not restore reminder scheduling. Please restart MindOrbit." } }
