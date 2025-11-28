package eu.kanade.tachiyomi.lib.logcatchunker

/**
 * Interface for logging messages. Can be used to redirect log output to different targets (logcat,
 * file, etc.).
 */
fun interface LogWriter {
    fun log(tag: String, message: String)
}
