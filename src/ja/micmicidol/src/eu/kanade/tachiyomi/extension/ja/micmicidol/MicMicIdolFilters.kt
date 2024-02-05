package eu.kanade.tachiyomi.extension.ja.micmicidol

import eu.kanade.tachiyomi.source.model.Filter

class LabelFilter(name: String, labels: List<Label>) : Filter.Group<Label>(name, labels)

class Label(name: String) : Filter.CheckBox(name)

// copy([...$0.querySelectorAll("li a[href]")].filter(e => e.getAttribute("href") != "#").map((e) => `"${decodeURIComponent(e.getAttribute("href").replace("/search/label/", "").replace("?max-results=50", ""))}",`).join("\n"))
fun getJapanMagazines() = listOf(
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

fun getJapanFashion() = listOf(
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

fun getTypes() = listOf(
    "- Cover",
    "- Japan Magazine",
    "- Japan Fashion Magazine",
    "- Japan Idol Photobook",
    "- Asia Idol",
)
