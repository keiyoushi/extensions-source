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
 * Base class for helpers that need to show UI (dialogs) on top of whatever Activity is
 * currently in the foreground.
 *
 * Activity tracking is shared process-wide, so creating many helpers registers lifecycle
 * callbacks only once.
 */
abstract class ActivityTrackingHelper {

    init {
        ActivityTracker.ensureRegistered()
    }

    /**
     * The foreground Activity, ready to host a dialog.
     * Must be called on the main thread.
     * @throws Exception if no usable Activity is available.
     */
    protected fun topActivity(): Activity = ActivityTracker.top()
        ?: throw IllegalStateException("No Activity found to show dialog")

    protected fun Activity.usable(): Boolean = !isFinishing && !isDestroyed
}

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
        val holder = current ?: return null
        holder.get()?.get()?.takeIf { it.usable() }?.let { return it }
        return findResumedActivity()
            ?.takeIf { it.usable() }
            ?.also { holder.set(WeakReference(it)) }
    }

    private fun Activity.usable() = !isFinishing && !isDestroyed

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
