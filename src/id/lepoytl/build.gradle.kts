import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LepoyTL"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.lepoytl.my.id"
    }
}
