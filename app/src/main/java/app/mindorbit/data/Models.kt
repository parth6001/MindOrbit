package app.mindorbit.data

import androidx.room.*

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(@PrimaryKey val id: String, val name: String, val icon: String = "label", val builtIn: Boolean = false)

@Entity(tableName = "reminders", indices = [Index("dueAt"), Index("status"), Index("categoryId")])
data class Reminder(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "", val notes: String = "", val categoryId: String = "general",
    val dueAt: Long = System.currentTimeMillis(), val localDateTime: String = "", val zoneId: String = java.time.ZoneId.systemDefault().id,
    val priority: String = "Normal", val repeat: String = "Once", val interval: Int = 1,
    val weekdays: String = "", val endAt: Long? = null, val maxOccurrences: Int? = null,
    val occurrenceIndex: Int = 1, val anchorDay: Int = 0, val anchorMonth: Int = 0,
    val status: String = "Active", val alarm: Boolean = false, val notify: Boolean = true,
    val sound: String = "Standard", val vibrate: Boolean = true, val randomThought: Boolean = false,
    val amount: String = "", val reference: String = "", val scheduleState: String = "Pending",
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(@PrimaryKey val id: String, val name: String)
@Entity(tableName = "reminder_tags", primaryKeys = ["reminderId", "tagId"], foreignKeys = [ForeignKey(entity = Reminder::class, parentColumns = ["id"], childColumns = ["reminderId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tagId")])
data class ReminderTag(val reminderId: String, val tagId: String)
@Entity(tableName = "fields", primaryKeys = ["reminderId", "name"], foreignKeys = [ForeignKey(entity = Reminder::class, parentColumns = ["id"], childColumns = ["reminderId"], onDelete = ForeignKey.CASCADE)])
data class CustomField(val reminderId: String, val name: String, val value: String, val type: String = "text")
@Entity(tableName = "history", indices = [Index("reminderId"), Index("at")])
data class History(@PrimaryKey val id: String = java.util.UUID.randomUUID().toString(), val reminderId: String, val title: String, val event: String, val at: Long = System.currentTimeMillis(), val occurrenceAt: Long = at)

data class ReminderRecord(
    @Embedded val reminder: Reminder,
    @Relation(parentColumn = "id", entityColumn = "reminderId") val fields: List<CustomField> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "id", associateBy = Junction(ReminderTag::class, parentColumn = "reminderId", entityColumn = "tagId")) val tags: List<Tag> = emptyList()
)

object Catalog {
    val categories = listOf("General", "Personal", "Work", "Email", "Subscription", "Bill", "Investment", "FD", "Stock / IPO", "Purchase", "Warranty", "Birthday", "Anniversary", "Health", "Document", "Vehicle", "Daily Thought", "Deadline", "Custom").map { Category(it.lowercase().replace(" / ", "-").replace(" ", "-"), it, it.lowercase(), true) }
    val repeats = listOf("Once", "Every day", "Weekdays", "Weekends", "Weekly", "Every X days", "Monthly", "Yearly", "Custom days", "Custom interval")
    val priorities = listOf("Low", "Normal", "High", "Critical")
    val sounds = listOf("Standard", "Soft", "Gentle", "Digital", "Bell", "Calm", "Silent")
    val thoughts = listOf("Small steps still move you forward.", "Make room for one good thing today.", "Progress begins with your next kind choice.", "You can begin again with what you have.", "Give your attention to what matters today.", "A quiet moment can make a busy day clearer.")
    fun fields(category: String): List<Pair<String, String>> = when (category) {
        "subscription" -> listOf("Billing cycle" to "text", "Auto-renew" to "boolean", "Renewal date" to "date", "Remind days before" to "number")
        "investment", "fd" -> listOf("Institution / Bank" to "text", "Investment type" to "text", "Start date" to "date", "Maturity date" to "date", "Interest rate (%)" to "number", "Expected maturity amount" to "number")
        "stock-ipo" -> listOf("Company name" to "text", "Opening date" to "date", "Closing date" to "date", "Allotment date" to "date", "Listing date" to "date", "Application status" to "text")
        "purchase" -> listOf("Store" to "text", "Purchase date" to "date", "Warranty end" to "date", "Return-window end" to "date", "Service date" to "date")
        "email" -> listOf("Person / company" to "text", "Follow-up date" to "date", "Email status" to "text")
        else -> emptyList()
    }
}
