import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaCloud"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangacloud.org"
    }

    deeplink {
        host("mangacloud.org")
        path("/comic/..*")
    }
}
