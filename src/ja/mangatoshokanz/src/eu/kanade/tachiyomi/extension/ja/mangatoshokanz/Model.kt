package eu.kanade.tachiyomi.extension.ja.mangatoshokanz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class Encrypted(
    val bi: String,
    val ek: String,
    val data: String,
)

@Serializable
internal class Decrypted(
    @SerialName("Images")
    val images: List<Image>,
    @SerialName("Location")
    val location: Location,
) {
    @Serializable
    internal class Image(
        val file: String,
    )

    @Serializable
    internal class Location(
        val base: String,
        val st: String,
    )
}
