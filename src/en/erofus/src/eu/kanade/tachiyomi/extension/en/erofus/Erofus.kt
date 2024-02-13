package eu.kanade.tachiyomi.extension.en.erofus

import eu.kanade.tachiyomi.multisrc.eromuse.EroMuse
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import rx.Observable

@ExperimentalStdlibApi
class Erofus : EroMuse("Erofus", "https://www.erofus.com") {

    override val albumSelector = "a.a-click"
    override val topLevelPathSegment = "comics"

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = fetchManga("$baseUrl/comics/various-authors?sort=viewed&page=1", page, "viewed")
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchManga("$baseUrl/comics/various-authors?sort=recent&page=1", page, "recent")
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (page == 1) {
            pageStack.clear()

            val filterList = if (filters.isEmpty()) getFilterList() else filters
            currentSortingMode = filterList.filterIsInstance<SortFilter>().first().toQueryValue()

            if (query.isNotBlank()) {
                // TODO possibly add genre search if a decent list of them can be built
                pageStack.addLast(StackItem("$baseUrl/?search=$query&sort=$currentSortingMode&page=1", SEARCH_RESULTS_OR_BASE))
            } else {
                val albumFilter = filterList.filterIsInstance<AlbumFilter>().first().selection()
                val url = (baseUrl + albumFilter.pathSegments).toHttpUrl().newBuilder()
                    .addQueryParameter("sort", currentSortingMode)
                    .addQueryParameter("page", "1")

                pageStack.addLast(StackItem(url.toString(), albumFilter.pageType))
            }
        }

