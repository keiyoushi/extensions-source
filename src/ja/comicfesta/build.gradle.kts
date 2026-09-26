import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Festa"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "clipstudioreader"

    source {
        lang = "ja"
        baseUrl = "https://comic.iowl.jp"
    }
}
