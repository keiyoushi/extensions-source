import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SlashLib"
    pkgName = "ru.yaoilib"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://v2.shlib.life")
        }
        lang = "ru"
        id = 2730544188738947015L
    }
}
