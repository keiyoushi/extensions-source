package eu.kanade.tachiyomi.extension.en.mayotune

fun Float.asString(): String = if (this % 1 == 0f) {
    this.toInt().toString()
} else {
    this.toString()
}
