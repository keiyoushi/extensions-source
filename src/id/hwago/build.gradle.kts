import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hwago"
    versionCode = 56
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "hwalumi"

    source {
        lang = "id"
        baseUrl = "https://02.hwago.xyz"
    }
}
