import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Maid Scan"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "greenshit"

    source {
        lang = "pt-BR"
        baseUrl = "https://empreguetes.wtf"
    }
}
