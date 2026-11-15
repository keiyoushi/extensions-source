package eu.kanade.tachiyomi.extension.all.dlsite

// The Mersenne Twister DLsite Play seeds its page tile order with
class Mt19937(seed: Int) {
    private val state = IntArray(N)
    private var index = N

    init {
        state[0] = seed
        for (i in 1 until N) {
            val previous = state[i - 1] xor (state[i - 1] ushr 30)
            state[i] = MULTIPLIER * previous + i
        }
    }

    private fun nextInt(): Int {
        if (index >= N) twist()
        var y = state[index++]
        y = y xor (y ushr 11)
        y = y xor ((y shl 7) and TEMPERING_B)
        y = y xor ((y shl 15) and TEMPERING_C)
        return y xor (y ushr 18)
    }

    private fun twist() {
        for (i in 0 until N) {
            val y = (state[i] and UPPER_MASK) or (state[(i + 1) % N] and LOWER_MASK)
            state[i] = state[(i + M) % N] xor (y ushr 1) xor (if (y and 1 != 0) MATRIX_A else 0)
        }
        index = 0
    }

    // A double in [0, 1) from the next 32 bits read as unsigned
    fun nextDouble(): Double = (nextInt().toLong() and 0xFFFFFFFFL) / 4294967296.0

    companion object {
        private const val N = 624
        private const val M = 397
        private const val MULTIPLIER = 1812433253
        private const val MATRIX_A = 0x9908B0DF.toInt()
        private const val UPPER_MASK = 0x80000000.toInt()
        private const val LOWER_MASK = 0x7FFFFFFF
        private const val TEMPERING_B = 0x9D2C5680.toInt()
        private const val TEMPERING_C = 0xEFC60000.toInt()
    }
}
