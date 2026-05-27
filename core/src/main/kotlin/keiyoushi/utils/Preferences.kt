package keiyoushi.utils

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Returns the [SharedPreferences] associated with current source id
 */
inline fun HttpSource.getPreferences(
    migration: SharedPreferences.() -> Unit = { },
): SharedPreferences = getPreferences(id).also(migration)

/**
 * Lazily returns the [SharedPreferences] associated with current source id
 */
inline fun HttpSource.getPreferencesLazy(
    crossinline migration: SharedPreferences.() -> Unit = { },
) = lazy { getPreferences(migration) }

/**
 * Returns the [SharedPreferences] associated with passed source id
 */
fun getPreferences(sourceId: Long): SharedPreferences = applicationContext.getSharedPreferences("source_$sourceId", 0x0000)
