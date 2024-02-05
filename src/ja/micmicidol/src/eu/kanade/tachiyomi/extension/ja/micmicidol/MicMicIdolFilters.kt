package eu.kanade.tachiyomi.extension.ja.micmicidol

import eu.kanade.tachiyomi.source.model.Filter

class LabelFilter(name: String, labels: List<Label>) : Filter.Group<Label>(name, labels)

class Label(name: String) : Filter.CheckBox(name)

// copy([...$0.querySelectorAll("li a[href]")].filter(e => e.getAttribute("href") != "#").map((e) => `Label("${decodeURIComponent(e.getAttribute("href").replace("/search/label/", "").replace("?max-results=50", ""))}"),`).join("\n"))
fun getJapanMagazines() = listOf(
    Label("cyzo"),
    Label("EnTame"),
    Label("EX大衆"),
    Label("Friday"),
    Label("Flash"),
    Label("Shonen Magazine"),
    Label("Shonen Sunday"),
    Label("Weekly Shonen Champion"),
    Label("Weekly Big Comic Spirits"),
    Label("Weekly Jitsuwa"),
    Label("Weekly Playboy"),
    Label("Weekly SPA!"),
    Label("Young Animal"),
    Label("Young Champion"),
    Label("Young Gangan"),
    Label("Young Jump"),
    Label("Young Magazine"),
)

fun getJapanFashion() = listOf(
    Label("andGIRL"),
    Label("aR"),
    Label("Baila"),
    Label("Biteki"),
    Label("CanCam"),
    Label("Classy"),
    Label("ELLE Japan"),
    Label("Ginger"),
    Label("JJ"),
    Label("Maquia"),
    Label("Mina"),
    Label("MORE"),
    Label("Non-no"),
    Label("Oggi"),
    Label("Ray"),
    Label("Scawaii"),
    Label("Steady"),
    Label("ViVi"),
    Label("VoCE"),
    Label("With"),
)

fun getTypes() = listOf(
    Label("- Cover"),
    Label("- Japan Magazine"),
    Label("- Japan Fashion Magazine"),
    Label("- Japan Idol Photobook"),
    Label("- Asia Idol"),
)
