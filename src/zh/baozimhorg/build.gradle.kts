plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GoDa"
    versionCode = 33
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "goda"

    source {
        name = "GoDa漫画"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://baozimh.org",
                "https://godamh.com",
                "https://m.baozimh.one",
                "https://bzmh.org",
                "https://g-mh.org",
                "https://m.g-mh.org",
            )
        }
        id = 774030471139699415L
    }
}
