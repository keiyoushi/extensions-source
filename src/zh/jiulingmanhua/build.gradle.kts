import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "90Manhua"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "sinmh"

    source {
        name = "90漫画网"
        lang = "zh"
        baseUrl = "https://www.90mh.cc"
    }

    deeplink {
        host("www.90mh.cc")
        host("m.90mh.cc")
        path("/manhua/..*")
    }
}
