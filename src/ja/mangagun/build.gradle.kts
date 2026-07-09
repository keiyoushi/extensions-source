plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NihonKuni"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://nihonkuni.com"
        // Formerly "MangaGun(漫画軍)"
        id = 3811800324362294701L
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
