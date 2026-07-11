import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sekte Komik"
    versionCode = 26
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "colorlibanime"

    source {
        lang = "id"
        baseUrl = "https://sektekomik.xyz"
    }
}
