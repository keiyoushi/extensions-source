import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sagrado Imp\u00e9rio da Britannia"
    versionCode = 53
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangotheme"

    source {
        lang = "pt-BR"
        baseUrl = "https://imperiodabritannia.net"
        id = 7355004027880350247L
    }
}
