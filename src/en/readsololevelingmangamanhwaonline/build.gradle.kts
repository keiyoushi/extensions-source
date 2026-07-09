plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Solo Leveling Manga Manhwa Online"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww3.readsololeveling.org"
    }
}
