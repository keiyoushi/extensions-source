import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhua Manga"
    theme = "mccms"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "漫画manga"
        lang = "zh"
        baseUrl = "https://www.mh03.com"
    }

    deeplink {
        host("www.mh03.com")
        host("mh03.com")
        path("/comic_..*")
        path("/chapter_..*")
    }
}
