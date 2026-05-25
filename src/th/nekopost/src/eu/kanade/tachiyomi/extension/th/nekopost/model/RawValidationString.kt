package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawValidationString(
    @SerialName("String")
    val string: String,
    @SerialName("Valid")
    val valid: Boolean,
)
