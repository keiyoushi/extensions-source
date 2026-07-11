import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiHere"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentaihere.com"
    }

    deeplink {
        host("hentaihere.com")
        path("/m/S..*")
    }
}
