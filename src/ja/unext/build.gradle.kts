import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "U-NEXT"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://video.unext.jp"
    }
}
