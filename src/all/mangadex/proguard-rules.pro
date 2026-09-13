# Keep class names for reflection (qualifiedName)
-keepnames class eu.kanade.tachiyomi.extension.all.mangadex.MangaDex

# Keep latestUpdatesRequest and latestUpdatesParse, which are used by Komikku
-keepclassmembers class eu.kanade.tachiyomi.extension.all.mangadex.MangaDex {
    public okhttp3.Request latestUpdatesRequest(int);
    public eu.kanade.tachiyomi.source.model.MangasPage latestUpdatesParse(okhttp3.Response);
}
