plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Festa"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "clipstudioreader"

    source {
        lang = "ja"
        baseUrl = "https://comic.iowl.jp"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
