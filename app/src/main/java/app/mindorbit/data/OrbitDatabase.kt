package app.mindorbit.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrbitDao {
    @Transaction @Query("SELECT * FROM reminders ORDER BY dueAt") fun observe(): Flow<List<ReminderRecord>>
    @Transaction @Query("SELECT * FROM reminders ORDER BY dueAt") suspend fun all(): List<ReminderRecord>
    @Transaction @Query("SELECT * FROM reminders WHERE id = :id") suspend fun get(id: String): ReminderRecord?
    @Query("SELECT * FROM categories ORDER BY builtIn DESC, name") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM history ORDER BY at DESC LIMIT 5000") fun history(): Flow<List<History>>
    @Query("SELECT * FROM history") suspend fun allHistory(): List<History>
    @Upsert suspend fun put(reminder: Reminder)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun category(category: Category)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun tag(tag: Tag)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun links(links: List<ReminderTag>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun fields(fields: List<CustomField>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun event(event: History)
    @Query("DELETE FROM fields WHERE reminderId = :id") suspend fun clearFields(id: String)
    @Query("DELETE FROM reminder_tags WHERE reminderId = :id") suspend fun clearTags(id: String)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM tags") suspend fun clearAllTags()
    @Query("DELETE FROM categories") suspend fun clearCategories()
}

@Database(entities = [Reminder::class, Category::class, Tag::class, ReminderTag::class, CustomField::class, History::class], version = 1, exportSchema = true)
abstract class OrbitDatabase : RoomDatabase() { abstract fun dao(): OrbitDao }
