package eu.kanade.tachiyomi.extension.en.utoon

import kotlinx.serialization.Serializable

@Serializable
class Dto(
    val url: String,
    val label: String,
    val ago: String? = null,
    private val locked: Boolean? = null,
) {
    val isLocked get() = locked == true
}
