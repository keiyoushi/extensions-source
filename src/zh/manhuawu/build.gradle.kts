import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuawu"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mccms"

    source {
        name = "漫画屋"
        lang = "zh"
        baseUrl = "https://www.mhua5.com"
    }
}
