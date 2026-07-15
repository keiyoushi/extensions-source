import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwasMe"
    versionCode = 3
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manhwas.me"
        id = 8004442288770923365L
    }
}
