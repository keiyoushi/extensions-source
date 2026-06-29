plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ciao Plus"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://ciao.shogakukan.co.jp"
    }
}
