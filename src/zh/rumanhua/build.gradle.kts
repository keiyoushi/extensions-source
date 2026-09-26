import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rumanhua"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mmlook"

    source {
        name = "如漫画"
        lang = "zh"
        baseUrl = "https://m.rumanhua2.com"
    }
}
