import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai da Tia"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://hentaidatia.com"
    }

    deeplink {
        host("hentaidatia.com")
        host("www.hentaidatia.com")
        path("/..*")
    }
}
