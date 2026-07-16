import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MunTruyen"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://munedge.com")
        }
    }
}
