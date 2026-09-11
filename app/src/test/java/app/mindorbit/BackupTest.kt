package app.mindorbit

import android.app.Application
import app.mindorbit.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BackupTest {
    private fun backup() = Backup(Catalog.categories, listOf(ReminderRecord(Reminder(title = "Keep me", notes = "line 1\nline 2", amount = "100.25"))), emptyList())
    @Test fun roundTripPreservesData() { val data = backup(); assertEquals(data, BackupCodec.decode(BackupCodec.encode(data))) }
    @Test(expected = Exception::class) fun malformedJsonRejected() { BackupCodec.decode("{corrupt") }
    @Test(expected = IllegalArgumentException::class) fun futureVersionRejected() { BackupCodec.decode(JSONObject(BackupCodec.encode(backup())).put("version", 2).toString()) }
    @Test(expected = IllegalArgumentException::class) fun invalidCategoryRejected() { BackupCodec.decode(BackupCodec.encode(backup().copy(records = listOf(ReminderRecord(Reminder(title = "x", categoryId = "missing")))))) }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsRejected() { val data = backup(); BackupCodec.decode(BackupCodec.encode(data.copy(records = data.records + data.records))) }
    @Test(expected = IllegalArgumentException::class) fun oversizedInputRejected() { BackupCodec.read(ByteArray(BackupCodec.MAX_BYTES + 1).inputStream()) }
    @Test fun csvEscapesQuotesAndNewlines() { val csv = BackupCodec.csv(listOf(ReminderRecord(Reminder(title = "a,\"b\"", notes = "line\nnext"))), Catalog.categories); assertTrue(csv.contains("\"a,\"\"b\"\"\"")); assertTrue(csv.contains("\"line\nnext\"")) }
    @Test fun csvNeutralizesFormula() { val csv = BackupCodec.csv(listOf(ReminderRecord(Reminder(title = "=SUM(1,2)"))), Catalog.categories); assertTrue(csv.contains("\"'=SUM(1,2)\"")) }
    @Test(expected = Exception::class) fun invalidFieldDateRejected() { val r = Reminder(title = "x"); BackupCodec.decode(BackupCodec.encode(backup().copy(records = listOf(ReminderRecord(r, listOf(CustomField(r.id, "Date", "2026-99-99", "date"))))))) }
}
