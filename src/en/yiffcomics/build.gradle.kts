import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yiff Comics (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://yiff-comics.com"
    }

    deeplink {
        host("yiff-comics.com")
        host("www.yiff-comics.com")
        path("/yiff/..*")
        path("/artist/..*")
        path("/genre/..*")
        path("/orientation/..*")
        path("/search/.*")
    }
}
