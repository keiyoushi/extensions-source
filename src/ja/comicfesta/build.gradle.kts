plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Festa"
    className = "ComicFesta"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "clipstudioreader"
    baseUrl = "https://comic.iowl.jp"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
