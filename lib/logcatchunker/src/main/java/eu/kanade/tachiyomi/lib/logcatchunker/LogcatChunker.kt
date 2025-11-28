package eu.kanade.tachiyomi.lib.logcatchunker

/**
 * Utility object for chunking log messages to avoid Android's logcat truncation.
 *
 * Android's logcat has a message size limit (typically around 4000 characters). This utility splits
 * long messages into multiple chunks while preserving line breaks.
 */
object LogcatChunker {

    /**
     * Default maximum chunk size that fits within Android logcat limits. Set slightly below 4096 to
     * account for potential overhead.
     */
    const val DEFAULT_CHUNK_SIZE = 4011

    /**
     * Chunks a message and logs each chunk using the provided logger.
     *
     * @param message The message to chunk and log.
     * @param tag The log tag to use.
     * @param chunkSize Maximum size of each chunk (default: [DEFAULT_CHUNK_SIZE]).
     * @param logWriter The function to call for each chunk.
     */
    fun logChunked(
        message: String,
        tag: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        logWriter: LogWriter,
    ) {
        val chunks = chunk(message, chunkSize)
        for (chunk in chunks) {
            logWriter.log(tag, chunk)
        }
    }

    /**
     * Splits a message into chunks that respect line boundaries where possible.
     *
     * The algorithm:
     * 1. Splits the message by newlines
     * 2. If a line fits in the current chunk, add it
     * 3. If a single line exceeds [chunkSize], it's split into multiple chunks of exactly
     *    [chunkSize]
     *
     * @param message The message to chunk.
     * @param chunkSize Maximum size of each chunk.
     * @return List of chunks, where each chunk is a string that doesn't exceed [chunkSize].
     */
    fun chunk(message: String, chunkSize: Int = DEFAULT_CHUNK_SIZE): List<String> {
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (originalLine in message.splitToSequence(Regex("\r?\n"))) {
            var line = originalLine

            while (line.isNotEmpty()) {
                if (line.length <= chunkSize) {
                    // Line fits - check if it fits in current chunk
                    val newLength = if (currentChunk.isEmpty()) {
                        line.length
                    } else {
                        currentChunk.length + 1 + line.length // +1 for newline
                    }

                    if (newLength <= chunkSize) {
                        // Add to current chunk
                        if (currentChunk.isNotEmpty()) {
                            currentChunk.append('\n')
                        }
                        currentChunk.append(line)
                    } else {
                        // Start new chunk
                        if (currentChunk.isNotEmpty()) {
                            chunks.add(currentChunk.toString())
                            currentChunk.clear()
                        }
                        currentChunk.append(line)
                    }
                    line = ""
                } else {
                    // Line is too long - need to split it
                    // First, flush current chunk if not empty
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString())
                        currentChunk.clear()
                    }

                    // Add a chunk of exactly chunkSize
                    chunks.add(line.substring(0, chunkSize))
                    line = line.substring(chunkSize)
                }
            }
        }

        // Don't forget the last chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        // Handle empty message case
        if (chunks.isEmpty()) {
            chunks.add("")
        }

        return chunks
    }
}
