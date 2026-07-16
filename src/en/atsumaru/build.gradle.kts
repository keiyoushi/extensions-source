import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Atsumaru"
    versionCode = 19
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://atsu.moe"
        versionId = 2
    }
}
