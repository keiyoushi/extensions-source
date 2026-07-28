import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Natsu"
    versionCode = 34
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl {
            custom("https://natsu.one")
        }
    }
}
