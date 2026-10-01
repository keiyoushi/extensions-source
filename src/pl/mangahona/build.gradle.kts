import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHoNa"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "pl"
        baseUrl = "https://mangahona.pl"
    }

    deeplink {
        host("mangahona.pl")
        path("/manga/.*")
        path("/czytaj/.*/.*")
    }
}
