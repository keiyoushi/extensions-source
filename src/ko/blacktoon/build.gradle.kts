import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BlackToon"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "블랙툰"
        lang = "ko"
        baseUrl {
            custom("https://blacktoon426.com")
        }
    }
}
