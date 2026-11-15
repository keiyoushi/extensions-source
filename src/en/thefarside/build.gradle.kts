import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Far Side"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.thefarside.com"
    }

    deeplink {
        host("www.thefarside.com")
        host("thefarside.com")
        path("/20..*")
        path("/new-stuff..*")
        path("/comic-collections..*")
        path("/sketchbooks..*")
    }
}
