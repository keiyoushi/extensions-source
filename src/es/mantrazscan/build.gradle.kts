import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa Scan"
    versionCode = 57
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://mantrazscaan.com"
        id = 7172992930543738693L
    }
}
