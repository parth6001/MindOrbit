package app.mindorbit.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.orbitPreferences by preferencesDataStore("preferences")
data class OrbitPreferences(val theme: String = "System", val sound: String = "Standard", val vibrate: Boolean = true, val snooze: Int = 10, val mondayFirst: Boolean = true, val time24: Boolean = false, val dateFormat: String = "dd MMM yyyy", val alarm: Boolean = false, val dynamicColor: Boolean = false)
class Preferences(context: Context) {
    private val store = context.orbitPreferences
    val flow = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { OrbitPreferences(it[stringPreferencesKey("theme")] ?: "System", it[stringPreferencesKey("sound")] ?: "Standard", it[booleanPreferencesKey("vibrate")] ?: true, it[intPreferencesKey("snooze")] ?: 10, it[booleanPreferencesKey("monday")] ?: true, it[booleanPreferencesKey("time24")] ?: false, it[stringPreferencesKey("date")] ?: "dd MMM yyyy", it[booleanPreferencesKey("alarm")] ?: false, it[booleanPreferencesKey("dynamic")] ?: false) }
    suspend fun text(key: String, value: String) { store.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun flag(key: String, value: Boolean) { store.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun number(key: String, value: Int) { store.edit { it[intPreferencesKey(key)] = value } }
    suspend fun clear() { store.edit { it.clear() } }
}
