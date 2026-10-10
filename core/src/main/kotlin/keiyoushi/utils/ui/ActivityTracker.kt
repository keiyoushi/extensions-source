package keiyoushi.utils.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import keiyoushi.utils.applicationContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * The foreground Activity, ready to host a dialog.
 * Must be called on the main thread.
 * @throws IllegalStateException if no usable Activity is available.
 */
fun topActivity(): Activity = ActivityTracker.top()
    ?: throw IllegalStateException("No Activity found to show dialog")

fun Activity.usable(): Boolean = !isFinishing && !isDestroyed

private object ActivityTracker {
    private const val SHARED_KEY = "keiyoushi.activity-tracker.current"

    @Volatile
    private var current: AtomicReference<WeakReference<Activity>?>? = null

    @Volatile
    private var reflectionFailed = false

    private val lock = Any()

    @Suppress("UNCHECKED_CAST")
    fun ensureRegistered() {
        if (current != null) return
        synchronized(lock) {
            if (current != null) return

            val fresh = AtomicReference<WeakReference<Activity>?>(null)
            val props = System.getProperties()
            val existing = props.putIfAbsent(SHARED_KEY, fresh)

            if (existing != null) {
                current = existing as AtomicReference<WeakReference<Activity>?>
                return
            }

            current = fresh
            runCatching { register(fresh) }.onFailure { e ->
                props.remove(SHARED_KEY, fresh)
                current = null
                Log.e("ActivityTracker", "Failed to attach activity listener", e)
            }
        }
    }

    private fun register(holder: AtomicReference<WeakReference<Activity>?>) {
        applicationContext.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(a: Activity) {
                    holder.set(WeakReference(a))
                }

                override fun onActivityPaused(a: Activity) {
                    if (holder.get()?.get() === a) holder.set(null)
                }

                override fun onActivityDestroyed(a: Activity) {
                    if (holder.get()?.get() === a) holder.set(null)
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            },
        )
    }

    fun top(): Activity? {
        ensureRegistered()
        val holder = current ?: return null
        holder.get()?.get()?.takeIf { it.usable() }?.let { return it }
        return findResumedActivity()
            ?.takeIf { it.usable() }
            ?.also { holder.set(WeakReference(it)) }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun findResumedActivity(): Activity? {
        if (reflectionFailed) return null
        return runCatching {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getMethod("currentActivityThread").invoke(null)
            val activities = threadClass.getDeclaredField("mActivities")
                .apply { isAccessible = true }
                .get(thread) as Map<*, *>

            activities.values.firstNotNullOfOrNull { record ->
                val recordClass = record!!.javaClass
                val paused = recordClass.getDeclaredField("paused")
                    .apply { isAccessible = true }.getBoolean(record)
                if (paused) {
                    null
                } else {
                    recordClass.getDeclaredField("activity")
                        .apply { isAccessible = true }.get(record) as? Activity
                }
            }
        }.onFailure { e ->
            reflectionFailed = true
            Log.e("ActivityTracker", "Failed to find resumed Activity", e)
        }.getOrNull()
    }
}

/**
 * Runs [block] once when this Activity is destroyed. Returns a function that
 * unregisters the hook (safe to call multiple times or after destroy).
 */
internal fun Activity.onDestroyed(block: () -> Unit): () -> Unit {
    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(a: Activity) {
            if (a !== this@onDestroyed) return
            application.unregisterActivityLifecycleCallbacks(this)
            block()
        }

        override fun onActivityResumed(a: Activity) = Unit
        override fun onActivityPaused(a: Activity) = Unit
        override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) = Unit
        override fun onActivityStarted(a: Activity) = Unit
        override fun onActivityStopped(a: Activity) = Unit
        override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) = Unit
    }
    application.registerActivityLifecycleCallbacks(callbacks)
    return { application.unregisterActivityLifecycleCallbacks(callbacks) }
}
