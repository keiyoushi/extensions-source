import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SpyFakku"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

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
