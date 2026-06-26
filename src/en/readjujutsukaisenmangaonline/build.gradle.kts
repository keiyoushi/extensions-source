plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Jujutsu Kaisen Manga Online"
    className = "ReadJujutsuKaisenMangaOnline"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"
    baseUrl = "https://ww5.readjujutsukaisen.com"
}
