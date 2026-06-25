plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTaro"
    className = "MangaTaroFactory"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangataro"
    baseUrl = "https://mangataro.org"
}
