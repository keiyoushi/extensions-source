import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comizy"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    theme = "mangak"
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://comizy.io"
    }
}
