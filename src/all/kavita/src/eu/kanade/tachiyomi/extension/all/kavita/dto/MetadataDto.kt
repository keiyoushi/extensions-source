package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable
/**
* This file contains all class for filtering
*  */
@Serializable
data class MetadataGenres(
    val id: Int,
    val title: String,
)

@Serializable
data class MetadataPeople(
    val id: Int,
    val name: String,
    val role: Int,
)

@Serializable
data class MetadataPubStatus(
    val value: Int,
    val title: String,
)

@Serializable
data class MetadataTag(
    val id: Int,
    val title: String,
)

@Serializable
data class MetadataAgeRatings(
    val value: Int,
    val title: String,
)

@Serializable
data class MetadataLanguages(
    val isoCode: String,
    val title: String,
)

@Serializable
data class MetadataLibrary(
    val id: Int,
    val name: String,
    val type: Int,
)

@Serializable
data class MetadataCollections(
    val id: Int,
    val title: String,
)

data class MetadataPayload(
    val forceUseMetadataPayload: Boolean = true,
    var sorting: Int = 1,
    var sorting_asc: Boolean = true,
    var readStatus: ArrayList<String> = arrayListOf<String>(),
    val readStatusList: List<String> = listOf("notRead", "inProgress", "read"),
    // _i = included, _e = excluded
    var genres_i: ArrayList<Int> = arrayListOf<Int>(),
    var genres_e: ArrayList<Int> = arrayListOf<Int>(),
    var tags_i: ArrayList<Int> = arrayListOf<Int>(),
    var tags_e: ArrayList<Int> = arrayListOf<Int>(),
    var ageRating_i: ArrayList<Int> = arrayListOf<Int>(),
    var ageRating_e: ArrayList<Int> = arrayListOf<Int>(),

    var formats: ArrayList<Int> = arrayListOf<Int>(),
    var collections_i: ArrayList<Int> = arrayListOf<Int>(),
    var collections_e: ArrayList<Int> = arrayListOf<Int>(),
    var userRating: Int = 0,
    var people: ArrayList<Int> = arrayListOf<Int>(),
    // _i = included, _e = excluded
    var language_i: ArrayList<String> = arrayListOf<String>(),
    var language_e: ArrayList<String> = arrayListOf<String>(),

    var libraries_i: ArrayList<Int> = arrayListOf<Int>(),
    var libraries_e: ArrayList<Int> = arrayListOf<Int>(),
    var pubStatus: ArrayList<Int> = arrayListOf<Int>(),
    var seriesNameQuery: String = "",
    var releaseYearRangeMin: Int = 0,
    var releaseYearRangeMax: Int = 0,

    var peopleWriters: ArrayList<Int> = arrayListOf<Int>(),
    var peoplePenciller: ArrayList<Int> = arrayListOf<Int>(),
    var peopleInker: ArrayList<Int> = arrayListOf<Int>(),
    var peoplePeoplecolorist: ArrayList<Int> = arrayListOf<Int>(),
    var peopleLetterer: ArrayList<Int> = arrayListOf<Int>(),
    var peopleCoverArtist: ArrayList<Int> = arrayListOf<Int>(),
    var peopleEditor: ArrayList<Int> = arrayListOf<Int>(),
    var peoplePublisher: ArrayList<Int> = arrayListOf<Int>(),
    var peopleCharacter: ArrayList<Int> = arrayListOf<Int>(),
    var peopleTranslator: ArrayList<Int> = arrayListOf<Int>(),
)

@Serializable
data class SmartFilter(
    val id: Int,
    val name: String,
    val filter: String,
)
