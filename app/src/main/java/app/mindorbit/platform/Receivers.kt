package app.mindorbit.platform

import android.content.*
import app.mindorbit.MindOrbitApp
import kotlinx.coroutines.launch

private fun validId(intent: Intent): String? = intent.getStringExtra("id")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.mindorbit.DUE") return
        val id = validId(intent) ?: return
        val due = intent.getLongExtra("due", -1).takeIf { it >= 0 } ?: return
        val pending = goAsync()
        val app = context.applicationContext as MindOrbitApp
        app.applicationScope.launch { try { app.repository.deliverDue(id, due, app.scheduler::notification) } catch (_: Exception) { app.backgroundError.value = "A reminder could not be delivered. Open MindOrbit to retry scheduling." } finally { pending.finish() } }
    }
}
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.mindorbit.DONE") return
        val id = validId(intent) ?: return
        val due = intent.getLongExtra("due", -1).takeIf { it >= 0 } ?: return
        val pending = goAsync()
        val app = context.applicationContext as MindOrbitApp
        app.applicationScope.launch { try { app.repository.complete(id, due) } catch (_: Exception) { app.backgroundError.value = "Could not complete the reminder. Open MindOrbit to retry." } finally { pending.finish() } }
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        val app = context.applicationContext as MindOrbitApp
        app.applicationScope.launch { try { app.repository.initialize(); app.repository.reschedule(intent.action == Intent.ACTION_TIMEZONE_CHANGED) } catch (_: Exception) { app.backgroundError.value = "Could not restore reminder scheduling. Open MindOrbit to retry." } finally { pending.finish() } }
    }
}
