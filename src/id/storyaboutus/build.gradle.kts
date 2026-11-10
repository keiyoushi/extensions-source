import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Story About Us"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://storyabout-us.com"
    }

    deeplink {
        path("/chapter.html.*")
    }
}
