package app.mindorbit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mindorbit.data.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

class FakeScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<String, Reminder>(); val dismissed = mutableListOf<String>(); var fail = false
    override fun schedule(reminder: Reminder): String { if (fail) error("denied"); scheduled[reminder.id] = reminder; return "Scheduled exact" }
    override fun cancel(id: String) { scheduled.remove(id) }
    override fun dismiss(id: String) { dismissed.add(id) }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RepositoryTest {
    private lateinit var db: OrbitDatabase; private lateinit var scheduler: FakeScheduler; private lateinit var repo: ReminderRepository
    private val now = 1800000000000L
    @Before fun setup() = runTest { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OrbitDatabase::class.java).allowMainThreadQueries().build(); scheduler = FakeScheduler(); repo = ReminderRepository(db, scheduler) { now }; repo.initialize() }
    @After fun close() { db.close() }
    private fun reminder() = Reminder(title = "Renewal", dueAt = now + 600000)
    @Test fun savesRelationsAndScheduledState() = runTest { val r = reminder(); repo.save(r, listOf(CustomField(r.id, "Bank", "Local bank")), listOf("Money")); val saved = repo.dao.get(r.id)!!; assertEquals("Scheduled exact", saved.reminder.scheduleState); assertEquals("Local bank", saved.fields.single().value); assertEquals("money", saved.tags.single().name); assertTrue(scheduler.scheduled.containsKey(r.id)) }
    @Test fun editPreservesRepeat() = runTest { val r = reminder().copy(repeat = "Every day"); repo.save(r, emptyList(), emptyList()); repo.save(repo.dao.get(r.id)!!.reminder.copy(title = "Edited"), emptyList(), emptyList()); assertEquals("Every day", repo.dao.get(r.id)!!.reminder.repeat) }
    @Test fun completeCancelsOneTime() = runTest { val r = reminder(); repo.save(r, emptyList(), emptyList()); repo.complete(r.id); assertEquals("Completed", repo.dao.get(r.id)!!.reminder.status); assertFalse(scheduler.scheduled.containsKey(r.id)); assertEquals("Completed", repo.dao.allHistory().single().event) }
    @Test fun repeatAdvancesAtDelivery() = runTest { val r = reminder().copy(repeat = "Every day"); repo.save(r, emptyList(), emptyList()); repo.deliver(r.id, r.dueAt) { "Delivered" }; assertEquals(r.dueAt + 86400000, repo.dao.get(r.id)!!.reminder.dueAt); assertTrue(scheduler.scheduled.containsKey(r.id)) }
    @Test fun oldActionCannotCompleteNextOccurrence() = runTest { val r = reminder().copy(repeat = "Every day"); repo.save(r, emptyList(), emptyList()); repo.deliver(r.id, r.dueAt) { "Delivered" }; val next = repo.dao.get(r.id)!!.reminder.dueAt; repo.complete(r.id, r.dueAt); assertEquals(next, repo.dao.get(r.id)!!.reminder.dueAt); assertEquals("Active", repo.dao.get(r.id)!!.reminder.status) }
    @Test fun staleDeliveryIgnored() = runTest { val r = reminder(); repo.save(r, emptyList(), emptyList()); var called = false; repo.deliver(r.id, r.dueAt - 1) { called = true; "Delivered" }; assertFalse(called) }
    @Test fun simultaneousRemindersDeliverTogether() = runTest { val first = reminder().copy(dueAt = now - 1); val second = reminder().copy(dueAt = now - 1); repo.saveBatch(listOf(ReminderRecord(first), ReminderRecord(second))); var count = 0; repo.deliverDue(first.id, first.dueAt) { count++; "Delivered" }; assertEquals(2, count); assertTrue(scheduler.scheduled.isEmpty()) }
    @Test fun duplicateDeliveryIgnored() = runTest { val r = reminder(); repo.save(r, emptyList(), emptyList()); var calls = 0; repeat(2) { repo.deliver(r.id, r.dueAt) { calls++; "Delivered" } }; assertEquals(1, calls) }
    @Test fun snoozeSchedulesFutureTime() = runTest { val r = reminder(); repo.save(r, emptyList(), emptyList()); repo.snooze(r.id, 15); assertEquals(now + 900000, repo.dao.get(r.id)!!.reminder.dueAt); assertEquals("Snoozed", repo.dao.allHistory().single().event) }
    @Test fun archiveCancels() = runTest { val r = reminder(); repo.save(r, emptyList(), emptyList()); repo.archive(r.id); assertEquals("Archived", repo.dao.get(r.id)!!.reminder.status); assertTrue(scheduler.scheduled.isEmpty()) }
    @Test fun deleteCascadesRelations() = runTest { val r = reminder(); repo.save(r, listOf(CustomField(r.id, "x", "y")), listOf("tag")); repo.delete(r.id); assertNull(repo.dao.get(r.id)); assertTrue(repo.dao.all().isEmpty()) }
    @Test fun duplicateHasDistinctIdentity() = runTest { val r = reminder(); repo.save(r, emptyList(), emptyList()); val copy = repo.duplicate(r.id); assertNotEquals(r.id, copy); assertEquals(2, repo.dao.all().size) }
    @Test fun scheduleFailureIsPersisted() = runTest { scheduler.fail = true; val r = reminder(); repo.save(r, emptyList(), emptyList()); assertTrue(repo.dao.get(r.id)!!.reminder.scheduleState.contains("failed")) }
    @Test fun rebootReschedulesStoredReminders() = runTest { val r = reminder(); repo.save(r, emptyList(), emptyList()); scheduler.scheduled.clear(); repo.reschedule(); assertTrue(scheduler.scheduled.containsKey(r.id)) }
    @Test fun backupRestoresAfterDelete() = runTest { val r = reminder(); repo.save(r, listOf(CustomField(r.id, "Maturity date", "2030-01-01", "date")), listOf("fd")); val backup = BackupCodec.decode(BackupCodec.encode(Backup(repo.dao.allCategories(), repo.dao.all(), repo.dao.allHistory()))); repo.delete(r.id); repo.restore(backup); assertEquals("2030-01-01", repo.dao.get(r.id)!!.fields.single().value); assertTrue(scheduler.scheduled.containsKey(r.id)) }
    @Test fun thousandRecordsRemainSearchable() = runTest { repeat(1001) { repo.dao.put(reminder().copy(title = "Task $it")) }; val start = System.nanoTime(); val records = repo.dao.all(); val found = app.mindorbit.ui.ReminderFilter.apply(records, repo.dao.allCategories(), query = "Task 1000", now = now); assertEquals(1, found.size); assertTrue("Read/search should finish within 5 seconds", System.nanoTime() - start < 5_000_000_000L) }
    @Test fun deleteAllClearsData() = runTest { repo.save(reminder(), emptyList(), listOf("x")); repo.clearAll(); assertTrue(repo.dao.all().isEmpty()); assertTrue(scheduler.scheduled.isEmpty()); assertEquals(Catalog.categories.size, repo.dao.allCategories().size) }
}
