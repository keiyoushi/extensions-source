plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Truyen tranh dam my"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "Truyện tranh đam mỹ"
        lang = "vi"
        baseUrl = "https://truyentranhdammyy.site"
    }
}