        return client.newCall(stackRequest())
            .asObservableSuccess()
            .map { response -> parseManga(response.asJsoup()) }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
            with(response.asJsoup()) {
                setUrlWithoutDomain(response.request.url.toString())
                thumbnail_url = select("$albumSelector img").firstOrNull()?.imgAttr()
                author = when (getAlbumType(url)) {
                    AUTHOR -> {
                        // eg. https://www.erofus.com/comics/witchking00-comics/adventure-time
                        // eg. https://www.erofus.com/comics/mcc-comics/bearing-gifts/bearing-gifts-issue-1
                        select("div.navigation-breadcrumb li:nth-child(3)").text()
                    }
                    VARIOUS_AUTHORS -> {
                        // eg. https://www.erofus.com/comics/various-authors/artdude41/bat-vore
                        select("div.navigation-breadcrumb li:nth-child(5)").text()
                    }
                    else -> null
                }

                genre = select("div.album-tag-container a").joinToString { it.text() }
            }
        }
    }

    override val linkedChapterSelector = "a.a-click:has(img)[href^=/comics/]"
    override val pageThumbnailSelector = "a.a-click:has(img)[href*=/pic/] img"

    override val pageThumbnailPathSegment = "/thumb/"
    override val pageFullSizePathSegment = "/medium/"

    override fun getAlbumList() = arrayOf(
        Triple("All Authors", "", SEARCH_RESULTS_OR_BASE),
        Triple("Various Authors", "/comics/various-authors", VARIOUS_AUTHORS),
        Triple("Hentai and Manga English", "/comics/hentai-and-manga-english", VARIOUS_AUTHORS),
        Triple("TabooLicious.xxx Comics", "/comics/taboolicious_xxx-comics", AUTHOR),
        Triple("IllustratedInterracial.com Comics", "/comics/illustratedinterracial_com-comics", AUTHOR),
        Triple("ZZZ Comics", "/comics/zzz-comics", AUTHOR),
        Triple("JohnPersons.com Comics", "/comics/johnpersons_com-comics", AUTHOR),
        Triple("For members only", "/", AUTHOR),
        Triple("PalComix Comics", "/comics/palcomix-comics", AUTHOR),
        Triple("Melkormancin.com Comics", "/comics/melkormancin_com-comics", AUTHOR),
        Triple("TG Comics", "/comics/tg-comics", AUTHOR),
        Triple("ShadBase Comics", "/comics/shadbase-comics", AUTHOR),
        Triple("Filthy Figments Comics", "/comics/filthy-figments-comics", AUTHOR),
        Triple("Witchking00 Comics", "/comics/witchking00-comics", AUTHOR),
        Triple("Tease Comix", "/comics/tease-comix", AUTHOR),
        Triple("PrismGirls Comics", "/comics/prismgirls-comics", AUTHOR),
        Triple("Croc Comics", "/comics/croc-comics", AUTHOR),
        Triple("CRAZYXXX3DWORLD Comics", "/comics/crazyxxx3dworld-comics", AUTHOR),
        Triple("Moiarte Comics", "/comics/moiarte-comics", AUTHOR),
        Triple("Nicole Heat Comics", "/comics/nicole-heat-comics", AUTHOR),
        Triple("Expansion Comics", "/comics/expansion-comics", AUTHOR),
        Triple("DizzyDills Comics", "/comics/dizzydills-comics", AUTHOR),
        Triple("Hustler Cartoons", "/comics/hustler-cartoons", AUTHOR),
        Triple("ArtOfJaguar Comics", "/comics/artofjaguar-comics", AUTHOR),
        Triple("Grow Comics", "/comics/grow-comics", AUTHOR),
        Triple("Bimbo Story Club Comics", "/comics/bimbo-story-club-comics", AUTHOR),
        Triple("HentaiTNA.com Comics", "/comics/hentaitna_com-comics", AUTHOR),
        Triple("ZZomp Comics", "/comics/zzomp-comics", AUTHOR),
        Triple("Seiren.com.br Comics", "/comics/seiren_com_br-comics", AUTHOR),
        Triple("DukesHardcoreHoneys.com Comics", "/comics/dukeshardcorehoneys_com-comics", AUTHOR),
        Triple("Frozen Parody Comics", "/comics/frozen-parody-comics", AUTHOR),
        Triple("Giantess Club Comics", "/comics/giantess-club-comics", AUTHOR),
        Triple("Ultimate3DPorn Comics", "/comics/ultimate3dporn-comics", AUTHOR),
        Triple("Sean Harrington Comics", "/comics/sean-harrington-comics", AUTHOR),
        Triple("Central Comics", "/comics/central-comics", AUTHOR),
        Triple("Mana World Comics", "/comics/mana-world-comics", AUTHOR),
        Triple("The Foxxx Comics", "/comics/the-foxxx-comics", AUTHOR),
        Triple("Bloody Sugar Comics", "/comics/bloody-sugar-comics", AUTHOR),
        Triple("Deuce Comics", "/comics/deuce-comics", AUTHOR),
        Triple("Adult Empire Comics", "/comics/adult-empire-comics", AUTHOR),
        Triple("SuperHeroineComixxx", "/comics/superheroinecomixxx", AUTHOR),
        Triple("Sluttish Comics", "/comics/sluttish-comics", AUTHOR),
        Triple("Damn3D Comics", "/comics/damn3d-comics", AUTHOR),
        Triple("Fake Celebrities Sex Pictures", "/comics/fake-celebrities-sex-pictures", AUTHOR),
        Triple("Secret Chest Comics", "/comics/secret-chest-comics", AUTHOR),
        Triple("Project Bellerophon Comics", "/comics/project-bellerophon-comics", AUTHOR),
        Triple("Smudge Comics", "/comics/smudge-comics", AUTHOR),
        Triple("Superheroine Central Comics", "/comics/superheroine-central-comics", AUTHOR),
        Triple("Jay Marvel Comics", "/comics/jay-marvel-comics", AUTHOR),
        Triple("Fred Perry Comics", "/comics/fred-perry-comics", AUTHOR),
        Triple("Seduced Amanda Comics", "/comics/seduced-amanda-comics", AUTHOR),
        Triple("VGBabes Comics", "/comics/vgbabes-comics", AUTHOR),
        Triple("SodomSluts.com Comics", "/comics/sodomsluts_com-comics", AUTHOR),
        Triple("AKABUR Comics", "/comics/akabur-comics", AUTHOR),
        Triple("eBluberry Comics", "/comics/ebluberry-comics", AUTHOR),
        Triple("InterracialComicPorn.com Comics", "/comics/interracialcomicporn_com-comics", AUTHOR),
        Triple("Dubh3d-Dubhgilla Comics", "/comics/dubh3d-dubhgilla-comics", AUTHOR),
        Triple("Gush Bomb Comix", "/comics/gush-bomb-comix", AUTHOR),
        Triple("Chiyoji Tomo Comics", "/comics/chiyoji-tomo-comics", AUTHOR),
        Triple("Mangrowing Comics", "/comics/mangrowing-comics", AUTHOR),
        Triple("eAdultComics Collection", "/comics/eadultcomics-collection", AUTHOR),
        Triple("Skulltitti Comics", "/comics/skulltitti-comics", AUTHOR),
        Triple("James Lemay Comics", "/comics/james-lemay-comics", AUTHOR),
        Triple("TalesOfPleasure.com Comics", "/comics/talesofpleasure_com-comics", AUTHOR),
        Triple("Eden Comics", "/comics/eden-comics", AUTHOR),
        Triple("WorldOfPeach Comics", "/comics/worldofpeach-comics", AUTHOR),
        Triple("Daniel40 Comics", "/comics/daniel40-comics", AUTHOR),
        Triple("DontFapGirl Comics", "/comics/dontfapgirl-comics", AUTHOR),
        Triple("Wingbird Comics", "/comics/wingbird-comics", AUTHOR),
        Triple("Intrigue3d.com Comics", "/comics/intrigue3d_com-comics", AUTHOR),
        Triple("Hentaikey Comics", "/comics/hentaikey-comics", AUTHOR),
        Triple("Kamina1978 Comics", "/comics/kamina1978-comics", AUTHOR),
        Triple("3DPerils Comics", "/comics/3dperils-comics", AUTHOR),
        Triple("Tracy Scops Comics", "/comics/tracy-scops-comics", AUTHOR),
        Triple("Shemale3D Comics", "/comics/shemale3d-comics", AUTHOR),
        Triple("InterracialSex3D.com Comics", "/comics/Interracialsex3d-Com-Comix", AUTHOR),
        Triple("MyHentaiGrid Comics", "/comics/myhentaigrid-comics", AUTHOR),
        Triple("Magnifire Comics", "/comics/magnifire-comics", AUTHOR),
        Triple("Reptileye Comics", "/comics/reptileye-comics", AUTHOR),
        Triple("ProjectPinkXXX.com Comics", "/comics/projectpinkxxx_com-comics", AUTHOR),
        Triple("CallMePlisskin Comics", "/comics/callmeplisskin-comics", AUTHOR),
    )

    override fun getSortList() = arrayOf(
        Pair("Viewed", "viewed"),
        Pair("Liked", "liked"),
        Pair("Date", "recent"),
        Pair("A-Z", "az"),
    )
}
