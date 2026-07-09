plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Nanatsu no Taizai 7 Deadly Sins Manga Online"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww7.read7deadlysins.com"
    }
}
