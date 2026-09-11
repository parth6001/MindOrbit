package app.mindorbit.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mindorbit.*
import app.mindorbit.data.*
import java.time.*
import java.time.format.DateTimeFormatter

class AndroidScheduler(private val context: Context) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val queue = mutableMapOf<String, Reminder>()
    private fun pending(r: Reminder) = PendingIntent.getBroadcast(context, 0, Intent(context, AlarmReceiver::class.java).setAction("app.mindorbit.DUE").putExtra("id", r.id).putExtra("due", r.dueAt), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun arm() {
        val next = queue.values.minByOrNull { it.dueAt } ?: return
        val trigger = maxOf(next.dueAt, System.currentTimeMillis() + 1000)
        val operation = pending(next)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation); return } catch (_: SecurityException) { /* Access can be revoked between check and call. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
    }
    @Synchronized override fun schedule(reminder: Reminder): String {
        queue[reminder.id] = reminder
        arm()
        return if (!notificationsAllowed()) "Notifications disabled" else if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) "Scheduled exact" else "Scheduled approximate"
    }
    @Synchronized override fun cancel(id: String) {
        val removed = queue.remove(id)
        if (removed != null) alarms.cancel(pending(removed))
        arm()
    }
    override fun dismiss(id: String) { NotificationManagerCompat.from(context).cancel(id, 1) }
    fun notificationsAllowed() = NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun notification(r: Reminder): String {
        if (!notificationsAllowed()) return "Notifications disabled"
        val manager = context.getSystemService(NotificationManager::class.java)
        val type = if (r.alarm) "Alarm" else if (r.priority in listOf("High", "Critical")) "Important" else "Normal"
        val channelId = "${type}_${r.sound}_${r.vibrate}_v1"
        val channel = NotificationChannel(channelId, "$type reminders - ${r.sound}${if (r.vibrate) " + vibration" else ""}", if (type == "Normal") NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Locally scheduled MindOrbit reminders"
            enableVibration(r.vibrate)
            setSound(Sounds.uri(context, r.sound, r.alarm), AudioAttributes.Builder().setUsage(if (r.alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION).build())
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
        if (manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) return "Notification channel disabled"
        val openIntent = Intent(context, if (r.alarm) AlarmActivity::class.java else MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(Uri.parse("mindorbit://reminder/${r.id}")).putExtra("id", r.id).putExtra("due", r.dueAt)
        val open = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String): PendingIntent = PendingIntent.getBroadcast(context, 0, Intent(context, ActionReceiver::class.java).setAction(name).setData(Uri.parse("mindorbit://action/${r.id}/$name/${r.dueAt}")).putExtra("id", r.id).putExtra("due", r.dueAt), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(Uri.parse("mindorbit://snooze/${r.id}/${r.dueAt}")).putExtra("id", r.id).putExtra("due", r.dueAt).putExtra("snooze", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notes = if (r.randomThought) Catalog.thoughts.random() else r.notes
        val time = Instant.ofEpochMilli(r.dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM  HH:mm"))
        val category = Catalog.categories.firstOrNull { it.id == r.categoryId }?.name ?: "Reminder"
        val builder = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_notification).setContentTitle(r.title).setContentText("$category - $time").setStyle(NotificationCompat.BigTextStyle().bigText("$category - $time\n$notes")).setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(if (r.alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER).setPriority(if (type == "Normal") NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH).addAction(0, "Mark Done", action("app.mindorbit.DONE")).addAction(0, "Snooze", snooze).addAction(0, "Open", open)
        if (r.alarm && (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent())) builder.setFullScreenIntent(open, true)
        return try { manager.notify(r.id, 1, builder.build()); "Delivered" } catch (_: SecurityException) { "Notifications disabled" }
    }
}

object Sounds {
    fun uri(context: Context, name: String, alarm: Boolean): Uri? {
        if (name == "Silent") return null
        if (name == "Standard") return RingtoneManager.getDefaultUri(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
        val id = when (name) { "Soft" -> R.raw.soft; "Gentle" -> R.raw.gentle; "Digital" -> R.raw.digital; "Bell" -> R.raw.bell; "Calm" -> R.raw.calm; else -> 0 }
        return if (id != 0) Uri.parse("android.resource://${context.packageName}/$id") else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}
