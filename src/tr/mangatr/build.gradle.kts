import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-TR"
    versionCode = 24
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    deeplink {
        host("www.manga-tr.com")
        host("manga-tr.com")
        path("/manga-..*")
    }

    source {
        lang = "tr"
        baseUrl = "https://manga-tr.com"
    }
}
