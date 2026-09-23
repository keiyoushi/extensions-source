package eu.kanade.tachiyomi.multisrc.initmanga

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class GenreData(
    val name: String,
    val url: String,
)

class Genre(
    name: String,
    val url: String,
) : Filter.CheckBox(name)

class GenreListFilter(
    name: String,
    genres: List<Genre>,
) : Filter.Group<Genre>(name, genres)

open class TypeFilter(name: String = "Tür", options: Array<String> = typeFilterOptions) : Filter.Select<String>(name, options)

open class StatusFilter(name: String = "Durum", options: Array<String> = statusFilterOptions) : Filter.Select<String>(name, options)

open class SortFilter(name: String = "Sırala", options: Array<String> = sortFilterOptions) : Filter.Select<String>(name, options)

val typeFilterOptions = arrayOf("Tüm Türler", "Çizgi Roman", "Roman")
val typeValues = arrayOf("", "comic", "novel")

val statusFilterOptions = arrayOf(
    "Tüm Durumlar",
    "Devam ediyor",
    "Sezon sonu",
    "Tamamlandı",
    "Kaynak ara verdi",
    "Güncel",
    "Bırakıldı",
)
val statusValues = arrayOf(
    "",
    "ongoing",
    "season_end",
    "completed",
    "source_hiatus",
    "caught_up",
    "dropped",
)

val sortFilterOptions = arrayOf(
    "Son Güncellenen",
    "En yeni",
    "En Çok Görüntülenme",
    "En Yüksek Puan",
    "Popülerlik (Güç / Kutsama)",
    "En Çok Takipçi",
)
val sortValues = arrayOf(
    "updated",
    "new",
    "views",
    "rating",
    "power",
    "follow",
)
