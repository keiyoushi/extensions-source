import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toptoon.net"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "TOPTOON頂通"
        lang = "zh"
        baseUrl = "https://www.toptoon.net"
    }
}
