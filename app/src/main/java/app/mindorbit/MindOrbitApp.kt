package app.mindorbit

import android.app.Application
import androidx.room.Room
import app.mindorbit.data.*
import app.mindorbit.platform.AndroidScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class MindOrbitApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, OrbitDatabase::class.java, "mindorbit.db").build() }
    val scheduler by lazy { AndroidScheduler(this) }
    val repository by lazy { ReminderRepository(database, scheduler) }
    val preferences by lazy { Preferences(this) }
    val backgroundError = MutableStateFlow<String?>(null)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { runCatching { repository.initialize(); repository.reschedule() }.onFailure { backgroundError.value = "Could not restore reminder scheduling. Reopen MindOrbit to retry." } }
    }
}
