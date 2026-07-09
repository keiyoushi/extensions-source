plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FireCross"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "clipstudioreader"

    source {
        lang = "ja"
        baseUrl = "https://firecross.jp"
    }
}
