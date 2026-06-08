package eu.kanade.tachiyomi.multisrc.initmanga

import eu.kanade.tachiyomi.source.model.Filter

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

open class TypeFilter(name: String, options: Array<String> = typeFilterOptions) : Filter.Select<String>(name, options)

open class StatusFilter(name: String, options: Array<String> = statusFilterOptions) : Filter.Select<String>(name, options)

open class AgeRatingFilter(name: String, options: Array<String> = ageRatingFilterOptions) : Filter.Select<String>(name, options)

open class RatingMinFilter(name: String, options: Array<String> = ratingMinFilterOptions) : Filter.Select<String>(name, options)

open class RatingMaxFilter(name: String, options: Array<String> = ratingMaxFilterOptions) : Filter.Select<String>(name, options)

open class SortFilter(name: String, options: Array<String> = sortFilterOptions) : Filter.Select<String>(name, options)

val typeFilterOptions = arrayOf("Tüm", "Çizgi Roman", "Roman", "Tek Bölümlük")
val typeValues = arrayOf("", "comic", "novel", "oneshot")

val statusFilterOptions = arrayOf("Tüm Durumlar", "Devam Ediyor", "Sezon F.", "Final", "Kaynak Ara Verdi", "Güncel", "Bırakıldı")
val statusValues = arrayOf("", "ongoing", "season_end", "completed", "source_hiatus", "caught_up", "dropped")

val ageRatingFilterOptions = arrayOf("Her Yaş", "13+", "16+", "18+")
val ageRatingValues = arrayOf("", "13+", "16+", "18+")

val ratingMinFilterOptions = arrayOf("Min", "1★", "2★", "3★", "4★", "5★")
val ratingMinValues = arrayOf("0", "1", "2", "3", "4", "5")

val ratingMaxFilterOptions = arrayOf("Maks", "1★", "2★", "3★", "4★", "5★")
val ratingMaxValues = arrayOf("6", "1", "2", "3", "4", "5")

val sortFilterOptions = arrayOf("Son Güncellenenler", "En Yeni", "En Eski", "En Çok Görüntüleme", "Günlük Görüntülemeler", "Haftalık Görüntüleme", "Aylık Görüntülemeler", "En Yüksek Puan", "En Çok Efsun", "En Çok Takipçi")
val sortValues = arrayOf("updated", "new", "old", "views", "views_day", "views_week", "views_month", "rating", "power", "follow")
