package eu.kanade.tachiyomi.extension.en.silentmangaaudition

import eu.kanade.tachiyomi.source.model.SManga

data class SmaEntry(
    val name: String,
    val url: String,
    val chapterListUrl: String,
    val thumbnailUrl: String,
) {

    fun toSManga(index: Int): SManga = SManga.create().apply {
        title = name
        author = "Various artists"
        status = SManga.COMPLETED
        description = "The theme is… " + name.substringAfter(" ") + "."
        thumbnail_url = thumbnailUrl
        url = "${this@SmaEntry.url},$chapterListUrl,$index"
        initialized = true
    }
}

val SMA_ENTRIES = listOf(
    SmaEntry(
        "SMA-17 “MOMENTS of HASTE, RAGE or SMILES”",
        "/sma17-silent-manga-audition-2022-results-announcement",
        "/v/sma17/bones-by-rimui/?lang=en",
        "https://www.manga-audition.com/wp/wp-content/themes/gridlove-child/assets/img/award-result/sma17/header_sp.png",
    ),
    SmaEntry(
        "SMA-16 “MOMENTS of FEAR, JOY, or LOVE”",
        "/sma16-silent-manga-audition-2021-results-announcement",
        "/v/sma16/lacrimosa-by-laica-chrose/?lang=en",
        "https://www.manga-audition.com/wp/wp-content/uploads/2021/11/hero_sp_new.jpg",
    ),
    SmaEntry(
        "SMA-15 “Moments of CRYING, SMILING or LOVE”",
        "/sma15-silent-manga-audition-2021-award-winners",
        "/v/sma15/blossom-by-enewald/?lang=en",
        "https://www.manga-audition.com/wp/wp-content/themes/gridlove-child/assets/img/award-result/sma15/header_sp.png",
    ),
    SmaEntry(
        "SMA-14 “Creature, Spirits & Monsters”",
        "/sma14-silent-manga-audition-2020-award-winners",
        "/v/sma14/blooming-flower-by-blackwink/",
        "https://www.manga-audition.com/wp/wp-content/themes/gridlove-child/assets/img/award-result/sma14/header_sp.png",
    ),
    SmaEntry(
        "SMA-13 “Together for peace”",
        "/sma13-silent-manga-audition-2020-award-winners/",
        "/v/sma13/homeless-by-simone-sanseverino/?lang=en",
        "https://www.manga-audition.com/wp/wp-content/themes/gridlove-child/assets/img/award-result/sma13/header_sp.png",
    ),
    SmaEntry(
        "SMA-12 “New beginning”",
        "/sma12-silent-manga-audition-2019-award-winners/",
        "/v/sma12/never-late-by-lucas-marques-and-priscilla-miranda/?lang=en",
        "https://www.manga-audition.com/wp/wp-content/themes/gridlove-child/assets/img/award-result/sma12/header_sp.png",
    ),
    SmaEntry(
        "SMA-EX5 “Kumamoto + Do Your Best!”",
        "/smaex5-silent-manga-audition-2019-award-winners/",
        "/v/smaex5/fish-by-youngman/",
        "https://www.manga-audition.com/wp/wp-content/themes/gridlove-child/assets/img/award-result/smaex5/top_banner_sp.jpg",
    ),
    SmaEntry(
        "SMA11 “Promise”",
        "/sma11-silent-manga-audition-2019-award-winners/",
        "/v/sma11/reborn-by-riza-al-assami/?lang=en",
        "https://www.manga-audition.com/wp/wp-content/themes/gridlove-child/assets/img/award-result/sma11/top_banner_sp.jpg",
    ),
    SmaEntry(
        "SMA-EX4 “Kit Kat ROUND”",
        "/smaex4-2018award/",
        "/v/smaex4/lucky-charm-by-harihtaroon/",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/smaex4/smaex4_main01.png",
    ),
    SmaEntry(
        "SMA10 “Effort / Friendship / Victory”",
        "/sma10-2018award-2/",
        "/v/sma10/run-by-riza-al-assami/?lang=en",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/sma10/sma10main_3.png",
    ),
    SmaEntry(
        "SMA-EX3 “Kumamoto + Wasamon”",
        "/smaex3-2018award/",
        "/v/smaex3/to-the-sky-by-zevania-and-nattorin/",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/smaex3/smaex3_main01.png",
    ),
    SmaEntry(
        "SMA9 “Fairness / Respect / Teamwork”",
        "/sma9-2018award/",
        "/v/sma9/fisherman-tales-by-joao-eddie/",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma9/sma9_theme.png",
    ),
    SmaEntry(
        "SMA8 “Fair Play”",
        "/sma8-2017award/",
        "/v/sma8/checkmate-by-sideburn004/",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma8/sma8_theme.png",
    ),
    SmaEntry(
        "SMA7 “Unforgettable Taste”",
        "/sma7-2017award/",
        "/v/sma7/our-promised-land-by-nattorin-and-zevania/",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma7/sma7_theme.png",
    ),
    SmaEntry(
        "SMA-EX2 “Kumamoto + Smile”",
        "http://data.smacmag.net/sma/smaex2-2017award/",
        "https://smacmag.net/v/sma2/drawing-a-smile-out-by-dee-juusan/",
        "http://data.smacmag.net/sma/smaex2-2017award/images/smaex2_title.png",
    ),
    SmaEntry(
        "SMA6 “Childhood”",
        "/sma6-2016award/",
        "/v/sma6/forbidden-by-yos/13828",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma6/SMA06_themes.png",
    ),
    SmaEntry(
        "SMA5 “Friend-ship + Communication Tool”",
        "/sma05-2016award/",
        "/v/sma5/im-happy-by-ds-studio/11915",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma5/sma5_theme.png",
    ),
    SmaEntry(
        "SMA-EX1 “Fukushima Sakuramori”",
        "/smaex1-2016award/",
        "/v/smaex1/seeds-by-jim/9574",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/smaex1/smaex1_theme.png",
    ),
    SmaEntry(
        "SMA4 “A Charming Gift”",
        "/sma04-2015award/",
        "/v/sma4/birdy-by-kalongzz/",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma4/sma4_theme.png",
    ),
    SmaEntry(
        "SMA3 “Mother”",
        "/sma03-2015award/",
        "/v/sma3/homesick-alien-by-ichirou/4390",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma3/sma3_theme.png",
    ),
    SmaEntry(
        "SMA2 “The Finest Smile”",
        "/sma02-2014award/",
        "/v/sma2/fathers-gift-by-ichirou/1775",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma2/sma2_theme.png",
    ),
    SmaEntry(
        "SMA1 “Love Letter”",
        "/sma01-2013award/",
        "/v/sma1/excuse-me-by-alex-irzaqi/",
        "https://s3-ap-northeast-1.amazonaws.com/data.smacmag.net/_images/sma_page/pc/sma1/sma1_theme.png",
    ),
)
