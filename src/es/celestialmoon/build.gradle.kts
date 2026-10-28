import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Celestial Moon"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://lecturacelestialmoon.site"
        // ZeistManga -> MangaThemesia
        versionId = 2
    }

    deeplink {
        host("lecturacelestialmoon.site")
        host("palegoldenrod-mallard-636284.hostingersite.com")
        host("mediumaquamarine-cheetah-476183.hostingersite.com")
        path("/obra/..*")
        path("/leer/..*")
    }
}
