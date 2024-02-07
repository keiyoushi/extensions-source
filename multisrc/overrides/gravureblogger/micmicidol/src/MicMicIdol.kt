package eu.kanade.tachiyomi.extension.ja.micmicidol

import eu.kanade.tachiyomi.multisrc.gravureblogger.GravureBlogger

class MicMicIdol : GravureBlogger("MIC MIC IDOL", "https://www.micmicidol.club", "ja") {
    override val labelFilters = buildMap {
        put("Type", getTypes())
        put("Japan Magazine", getJapanMagazines())
        put("Japan Fashion", getJapanFashion())
    }

    private fun getJapanMagazines() = listOf(
        "cyzo",
        "EnTame",
        "EX大衆",
        "Friday",
        "Flash",
        "Shonen Magazine",
        "Shonen Sunday",
        "Weekly Shonen Champion",
        "Weekly Big Comic Spirits",
        "Weekly Jitsuwa",
        "Weekly Playboy",
        "Weekly SPA!",
        "Young Animal",
        "Young Champion",
        "Young Gangan",
        "Young Jump",
        "Young Magazine",
    )

    private fun getJapanFashion() = listOf(
        "andGIRL",
        "aR",
        "Baila",
        "Biteki",
        "CanCam",
        "Classy",
        "ELLE Japan",
        "Ginger",
        "JJ",
        "Maquia",
        "Mina",
        "MORE",
        "Non-no",
        "Oggi",
        "Ray",
        "Scawaii",
        "Steady",
        "ViVi",
        "VoCE",
        "With",
    )

    private fun getTypes() = listOf(
        "- Cover",
        "- Japan Magazine",
        "- Japan Fashion Magazine",
        "- Japan Idol Photobook",
        "- Asia Idol",
    )
}
