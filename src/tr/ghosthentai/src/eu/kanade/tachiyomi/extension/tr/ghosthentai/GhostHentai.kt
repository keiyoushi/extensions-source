package eu.kanade.tachiyomi.extension.tr.ghosthentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class GhostHentai : Madara(
    "Ghost Hentai",
    "https://ghosthentai.com",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaSubString = "seri"

    override val fetchGenres = false
    override var genresList = listOf(
        Genre("3D", "3d"),
        Genre("Aile", "aile"),
        Genre("Aincard", "aincard"),
        Genre("Aksiyon", "aksiyon"),
        Genre("Askeri", "askeri"),
        Genre("Bilim Kurgu", "bilim-kurgu"),
        Genre("Büyü", "buyu"),
        Genre("Büyükler İçin", "yasi-buyukler-icin"),
        Genre("Chemosh Special", "chemosh-special"),
        Genre("Doğaüstü", "dogaustu"),
        Genre("Dövüş", "dovus"),
        Genre("Dövüş Sanatları", "dovus-sanatlari"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Erkek Y", "erkek-y"),
        Genre("Fantastik", "fantastik"),
        Genre("Fantezi", "fantezi"),
        Genre("Final", "final"),
        Genre("Ghost Hentai", "gri-melek"),
        Genre("Gizem", "gizem"),
        Genre("Harem", "harem"),
        Genre("İntikam", "intikam"),
        Genre("İsekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kadın X", "kadin-x"),
        Genre("Komedi", "komedi"),
        Genre("Korku", "korku"),
        Genre("Macera", "macera"),
        Genre("Manga", "manga"),
        Genre("Manhwa", "manhwa"),
        Genre("Normal", "normal"),
        Genre("NTR", "ntr"),
        Genre("Okul", "okul"),
        Genre("Okul Hayatı", "okul-hayati"),
        Genre("Psikoloji", "psikoloji"),
        Genre("Reenkarnasyon", "reenkarnasyon"),
        Genre("Romantik", "romantik"),
        Genre("Sansürsüz", "sansursuz"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Şiddet", "siddet"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Spor", "spor"),
        Genre("Suç", "suc"),
        Genre("Tarih", "tarih"),
        Genre("Tek eşli", "tek-esli"),
        Genre("TOMMY SHELBY", "tommy-shelby"),
        Genre("Trajedi", "trajedi"),
        Genre("Türkçe", "turkce"),
        Genre("Webtoon", "webtoon"),
        Genre("3D", "3d"),
        Genre("Aile", "aile"),
        Genre("Aincard", "aincard"),
        Genre("Aksiyon", "aksiyon"),
        Genre("Askeri", "askeri"),
        Genre("Bilim Kurgu", "bilim-kurgu"),
        Genre("Büyü", "buyu"),
        Genre("Büyükler İçin", "yasi-buyukler-icin"),
        Genre("Chemosh Special", "chemosh-special"),
        Genre("Doğaüstü", "dogaustu"),
        Genre("Dövüş", "dovus"),
        Genre("Dövüş Sanatları", "dovus-sanatlari"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Erkek Y", "erkek-y"),
        Genre("Fantastik", "fantastik"),
        Genre("Fantezi", "fantezi"),
        Genre("Final", "final"),
        Genre("Ghost Hentai", "gri-melek"),
        Genre("Gizem", "gizem"),
        Genre("Harem", "harem"),
        Genre("İntikam", "intikam"),
        Genre("İsekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kadın X", "kadin-x"),
        Genre("Komedi", "komedi"),
        Genre("Korku", "korku"),
        Genre("Macera", "macera"),
        Genre("Manga", "manga"),
        Genre("Manhwa", "manhwa"),
        Genre("Normal", "normal"),
        Genre("NTR", "ntr"),
        Genre("Okul", "okul"),
        Genre("Okul Hayatı", "okul-hayati"),
        Genre("Psikoloji", "psikoloji"),
        Genre("Reenkarnasyon", "reenkarnasyon"),
        Genre("Romantik", "romantik"),
        Genre("Sansürsüz", "sansursuz"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Şiddet", "siddet"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Spor", "spor"),
        Genre("Suç", "suc"),
        Genre("Tarih", "tarih"),
        Genre("Tek eşli", "tek-esli"),
        Genre("TOMMY SHELBY", "tommy-shelby"),
        Genre("Trajedi", "trajedi"),
        Genre("Türkçe", "turkce"),
        Genre("Webtoon", "webtoon"),
    )

    override fun pageListParse(document: Document): List<Page> {
        val pageList = super.pageListParse(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Inicie sesión en WebView para ver este capítulo")
        }
        return pageList
    }

    override val pageListParseSelector = "div.page-break img.wp-manga-chapter-img"
}
