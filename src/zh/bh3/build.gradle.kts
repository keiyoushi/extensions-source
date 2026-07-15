import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BH3"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "《崩坏3》IP站"
        lang = "zh"
        baseUrl = "https://comic.bh3.com"
    }
}
