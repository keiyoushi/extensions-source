package eu.kanade.tachiyomi.extension.fr.lugnicascans

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class HomePageManga(
    @SerialName("manga_title")
    val mangaTitle: String,
    @SerialName("manga_slug")
    val mangaSlug: String,
    @SerialName("manga_image")
    val mangaImage: String,
)

@Serializable
class MangaDetailsChapter(
    val chapter: Float,
    val date: String,
)

@Serializable
class MangaDetails(
    val title: String,
    val image: String,
    val status: String,
    val description: String,
    val genre: List<String>,
    val theme: List<String>,
    val author: String,
    val artist: String,
)

@Serializable
class MangaDetailsResponse(
    val manga: MangaDetails,
    val chapters: Map<String, List<MangaDetailsChapter>>,
)

@Serializable
class PageList(
    val manga: PageListManga,
    var chapter: PageListChapter,
)

@Serializable
class PageListManga(
    val id: Int,
)

object NumberAsJsonDeserializer : KSerializer<Number> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleNumber", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Number) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): Number {
        val input = decoder as? JsonDecoder ?: error("Only supports JSON")
        val element = input.decodeJsonElement().jsonPrimitive
        val raw = element.content

        return if (raw.contains('.')) {
            raw.toDouble()
        } else {
            raw.toInt()
        }
    }
}

@Serializable
class PageListChapter(
    val files: List<String>,
    @Serializable(with = NumberAsJsonDeserializer::class)
    val chapter: Number, // Sometimes Int, sometimes Float
)

val GENRES = mapOf(
    0 to "Normal",
    1 to "Ecchi",
    2 to "Gore",
    3 to "Violence sexuelle",
    4 to "Obscene",
    10 to "Action",
    11 to "Aventure",
    12 to "Comédie",
    13 to "Crime",
    14 to "Drame",
    15 to "Fantaisie",
    16 to "Historique",
    17 to "Horreur",
    18 to "Isekai",
    19 to "Magical Girls",
    20 to "Mecha",
    21 to "Medecine",
    22 to "Mystère",
    23 to "Philosophique",
    24 to "Psychologique",
    25 to "Romance",
    26 to "Sci-Fi",
    27 to "Shoujo Ai",
    28 to "Shounen Ai",
    29 to "Tranche de vie",
    30 to "Sports",
    31 to "Super héros",
    32 to "Thriller",
    33 to "Tragedie",
    34 to "Wuxia",
    35 to "Yaoi",
    36 to "Yuri",
    40 to "Aliens",
    41 to "Animaux",
    42 to "Cuisine",
    43 to "Crossdressing",
    44 to "Délinquants",
    45 to "Démons",
    46 to "Genderswap",
    47 to "Fantômes",
    48 to "Gyaru",
    49 to "Harem",
    50 to "Inceste",
    51 to "Loli",
    52 to "Mafia",
    53 to "Magie",
    54 to "Arts martiaux",
    55 to "Militaire",
    56 to "Monster Girls",
    57 to "Monstres",
    58 to "Musique",
    59 to "Ninja",
    60 to "Employés de bureau",
    61 to "Police",
    62 to "Post-apocalyptique",
    63 to "Réincarnation",
    64 to "Harem inversé",
    65 to "Samurai",
    66 to "Vie scolaire",
    67 to "Shota",
    68 to "Surnaturel",
    69 to "Survie",
    70 to "Voyage dans le temps",
    71 to "Jeux traditionnels",
    72 to "Vampires",
    73 to "Jeux vidéo",
    74 to "Réalité virtuelle",
    75 to "Zombies",
)
