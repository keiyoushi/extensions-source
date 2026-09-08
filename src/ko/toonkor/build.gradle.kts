import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonkor"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ko"
        baseUrl {
            custom("https://tkor148.com")
        }
    }
}
