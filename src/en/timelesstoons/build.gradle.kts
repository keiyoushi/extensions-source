import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TimelessToons"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://timelesstoons.org"
    }
}
