package eu.kanade.tachiyomi.extension.en.doujinio

import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

val json: Json by injectLazy()

val tags = listOf(
    Tag(id = 22, name = "Aggressive Sex"),
    Tag(id = 23, name = "Anal"),
    Tag(id = 104, name = "BBM"),
    Tag(id = 105, name = "BSS"),
    Tag(id = 62, name = "Big Breasts"),
    Tag(id = 26, name = "Blowjob"),
    Tag(id = 27, name = "Bondage"),
    Tag(id = 29, name = "Cheating"),
    Tag(id = 32, name = "Creampie"),
    Tag(id = 33, name = "Crossdressing"),
    Tag(id = 34, name = "Cunnilingus"),
    Tag(id = 35, name = "Dark Skin"),
    Tag(id = 36, name = "Defloration"),
    Tag(id = 38, name = "Demon Girl"),
    Tag(id = 51, name = "Dickgirl"),
    Tag(id = 112, name = "Doll Joints"),
    Tag(id = 41, name = "Elf"),
    Tag(id = 106, name = "Exhibitionism"),
    Tag(id = 107, name = "Family"),
    Tag(id = 44, name = "Femdom"),
    Tag(id = 46, name = "Footjob"),
    Tag(id = 49, name = "Full Color"),
    Tag(id = 50, name = "Furry"),
    Tag(id = 53, name = "Gender Bender"),
    Tag(id = 54, name = "Group"),
    Tag(id = 55, name = "Gyaru"),
    Tag(id = 56, name = "Gym Uniform"),
    Tag(id = 114, name = "Kemonomimi"),
    Tag(id = 61, name = "Lactation"),
    Tag(id = 9, name = "Maid Uniform"),
    Tag(id = 65, name = "Mind Control"),
    Tag(id = 108, name = "Mindbreak"),
    Tag(id = 109, name = "Monster Girl"),
    Tag(id = 69, name = "Muscle"),
    Tag(id = 71, name = "Netorare"),
    Tag(id = 73, name = "Ninja Outfit"),
    Tag(id = 74, name = "Non-H"),
    Tag(id = 75, name = "Nun Outfit"),
    Tag(id = 76, name = "Nurse Outfit"),
    Tag(id = 78, name = "Old Man"),
    Tag(id = 82, name = "Pay To Play"),
    Tag(id = 80, name = "Petite"),
    Tag(id = 81, name = "Pregnant"),
    Tag(id = 83, name = "Rimjob"),
    Tag(id = 84, name = "School Uniform"),
    Tag(id = 110, name = "Small Breasts"),
    Tag(id = 63, name = "Solo Action"),
    Tag(id = 90, name = "Swimsuit"),
    Tag(id = 91, name = "Tanlines"),
    Tag(id = 92, name = "Tentacles"),
    Tag(id = 93, name = "Titjob"),
    Tag(id = 94, name = "Toys"),
    Tag(id = 95, name = "Urination"),
    Tag(id = 99, name = "Yaoi"),
)

fun parseDate(dateStr: String): Long {
    return try {
        dateFormat.parse(dateStr)!!.time
    } catch (_: ParseException) {
        0L
    }
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
}

fun getIdFromUrl(url: String) = url.split("/").last()

fun getIdsFromUrl(url: String) = "${url.split("/")[1]}/${url.split("/").last()}"
