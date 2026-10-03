import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Terra Historicus"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "泰拉记事社"
        lang = "zh"
        baseUrl = "https://comic.hypergryph.com"
    }
}
