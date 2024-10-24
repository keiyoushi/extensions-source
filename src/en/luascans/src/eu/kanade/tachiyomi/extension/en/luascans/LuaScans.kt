package eu.kanade.tachiyomi.extension.en.luascans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class LuaScans : Keyoapp(
    "Lua Scans",
    "https://luacomic.net",
    "en",
) {
    // migrated from MangaThemesia to Keyoapp
    override val versionId = 2

    override val cdnUrl = "https://3xfsjdlc.is1.buzz/uploads"
}
