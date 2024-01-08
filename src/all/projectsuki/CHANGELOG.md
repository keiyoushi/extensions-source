## Version 1.4.3

- entries incorrectly listed as PUBLISHING_FINISHED now are correctly listed as COMPLETED
- fixes a rare bug that would throw a NoSuchElementException when the status or origin fields
  weren't found in the details table.
- separate Smart and Simple search in code to make it easier to debug and extend it in the future
- add activities to handle /book and /read URLs (search/ was already present)
- handle /book and /read urls as search query

## Version 1.4.2

- Improved search feature
- New and improved Popular tab
- Old Popular tab moved to Latest
- Fixed chapter numbering issues when "Chapter" wasn't explicitly present (e.g. "Ch. 2")
- Added chapter number inference for when the above fails
- Improved user feedback for errors and issues
- Fixed wording and clarity on most descriptions
- Added simple search option for Android API < 24
- Chapter language will now appear right of the scan group
- Enhanced chapters sorting (number > group > language)
- Changed extension language from English to Multi

## Version 1.4.1

First version of the extension:

- basic functionality
- basic search, limited to full-site