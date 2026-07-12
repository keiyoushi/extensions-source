import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SlashLib"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://slashlib.me")
        }
        lang = "ru"
        id = 2730544188738947015L
    }
}
