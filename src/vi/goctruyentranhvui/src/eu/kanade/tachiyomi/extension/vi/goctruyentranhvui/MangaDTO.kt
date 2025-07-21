package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import kotlinx.serialization.Serializable

@Serializable
class ChapterDTO(
    val result: ResultChapter,
)

@Serializable
class Chapters(
    val numberChapter: String,
    val stringUpdateTime: String,
)

@Serializable
class ResultChapter(
    val chapters: List<Chapters>,
)

@Serializable
class PopularDTO(
    val result: Result,
)

@Serializable
class Result(
    val p: Int? = null,
    val next: Boolean? = null,
    val data: ArrayList<Data>,
)

@Serializable
class Data(
    val name: String,
    val photo: String,
    val nameEn: String,
)

@Serializable
class ChapterWrapper(
    val headers: Map<String, String> = emptyMap(),
    val body: ChapterBody,
)

@Serializable
class ChapterBody(
    val result: ResultContent,
)

@Serializable
class ResultContent(
    val data: List<String>,
)

@Serializable
class SearchDTO(
    val result: List<ResultSearch>,
)

@Serializable
class ResultSearch(
    val name: String,
    val photo: String,
    val nameEn: String,
)
