import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goc Truyen Tranh"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "GocTruyenTranh"
        lang = "vi"
        baseUrl {
            custom("https://goctruyentranh.com")
        }
    }
}
