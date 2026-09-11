package app.mindorbit.data

import app.mindorbit.domain.Validation
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import android.util.JsonReader
import android.util.JsonToken

data class Backup(val categories: List<Category>, val records: List<ReminderRecord>, val history: List<History>)

object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    fun read(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= MAX_BYTES) { "Backup exceeds the 10 MB limit." }; output.write(buffer, 0, count) }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        return bytes.toString(Charsets.UTF_8)
    }
    fun encode(backup: Backup): String = JSONObject().apply {
        put("format", "MindOrbit"); put("version", 1)
        put("categories", JSONArray(backup.categories.map { JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("builtIn", it.builtIn) }))
        put("reminders", JSONArray(backup.records.map { record ->
            val r = record.reminder
            JSONObject().apply {
                put("id", r.id); put("title", r.title); put("notes", r.notes); put("categoryId", r.categoryId)
                put("dueAt", r.dueAt); put("localDateTime", r.localDateTime); put("zoneId", r.zoneId)
                put("priority", r.priority); put("repeat", r.repeat); put("interval", r.interval); put("weekdays", r.weekdays)
                put("endAt", r.endAt ?: JSONObject.NULL); put("maxOccurrences", r.maxOccurrences ?: JSONObject.NULL)
                put("occurrenceIndex", r.occurrenceIndex); put("anchorDay", r.anchorDay); put("anchorMonth", r.anchorMonth)
                put("status", r.status); put("alarm", r.alarm); put("notify", r.notify); put("sound", r.sound); put("vibrate", r.vibrate)
                put("randomThought", r.randomThought); put("amount", r.amount); put("reference", r.reference)
                put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
                put("fields", JSONArray(record.fields.map { JSONObject().put("name", it.name).put("value", it.value).put("type", it.type) }))
                put("tags", JSONArray(record.tags.map { it.name }))
            }
        }))
        put("history", JSONArray(backup.history.map { JSONObject().put("id", it.id).put("reminderId", it.reminderId).put("title", it.title).put("event", it.event).put("at", it.at).put("occurrenceAt", it.occurrenceAt) }))
    }.toString(2)
    fun decode(text: String): Backup {
        require(text.toByteArray().size <= MAX_BYTES) { "Backup is too large." }
        JsonReader(text.reader()).use { reader ->
            fun visit(depth: Int) {
                require(depth <= 12) { "Backup nesting is too deep." }
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> { reader.beginObject(); val names = mutableSetOf<String>(); while (reader.hasNext()) { require(names.add(reader.nextName())) { "Duplicate JSON key." }; visit(depth + 1) }; reader.endObject() }
                    JsonToken.BEGIN_ARRAY -> { reader.beginArray(); var count = 0; while (reader.hasNext()) { require(++count <= 100000) { "Too many values." }; visit(depth + 1) }; reader.endArray() }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("Invalid backup structure.")
                }
            }
            visit(0); require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected data after backup." }
        }
        val root = JSONObject(text)
        require(root.getString("format") == "MindOrbit" && root.getInt("version") == 1) { "Unsupported backup format or version." }
        val categories = root.getJSONArray("categories").objects(1000).map { Category(it.getString("id"), it.getString("name"), it.optString("icon", "label"), it.optBoolean("builtIn")) }
        require(categories.all { it.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) && it.name.length in 1..50 && it.icon.length <= 100 }) { "Invalid category." }
        require(categories.map { it.id }.distinct().size == categories.size && categories.map { it.name.lowercase() }.distinct().size == categories.size) { "Duplicate categories." }
        val records = root.getJSONArray("reminders").objects(10000).map { j ->
            val r = Reminder(id = j.getString("id"), title = j.getString("title"), notes = j.getString("notes"), categoryId = j.getString("categoryId"), dueAt = j.getLong("dueAt"), localDateTime = j.getString("localDateTime"), zoneId = j.getString("zoneId"), priority = j.getString("priority"), repeat = j.getString("repeat"), interval = j.getInt("interval"), weekdays = j.getString("weekdays"), endAt = if (j.isNull("endAt")) null else j.getLong("endAt"), maxOccurrences = if (j.isNull("maxOccurrences")) null else j.getInt("maxOccurrences"), occurrenceIndex = j.getInt("occurrenceIndex"), anchorDay = j.getInt("anchorDay"), anchorMonth = j.getInt("anchorMonth"), status = j.getString("status"), alarm = j.getBoolean("alarm"), notify = j.getBoolean("notify"), sound = j.getString("sound"), vibrate = j.getBoolean("vibrate"), randomThought = j.getBoolean("randomThought"), amount = j.getString("amount"), reference = j.getString("reference"), createdAt = j.getLong("createdAt"), updatedAt = j.getLong("updatedAt"))
            val fields = j.getJSONArray("fields").objects(40).map { CustomField(r.id, it.getString("name"), it.getString("value"), it.getString("type")) }
            val tagsArray = j.getJSONArray("tags")
            require(tagsArray.length() <= 30) { "Too many tags." }
            val tags = (0 until tagsArray.length()).map { tagsArray.getString(it).trim().lowercase() }.distinct().map { Tag(it, it) }
            require(tags.all { it.name.length in 1..50 }) { "Invalid tag." }
            Validation.validate(r, fields)
            require(categories.any { it.id == r.categoryId }) { "Unknown category." }
            ReminderRecord(r, fields, tags)
        }
        require(records.map { it.reminder.id }.distinct().size == records.size) { "Duplicate reminder identifiers." }
        val history = root.getJSONArray("history").objects(100000).map { History(it.getString("id"), it.getString("reminderId"), it.getString("title"), it.getString("event"), it.getLong("at"), it.getLong("occurrenceAt")) }
        require(history.all { it.id.length in 1..100 && it.reminderId.length in 1..100 && it.title.length <= 200 && it.event.length <= 100 && it.at >= 0 }) { "Invalid history." }
        return Backup(categories, records, history)
    }
    private fun JSONArray.objects(limit: Int): List<JSONObject> { require(length() <= limit) { "Backup contains too many records." }; return (0 until length()).map { getJSONObject(it) } }
    fun csv(records: List<ReminderRecord>, categories: List<Category>): String {
        fun cell(value: String): String {
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        return "Title,Notes,Category,Due,Priority,Repeat,Status,Amount,Reference,Tags\r\n" + records.joinToString("\r\n") { record ->
            val r = record.reminder
            listOf(r.title, r.notes, categories.firstOrNull { it.id == r.categoryId }?.name ?: r.categoryId, java.time.Instant.ofEpochMilli(r.dueAt).toString(), r.priority, r.repeat, r.status, r.amount, r.reference, record.tags.joinToString("; ") { it.name }).joinToString(",") { cell(it) }
        }
    }
}
