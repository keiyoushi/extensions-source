package eu.kanade.tachiyomi.extension.zh.kuaikuai3

class Kuaikuai3 : MCCMSReduced("快快漫画3", "https://mobile3.manhuaorg.com") {

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "okhttp/3.14.7")
}
