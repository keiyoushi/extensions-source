package eu.kanade.tachiyomi.extension.ru.unicomics

import eu.kanade.tachiyomi.source.model.Filter

class Publishers(publishers: Array<String>) : Filter.Select<String>("Издательства (только)", publishers)

class GetEventsList : Filter.Select<String>("События (только)", arrayOf("Нет", "в комиксах"))

internal data class Publisher(val name: String, val url: String)

internal fun getPublishersList() = listOf(
    Publisher("Все", "not"),
    Publisher("Marvel", "marvel"),
    Publisher("DC Comics", "dc"),
    Publisher("Image Comics", "imagecomics"),
    Publisher("Dark Horse Comics", "dark-horse-comics"),
    Publisher("IDW Publishing", "idw"),
    Publisher("Vertigo", "vertigo"),
    Publisher("WildStorm", "wildstorm"),
    Publisher("Dynamite Entertainment", "dynamite"),
    Publisher("Boom! Studios", "boomstudios"),
    Publisher("Avatar Press", "avatarpress"),
    Publisher("Fox Atomicg", "foxatomic"),
    Publisher("Top Shelf Productions", "topshelfproduct"),
    Publisher("Topps", "topps"),
    Publisher("Radical Publishing", "radical-publishing"),
    Publisher("Top Cow", "top-cow"),
    Publisher("Zenescope Entertainment", "zenescope"),
    Publisher("88MPH", "88mph"),
    Publisher("Soleil", "soleil"),
    Publisher("Warner Bros. Entertainment", "warner-bros"),
    Publisher("Ubisoft Entertainment", "ubisoft"),
    Publisher("Oni Press", "oni-press"),
    Publisher("Armada", "delcourt"),
    Publisher("Heavy Metal", "heavy-metal"),
    Publisher("Harris Comics", "harris-comics"),
    Publisher("Antarctic Press", "antarctic-press"),
    Publisher("Valiant", "valiant"),
    Publisher("Disney", "disney"),
    Publisher("Malibu", "malibu"),
    Publisher("Slave Labor", "slave-labor"),
    Publisher("Nbm", "nbm"),
    Publisher("Viper Comics", "viper-comics"),
    Publisher("Random House", "random-house"),
    Publisher("Active Images", "active-images"),
    Publisher("Eurotica", "eurotica"),
    Publisher("Vortex", "vortex"),
    Publisher("Fantagraphics", "fantagraphics"),
    Publisher("Epic", "epic"),
    Publisher("Warp Graphics", "warp-graphics"),
    Publisher("Scholastic Book Services", "scholastic-book-services"),
    Publisher("Ballantine Books", "ballantine-books"),
    Publisher("Id Software", "id-software"),
)

internal val publishersName = getPublishersList().map { it.name }.toTypedArray()
