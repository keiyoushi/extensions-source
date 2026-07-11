import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHoNa"
    versionCode = 51
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

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
