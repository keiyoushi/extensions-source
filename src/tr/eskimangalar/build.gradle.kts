import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Eski Mangalar"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "uzaymanga"

    source {
        lang = "tr"
        baseUrl = "https://eskimangalar.com"
    }
}
