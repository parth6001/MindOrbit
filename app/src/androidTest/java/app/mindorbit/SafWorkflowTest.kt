package app.mindorbit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.mindorbit.data.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SafWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun exportDeleteImportUsingDocumentPicker() {
        val app = compose.activity.application as MindOrbitApp
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val reminder = Reminder(title = "SAF restore verification", dueAt = System.currentTimeMillis() + 3600000)
        runBlocking { app.repository.clearAll(); app.repository.save(reminder, emptyList(), emptyList()) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Export backup").performScrollTo().performClick()
        device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10000)
        val filename = "MindOrbit-test-${System.currentTimeMillis()}.json"
        device.findObject(UiSelector().className("android.widget.EditText")).setText(filename)
        device.findObject(UiSelector().description("Show roots")).let { if (it.exists()) it.click() }
        device.findObject(UiSelector().text("Downloads")).let { if (it.exists()) it.click() }
        device.findObject(UiSelector().textMatches("(?i)save")).click()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Backup exported.").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { app.repository.delete(reminder.id) }
        compose.onNodeWithText("Import backup").performScrollTo().performClick()
        device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10000)
        device.findObject(UiSelector().description("Show roots")).let { if (it.exists()) it.click() }
        device.findObject(UiSelector().text("Downloads")).let { if (it.exists()) it.click() }
        val file = device.wait(Until.findObject(By.text(filename)), 10000)
        assertNotNull("Exported JSON should appear in Downloads", file)
        file.click()
        compose.onNodeWithText("Restore").performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.dao.get(reminder.id) != null } }
        assertEquals(reminder.title, runBlocking { app.repository.dao.get(reminder.id)!!.reminder.title })
    }
}
