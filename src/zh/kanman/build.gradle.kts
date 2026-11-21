import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kanman"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "看漫画"
        lang = "zh"
        baseUrl = "https://www.kanman.com"
    }

    deeplink {
        host("www.kanman.com")
        host("kanman.com")
        host("m.kanman.com")
        path("/..*")
    }
}
