plugins {
    id("lib-multisrc")
}

baseVersionCode = 45

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}
