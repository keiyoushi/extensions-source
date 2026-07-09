plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SummerToon"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://summertoons.net"
    }
}
