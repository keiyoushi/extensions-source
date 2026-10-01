import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WeLoveManga"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://weloma.net"
        // Formerly "RawLH"
        id = 7595224096258102519L
    }
}
