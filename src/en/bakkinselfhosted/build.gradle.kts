import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bakkin Self-hosted"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "bakkin"

    source {
        lang = "en"
        baseUrl {
            custom("http://127.0.0.1/")
        }
    }
}
