import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FireCross"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "clipstudioreader"

    source {
        lang = "ja"
        baseUrl = "https://firecross.jp"
    }
}
