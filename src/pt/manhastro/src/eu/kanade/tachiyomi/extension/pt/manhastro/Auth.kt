package eu.kanade.tachiyomi.extension.pt.manhastro

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
class Cookie {
    val value: Pair<String, String>
    val expired: String

    private constructor() {
        value = "" to ""
        expired = ""
    }

    constructor(setCookie: String) {
        val slices = setCookie.split("; ")
        value = slices.first().split("=").let {
            it.first() to it.last()
        }
        expired = Calendar.getInstance().apply {
            time = Date()
            add(Calendar.DATE, EXPIRE_AT)
        }.let { dateFormat.format(it.time) }
    }

    fun isExpired(): Boolean =
        try { dateFormat.parse(expired)!!.before(Date()) } catch (e: Exception) { true }

    fun isEmpty(): Boolean = expired.isEmpty() || value.toList().any(String::isBlank)

    companion object {
        fun empty() = Cookie()
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        private const val EXPIRE_AT = 2
    }
}

@Serializable
class Attempt(
    private var attempts: Long = 0,
    private var updateAt: Long = Date().time,
) {
    fun takeIfUnlocked(): Long? {
        if (hasNext()) {
            return attempts.also {
                register()
            }
        }

        return attempts.takeIf { now.isAfter(lockPeriod) }?.let {
            reset()
            register()
        }
    }

    private fun Date.isAfter(date: Date) = this.after(date)
    private val now: Date get() = Date()
    private val lockPeriod: Date get() = Calendar.getInstance().apply {
        time = Date(updateAt)
        add(Calendar.HOUR, MIN_PERIOD)
    }.time

    fun reset() {
        attempts = 0
    }

    fun updateAt(): String = dateFormat.format(Date(updateAt))

    fun hasNext(): Boolean = attempts <= MAX_ATTEMPT_WITHIN_PERIOD

    fun register() = (attempts++).also { updateAt = Date().time }

    override fun toString(): String = attempts.toString()

    companion object {
        const val MIN_PERIOD = 6
        private const val MAX_ATTEMPT_WITHIN_PERIOD = 2
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    }
}

class Credential(
    val email: String,
    val password: String,
) {
    val isEmpty: Boolean get() = email.isBlank() || password.isBlank()
    val isNotEmpty: Boolean get() = isEmpty.not()
}
