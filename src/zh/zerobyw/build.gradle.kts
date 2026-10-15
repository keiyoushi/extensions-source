import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zerobyw"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "zero搬运网"
        lang = "zh"
        baseUrl {
            custom("http://www.zerobyw33.com")
        }
    }
}
