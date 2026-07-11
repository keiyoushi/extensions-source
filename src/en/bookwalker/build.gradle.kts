plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BookWalker"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://bookwalker.com"
        id = 2744810059574599668L
    }
}

dependencies {

    implementation(project(":lib:e4p"))
}
