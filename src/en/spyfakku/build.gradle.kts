plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SpyFakku"
    versionCode = 16
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl("https://hentalk.pw") {
            mirrors = listOf(
                "https://fakku.cc",
                "https://fakkuonion.airdns.org:4096",
            )
        }
    }
}
