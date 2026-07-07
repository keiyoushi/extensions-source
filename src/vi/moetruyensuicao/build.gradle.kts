plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MoeTruyenSuiCao (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://moe.suicaodex.com"
    }
}
