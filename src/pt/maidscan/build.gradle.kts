import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Maid Scan"
    versionCode = 51
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "greenshit"

    source {
        lang = "pt-BR"
        baseUrl = "https://empreguetes.wtf"
    }
}
