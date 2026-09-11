package app.mindorbit

import android.app.*
import androidx.test.core.app.ApplicationProvider
import app.mindorbit.data.Reminder
import app.mindorbit.platform.AndroidScheduler
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AndroidSchedulerTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    @Test fun exactAccessDeniedUsesInexactAlarm() {
        val manager = context.getSystemService(AlarmManager::class.java)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = AndroidScheduler(context)
        scheduler.schedule(Reminder(title = "Fallback", dueAt = System.currentTimeMillis() + 60000))
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
    }
    @Test fun thousandRemindersUseOneSystemAlarm() {
        val manager = context.getSystemService(AlarmManager::class.java)
        val scheduler = AndroidScheduler(context)
        repeat(1001) { scheduler.schedule(Reminder(title = "Task $it", dueAt = System.currentTimeMillis() + 60000 + it)) }
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
    }
    @Test fun cancellingEarliestKeepsNextAlarm() {
        val manager = context.getSystemService(AlarmManager::class.java)
        val scheduler = AndroidScheduler(context)
        val first = Reminder(title = "First", dueAt = System.currentTimeMillis() + 60000)
        val second = Reminder(title = "Second", dueAt = first.dueAt + 60000)
        scheduler.schedule(first); scheduler.schedule(second); scheduler.cancel(first.id)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        scheduler.cancel(second.id); assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }
}
