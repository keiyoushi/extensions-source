import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDenizi"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://mangadenizi.net"
    }
}
