import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Japscan"
    versionCode = 70
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://www.japscan.foo/mangas/?sort=popular&p=1"
        id = 11L
    }
}
