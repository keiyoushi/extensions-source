import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Natsu"
    versionCode = 33
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl = "https://natsu.one"
    }
}
