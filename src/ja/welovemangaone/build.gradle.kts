import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Love4u"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://love4u.net"
        id = 1647179844716143786L
    }
}
