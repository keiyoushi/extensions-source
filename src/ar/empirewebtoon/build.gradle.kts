import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Empire Webtoon"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl {
            custom("https://webtoonempire-bl.com")
        }
    }
}
