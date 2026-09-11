package app.mindorbit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mindorbit.data.*
import app.mindorbit.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ViewModelTest {
    private lateinit var db: OrbitDatabase
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OrbitDatabase::class.java).allowMainThreadQueries().build() }
    @After fun cleanup() { db.close(); Dispatchers.resetMain() }
    @Test fun invalidSaveSurfacesError() = runTest {
        val repo = ReminderRepository(db, FakeScheduler()); repo.initialize(); val vm = OrbitViewModel(repo, Preferences(ApplicationProvider.getApplicationContext()))
        var saved = false; vm.save(Reminder(title = ""), emptyList(), emptyList(), false) { saved = true }.join()
        assertFalse(saved); assertNotNull(vm.error.value); assertTrue(repo.dao.all().isEmpty())
    }
    @Test fun ipoMilestonesHaveStableIds() = runTest {
        val repo = ReminderRepository(db, FakeScheduler()); repo.initialize(); val vm = OrbitViewModel(repo, Preferences(ApplicationProvider.getApplicationContext()))
        val r = Reminder(title = "Offline IPO", categoryId = "stock-ipo"); val fields = listOf(CustomField(r.id, "Closing date", "2030-03-03", "date"), CustomField(r.id, "Listing date", "2030-03-10", "date"))
        repeat(2) { vm.save(r, fields, emptyList(), true) {}.join() }
        assertNull(vm.error.value); assertEquals(3, repo.dao.all().size)
    }
    @Test fun successfulSaveCallsNavigation() = runTest {
        val repo = ReminderRepository(db, FakeScheduler()); repo.initialize(); val vm = OrbitViewModel(repo, Preferences(ApplicationProvider.getApplicationContext()))
        var navigated = false; vm.save(Reminder(title = "A real task"), emptyList(), emptyList(), false) { navigated = true }.join()
        assertTrue(navigated); assertFalse(vm.busy.value)
    }
}
