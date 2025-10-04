package eu.kanade.tachiyomi.extension.en.bookwalker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration

class Expiring<T>(
    private val obj: T,
    private val expirationTime: Duration,
    val onExpire: T.() -> Unit,
) {

    private var lastAccessed = System.currentTimeMillis()

    private var expired = false

    val contents
        get() = run {
            lastAccessed = System.currentTimeMillis()
            obj
        }

    private var expirationJob: Job

    fun expire() {
        if (!expired) {
            expired = true
            expirationJob.cancel()
            onExpire(obj)
        }
    }

    init {
        expirationJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                yield()
                val targetTime = lastAccessed + expirationTime.inWholeMilliseconds
                val currentTime = System.currentTimeMillis()
                val difference = targetTime - currentTime
                if (difference > 0) {
                    delay(difference)
                } else {
                    break
                }
            }
            expire()
        }
    }
}
