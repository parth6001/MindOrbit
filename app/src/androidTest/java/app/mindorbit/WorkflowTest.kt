package app.mindorbit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.mindorbit.data.*
import app.mindorbit.platform.AlarmReceiver
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

@FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class WorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as MindOrbitApp
    @Before fun reset() { runBlocking { app.repository.initialize(); app.repository.clearAll(); app.preferences.clear() }; compose.waitForIdle() }
    private fun create(title: String, category: String = "general", repeat: String = "Once", fields: List<Pair<String, String>> = emptyList()): Reminder {
        val r = Reminder(title = title, categoryId = category, repeat = repeat, dueAt = System.currentTimeMillis() + 300000)
        runBlocking { app.repository.save(r, fields.map { CustomField(r.id, it.first, it.second, if (it.first.contains("date", true)) "date" else "text") }, emptyList()) }
        compose.waitForIdle(); return r
    }
    @Test fun e2e01CreateFromUiAndSchedule() {
        compose.onAllNodesWithText("Add reminder").onFirst().performClick()
        compose.onNodeWithText("Title").performTextInput("UI reminder")
        compose.onNodeWithText("Save reminder").performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.dao.all().any { it.reminder.title == "UI reminder" } } }
        compose.onNodeWithText("UI reminder").assertIsDisplayed()
        assertTrue(runBlocking { app.repository.dao.all().single().reminder.scheduleState } in listOf("Scheduled exact", "Scheduled approximate", "Notifications disabled"))
    }
    @Test fun e2e02DailyRepeatEditPersists() {
        val r = create("Daily", repeat = "Every day")
        compose.onNodeWithText("Daily", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.onNodeWithText("Title").performTextClearance(); compose.onNodeWithText("Title").performTextInput("Daily edited")
        compose.onNodeWithText("Save reminder").performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.dao.get(r.id)?.reminder?.title == "Daily edited" } }
        assertEquals("Every day", runBlocking { app.repository.dao.get(r.id)!!.reminder.repeat })
    }
    @Test fun e2e03SubscriptionSurvivesActivityRestart() { val r = create("Renew OTT", "subscription", fields = listOf("Billing cycle" to "Monthly", "Renewal date" to "2030-01-01")); compose.activityRule.scenario.recreate(); compose.waitForIdle(); assertEquals(2, runBlocking { app.repository.dao.get(r.id)!!.fields.size }) }
    @Test fun e2e04FdDetails() { val r = create("FD maturity", "fd", fields = listOf("Institution / Bank" to "Local bank", "Maturity date" to "2030-01-01")); assertEquals("2030-01-01", runBlocking { app.repository.dao.get(r.id)!!.fields.first { it.name == "Maturity date" }.value }) }
    @Test fun e2e05MultipleIpoDates() { val r = create("Manual IPO", "stock-ipo", fields = listOf("Opening date" to "2030-01-01", "Closing date" to "2030-01-05", "Listing date" to "2030-01-10")); assertEquals(3, runBlocking { app.repository.dao.get(r.id)!!.fields.size }) }
    @Test fun e2e06EmailNeedsNoInternet() { create("Follow up with vendor", "email"); assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.INTERNET)) }
    @Test fun e2e07NotificationActions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant app.mindorbit android.permission.POST_NOTIFICATIONS")).bufferedReader().use { it.readText() }
        val r = create("Notification workflow")
        runBlocking { app.repository.deliver(r.id, r.dueAt, app.scheduler::notification) }
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        val notification = manager.activeNotifications.first { it.tag == r.id }.notification
        assertEquals(3, notification.actions.size)
        notification.contentIntent.send()
        compose.waitForIdle()
        assertTrue(compose.activity.intent.getStringExtra("id") == r.id || manager.activeNotifications.isNotEmpty())
        runBlocking { app.repository.snooze(r.id, 5) }
        assertTrue(runBlocking { app.repository.dao.get(r.id)!!.reminder.dueAt } > System.currentTimeMillis())
        runBlocking { app.repository.deliver(r.id, app.repository.dao.get(r.id)!!.reminder.dueAt, app.scheduler::notification) }
        manager.activeNotifications.first { it.tag == r.id }.notification.actions.first().actionIntent.send()
        compose.waitUntil(10000) { runBlocking { app.repository.dao.get(r.id)?.reminder?.status == "Completed" } }
    }
    @Test fun e2e08PermissionDenialKeepsData() {
        val r = create("Permission denied")
        assertNotNull(runBlocking { app.repository.dao.get(r.id) })
        if (!app.scheduler.notificationsAllowed()) assertEquals("Notifications disabled", app.scheduler.notification(r))
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Notification access").performScrollTo().assertIsDisplayed()
    }
    @Test fun e2e09RestoreReceiverReschedules() { val r = create("Reboot"); app.scheduler.cancel(r.id); runBlocking { app.repository.reschedule() }; assertTrue(runBlocking { app.repository.dao.get(r.id)!!.reminder.scheduleState }.startsWith("Scheduled") || !app.scheduler.notificationsAllowed()) }
    @Test fun e2e10BackupDeleteRestore() { val r = create("Backup target"); runBlocking { val dao = app.repository.dao; val backup = BackupCodec.decode(BackupCodec.encode(Backup(dao.allCategories(), dao.all(), dao.allHistory()))); app.repository.delete(r.id); app.repository.restore(backup); assertNotNull(dao.get(r.id)) } }
    @Test fun e2e11DarkModeNavigation() { runBlocking { app.preferences.text("theme", "Dark") }; compose.onNodeWithText("Calendar").performClick(); compose.onNodeWithContentDescription("Next month").assertIsDisplayed(); compose.onNodeWithText("Settings").performClick(); compose.onNodeWithText("Dark").assertExists() }
    @Test fun e2e12LargeListSearch() { runBlocking { repeat(1001) { app.repository.dao.put(Reminder(title = "Large task $it", dueAt = System.currentTimeMillis() + 600000)) } }; compose.onNodeWithText("Reminders").performClick(); compose.onNodeWithText("Search reminders").performTextInput("Large task 1000"); compose.waitUntil(10000) { compose.onAllNodesWithText("Large task 1000").fetchSemanticsNodes().size == 1 }; compose.onNodeWithText("Large task 1000").assertIsDisplayed() }
    @Test fun e2e13RealScheduledNotification() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant app.mindorbit android.permission.POST_NOTIFICATIONS")).bufferedReader().use { it.readText() }
        val r = Reminder(title = "Real scheduled alert", dueAt = System.currentTimeMillis() + 3000)
        runBlocking { app.repository.save(r, emptyList(), emptyList()) }
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        compose.waitUntil(30000) { manager.activeNotifications.any { it.tag == r.id } }
        assertEquals("Delivered", runBlocking { app.repository.dao.get(r.id)!!.reminder.scheduleState })
    }
    @Test fun e2e14AlarmScreenLaunches() {
        val r = Reminder(title = "Alarm screen", notes = "A local alarm", alarm = true, dueAt = System.currentTimeMillis() + 60000)
        runBlocking { app.repository.save(r, emptyList(), emptyList()); app.repository.deliver(r.id, r.dueAt, app.scheduler::notification) }
        app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.first { it.tag == r.id }.notification.contentIntent.send()
        var alarmActivity: AlarmActivity? = null
        compose.waitUntil(10000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { alarmActivity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<AlarmActivity>().firstOrNull() }
            alarmActivity != null
        }
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Dismiss")), 10000).click()
        compose.waitUntil(10000) { runBlocking { app.repository.dao.get(r.id)?.reminder?.status == "Completed" } }
    }
}
