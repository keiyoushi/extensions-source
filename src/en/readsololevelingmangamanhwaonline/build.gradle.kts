import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Solo Leveling Manga Manhwa Online"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww4.readsololeveling.org"
    }
}
