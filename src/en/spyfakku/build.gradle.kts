plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SpyFakku"
    versionCode = 16
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://hentalk.pw",
                "https://fakku.cc",
                "https://fakkuonion.airdns.org:4096",
            )
        }
    }
}
