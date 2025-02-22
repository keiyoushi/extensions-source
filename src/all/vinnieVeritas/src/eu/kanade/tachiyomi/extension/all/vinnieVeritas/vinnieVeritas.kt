package eu.kanade.tachiyomi.extension.all.vinnieVeritas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class vinnieVeritas(override val lang: String = "en") : ParsedHttpSource() {

    override val name = "Vinnie Veritas - CCC"
    override val supportsLatest = false

    override val baseUrl = "https://vinnieveritas.com/"

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("ccc-$lang/")
        manga.title = if (lang == "en") { "CCC: The city of opportunities" } else { "CCC: La ciudad de las oportunidades" }
        manga.artist = "Vinnie Veritas"
        manga.author = "Vinnie Veritas"
        manga.status = SManga.ONGOING
        manga.description = if (lang == "en") {
            """Almost 7 years ago I started working on a project where I would put everything I had drawn, characters, concepts and nonsense that came up while I was growing up .. it was so much I chose a city to put it all in. Like all people who draw, I abandoned many comics and concepts that I thought, sucked.. but I promised myself when I was around 19 years old that I would not abandon this one; because my ability to draw was less than today’s, the first two volumes of CCC: The city of the opportunities are… umm, ugly. When I was around 21-22 years old I began to animate in flash, so I decided to animate the world embodied in the comic and continue with the comic this time drawn in flash, therefore Volume 3 has color.

In this period I had a lot of animation and illustration work would not let me continue the story of CCC: The city of opportunities, the hiatus lasted about 5 years, while I still did animations I did not carried on with the story in the comic .. Now new comics every Thursday.

CCC is the name of the second largest city there is, is not an acronym or an abbreviation for something, CCC: The city of opportunies tells the story of Lucio Vasalle and his misadventures as a newcomer to CCC, comics, drawings and animations are related, they all have bits of story about the characters and their past, you are welcome to explore all this and draw your own conclusions, if you look closely you may find something that someone hasn’t noticed yet (:			"""
        } else {
            """Hace casi 7 años empecé un proyecto donde iba a meter todas las cosas que había dibujado: personajes, conceptos y tonterias que se me habían ocurrido mientras crecía.. era tanto que pensé que en lo único donde cabría era en una ciudad. Como todos los que dibujamos, abandoné muchos comics y conceptos que no me convencieron al final.. pero me prometí a mi mismo a los 19 años que este comic no lo iba a abandonar; ya que mi habilidad para dibujar era menor a la de hoy en día los primeros dos volumenes de CCC: La ciudad de las oportunidades se ven tan… umm, culeros. Cuando tenía alrededor de 21-22 años comencé a animar en flash y me gustó, decidí animar el mundo que plasmaba en el comic y continuar con la historia dibujada en flash, por eso en el volumen 3 tiene color.

En este lapso de tiempo tuve mucho trabajo de animación e ilustración que no me dejó continuar con la historieta de CCC: La ciudad de las oportunidades, el hiatus duró mas o menos 5 años, al mismo tiempo animaba pero ya no continuaba con la historia del comic.. Ahora ya la actualizo cada jueves.

CCC es el nombre de la segunda ciudad mas grande que hay, no son siglas ni la abreviación de algo, CCC: La ciudad de las oportunidades narra la historia de Lucio Vasalle y sus desventuras en CCC como recién llegado, el comic, los dibujos sueltos y las animaciones están relacionados, todos cuentan pequeños pedazos de  los personajes y de sus pasados, eres bienvenido a explorar todo esto y sacar tus propias conclusiones, si te fijas bien puede que encuentres algo que alguien no haya notado (:			"""
        }
        manga.thumbnail_url = thumbnailUrl
        manga.genre = "webcomic"

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1)
        .map { it.mangas.first().apply { initialized = true } }

    // Chapters are listed old to new.
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "option.webcomic${if (lang == "en"){1}else {2}}-link"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.attr("data-webcomic-url")
        chapter.name = element.text()
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".webcomic-image img").mapIndexed { i, image -> Page(i, "", image.attr("src")) }
    }

    companion object {
        private const val thumbnailUrl = "https://i1.wp.com/vinnieveritas.com/wp-content/uploads/2016/02/CCC000.jpg"
    }

    // unused
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun popularMangaSelector(): String = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
}
