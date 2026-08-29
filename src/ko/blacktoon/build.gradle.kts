import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BlackToon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "블랙툰"
        lang = "ko"
        baseUrl = "https://blacktoon.me"
    }
}
