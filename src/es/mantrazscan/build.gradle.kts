import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mantraz Scan"
    versionCode = 58
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://mantrazscans.lat"
        id = 7172992930543738693L
    }
}
