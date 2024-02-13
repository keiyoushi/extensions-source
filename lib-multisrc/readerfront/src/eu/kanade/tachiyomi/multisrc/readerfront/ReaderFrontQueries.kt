package eu.kanade.tachiyomi.multisrc.readerfront

private fun String.encodeUri() =
    android.net.Uri.encode(trimMargin())!!

internal const val STUB_QUERY = "stub:"

fun works(lang: Int, sort: String, order: String, page: Int, limit: Int) = """{
   |works(
   |    orderBy: "$order"
   |    sortBy: "$sort"
   |    first: $limit
   |    offset: ${(page - 1) * limit}
   |    languages: [$lang]
   |    showHidden: false
   |) {
   |    name
   |    stub
   |    thumbnail_path
   |}
}""".encodeUri()

fun work(lang: Int, stub: String) = """{
   |work(
   |  stub: "$stub"
   |  language: $lang
   |  showHidden: false
   |) {
   |  name
   |  stub
   |  thumbnail_path
   |  status_name
   |  adult
   |  type
   |  licensed
   |  description
   |  demographic_name
   |  genres { name }
   |  people_works {
   |    role: rol
   |    people { name }
   |  }
   |}
}""".encodeUri()

fun chaptersByWork(lang: Int, stub: String) = """{
   |chaptersByWork(
   |  workStub: "$stub"
   |  languages: [$lang]
   |  showHidden: false
   |) {
   |  id
   |  chapter
   |  subchapter
   |  volume
   |  name
   |  releaseDate
   |}
}""".encodeUri()

fun chapterById(id: Int) = """{
   |chapterById(
   |  id: $id
   |  showHidden: false
   |) {
   |  uniqid
   |  work { uniqid }
   |  pages {
   |    filename
   |    width
   |  }
   |}
}""".encodeUri()
