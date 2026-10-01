import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "oots"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "The Order Of The Stick (OOTS)"
        lang = "en"
        baseUrl = "https://www.giantitp.com"
    }
}
