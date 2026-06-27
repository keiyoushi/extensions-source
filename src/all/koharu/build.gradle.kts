plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SchaleNetwork"
    className = "KoharuFactory"
    versionCode = 20
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("koharu.to")
        host("schale.network")
        host("gehenna.jp")
        host("niyaniya.moe")
        host("seia.to")
        host("shupogaki.moe")
        host("hoshino.one")
        path("/g/..*/..*")
    }
}
