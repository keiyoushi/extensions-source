import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DAYS NEO"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://daysneo.com"
    }

    deeplink {
        host("daysneo.com")
        host("www.daysneo.com")
        path("/works/..*")
        path("/sp/works/..*")
        path("/ranking/..*")
        path("/sp/ranking/..*")
        path("/search/.*")
        path("/sp/search/.*")
    }
}
