plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zonatmo.to (unoriginal)"
    className = "ZonatmoTo"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("zonatmo.to")
        host("www.zonatmo.to")
        path("/manga/..*")
    }
}
