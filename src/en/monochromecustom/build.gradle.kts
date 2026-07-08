plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Monochrome Custom"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "monochrome"

    source {
        lang = "en"
        baseUrl {
            custom("https://monochromecms.netlify.app")
        }
    }
}
