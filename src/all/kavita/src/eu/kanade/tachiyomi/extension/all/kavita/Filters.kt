package eu.kanade.tachiyomi.extension.all.kavita

import eu.kanade.tachiyomi.extension.all.kavita.KavitaConstants.noSmartFilterSelected
import eu.kanade.tachiyomi.source.model.Filter

class UserRating :
    Filter.Select<String>(
        "Minimum Rating",
        arrayOf(
            "Any",
            "1 star",
            "2 stars",
            "3 stars",
            "4 stars",
            "5 stars",
        ),
    )
class SmartFiltersFilter(smartFilters: Array<String>) :
    Filter.Select<String>("Smart Filters", arrayOf(noSmartFilterSelected) + smartFilters)
class SortFilter(sortables: Array<String>) : Filter.Sort("Sort by", sortables, Selection(0, true))

val sortableList = listOf(
    Pair("Sort name", 1),
    Pair("Created", 2),
    Pair("Last modified", 3),
    Pair("Item added", 4),
    Pair("Time to Read", 5),
    Pair("Release year", 6),
)

class StatusFilter(name: String) : Filter.CheckBox(name, false)
class StatusFilterGroup(filters: List<StatusFilter>) :
    Filter.Group<StatusFilter>("Status", filters)

class ReleaseYearRange(name: String) : Filter.Text(name)
class ReleaseYearRangeGroup(filters: List<ReleaseYearRange>) :
    Filter.Group<ReleaseYearRange>("Release Year", filters)
class GenreFilter(name: String) : Filter.TriState(name)
class GenreFilterGroup(genres: List<GenreFilter>) :
    Filter.Group<GenreFilter>("Genres", genres)

class TagFilter(name: String) : Filter.TriState(name)
class TagFilterGroup(tags: List<TagFilter>) : Filter.Group<TagFilter>("Tags", tags)

class AgeRatingFilter(name: String) : Filter.TriState(name)
class AgeRatingFilterGroup(ageRatings: List<AgeRatingFilter>) :
    Filter.Group<AgeRatingFilter>("Age Rating", ageRatings)

class FormatFilter(name: String) : Filter.CheckBox(name, false)
class FormatsFilterGroup(formats: List<FormatFilter>) :
    Filter.Group<FormatFilter>("Formats", formats)

class CollectionFilter(name: String) : Filter.TriState(name)
class CollectionFilterGroup(collections: List<CollectionFilter>) :
    Filter.Group<CollectionFilter>("Collection", collections)

class LanguageFilter(name: String) : Filter.TriState(name)
class LanguageFilterGroup(languages: List<LanguageFilter>) :
    Filter.Group<LanguageFilter>("Language", languages)

class LibraryFilter(library: String) : Filter.TriState(library)
class LibrariesFilterGroup(libraries: List<LibraryFilter>) :
    Filter.Group<LibraryFilter>("Libraries", libraries)

class PubStatusFilter(name: String) : Filter.CheckBox(name, false)
class PubStatusFilterGroup(status: List<PubStatusFilter>) :
    Filter.Group<PubStatusFilter>("Publication Status", status)

class PeopleHeaderFilter(name: String) :
    Filter.Header(name)
class PeopleSeparatorFilter :
    Filter.Separator()

class WriterPeopleFilter(name: String) : Filter.CheckBox(name, false)
class WriterPeopleFilterGroup(peoples: List<WriterPeopleFilter>) :
    Filter.Group<WriterPeopleFilter>("Writer", peoples)

class PencillerPeopleFilter(name: String) : Filter.CheckBox(name, false)
class PencillerPeopleFilterGroup(peoples: List<PencillerPeopleFilter>) :
    Filter.Group<PencillerPeopleFilter>("Penciller", peoples)

class InkerPeopleFilter(name: String) : Filter.CheckBox(name, false)
class InkerPeopleFilterGroup(peoples: List<InkerPeopleFilter>) :
    Filter.Group<InkerPeopleFilter>("Inker", peoples)

class ColoristPeopleFilter(name: String) : Filter.CheckBox(name, false)
class ColoristPeopleFilterGroup(peoples: List<ColoristPeopleFilter>) :
    Filter.Group<ColoristPeopleFilter>("Colorist", peoples)

class LettererPeopleFilter(name: String) : Filter.CheckBox(name, false)
class LettererPeopleFilterGroup(peoples: List<LettererPeopleFilter>) :
    Filter.Group<LettererPeopleFilter>("Letterer", peoples)

class CoverArtistPeopleFilter(name: String) : Filter.CheckBox(name, false)
class CoverArtistPeopleFilterGroup(peoples: List<CoverArtistPeopleFilter>) :
    Filter.Group<CoverArtistPeopleFilter>("Cover Artist", peoples)

class EditorPeopleFilter(name: String) : Filter.CheckBox(name, false)
class EditorPeopleFilterGroup(peoples: List<EditorPeopleFilter>) :
    Filter.Group<EditorPeopleFilter>("Editor", peoples)

class PublisherPeopleFilter(name: String) : Filter.CheckBox(name, false)
class PublisherPeopleFilterGroup(peoples: List<PublisherPeopleFilter>) :
    Filter.Group<PublisherPeopleFilter>("Publisher", peoples)

class CharacterPeopleFilter(name: String) : Filter.CheckBox(name, false)
class CharacterPeopleFilterGroup(peoples: List<CharacterPeopleFilter>) :
    Filter.Group<CharacterPeopleFilter>("Character", peoples)

class TranslatorPeopleFilter(name: String) : Filter.CheckBox(name, false)
class TranslatorPeopleFilterGroup(peoples: List<TranslatorPeopleFilter>) :
    Filter.Group<TranslatorPeopleFilter>("Translator", peoples)
