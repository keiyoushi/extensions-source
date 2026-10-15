import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuaren"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "漫画人"
        lang = "zh"
        baseUrl = "https://mangaapi.manhuaren.com"
    }

    deeplink {
        host("www.manhuaren.com")
        path("/manhua-..*")
    }
}
