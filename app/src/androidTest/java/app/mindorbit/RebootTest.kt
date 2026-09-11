package app.mindorbit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mindorbit.data.Reminder
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RebootSeedTest {
    @Test fun seedPendingReminder() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as MindOrbitApp
        app.repository.initialize()
        app.repository.save(Reminder(id = "reboot-verification", title = "Reboot verification", dueAt = System.currentTimeMillis() + 1200000), emptyList(), emptyList())
        assertNotNull(app.repository.dao.get("reboot-verification"))
    }
}
@RunWith(AndroidJUnit4::class)
class RebootVerifyTest {
    @Test fun pendingReminderSurvived() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as MindOrbitApp
        val record = app.repository.dao.get("reboot-verification")
        assertNotNull("Run RebootSeedTest before rebooting the emulator", record)
        withTimeout(10000) { while (app.repository.dao.get("reboot-verification")!!.reminder.scheduleState == "Pending") delay(100) }
        assertEquals("Active", record!!.reminder.status)
    }
}
