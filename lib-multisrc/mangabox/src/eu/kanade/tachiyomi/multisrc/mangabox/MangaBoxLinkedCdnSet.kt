package eu.kanade.tachiyomi.multisrc.mangabox

class MangaBoxLinkedCdnSet : LinkedHashSet<String>() {
    fun moveItemToFirst(item: String) {
        // Lock the object to avoid multi threading issues
        synchronized(this) {
            if (this.contains(item) && this.first() != item) {
                // Remove the item from the current set
                this.remove(item)
                // Create a new list with the item at the first position
                val newItems = mutableListOf(item)
                // Add the remaining items
                newItems.addAll(this)
                // Clear the current set and add all items from the new list
                this.clear()
                this.addAll(newItems)
            }
        }
    }
}
