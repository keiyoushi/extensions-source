import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jmanga"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl {
            custom("https://jmanga.help")
        }
    }
}
