plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NgamenKomik"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://ngamenkomik05.blogspot.com"
    }
}
