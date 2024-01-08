# MangaDex

Table of Content
- [FAQ](#FAQ)
  - [Version 5 API Rewrite](#version-5-api-rewrite)
- [Guides](#Guides)
  - [How can I block particular Scanlator Groups?](#how-can-i-block-particular-scanlator-groups)
    
Don't find the question you are look for go check out our general FAQs and Guides over at [Extension FAQ](https://tachiyomi.org/help/faq/#extensions) or [Getting Started](https://tachiyomi.org/help/guides/getting-started/#installation)

## FAQ

### Version 5 API Rewrite

#### Why are all my manga saying "Manga ID format has changed, migrate from MangaDex to MangaDex to continue reading"?
You need to [migrate](https://tachiyomi.org/help/guides/source-migration/) all your MangaDex manga from MangaDex to MangaDex as MangaDex has changed their manga ID system from IDs to UUIDs.

#### Why can I not restore from a JSON backup?
JSON backups are now unusable due to the ID change. You will have to manually re-add your manga.

## Guides

### What does the Status of a Manga in Tachiyomi mean?

Please refer to the following table

| Status in Tachiyomi | in MangaDex            | Remarks |
|---------------------|------------------------|---------|
| Ongoing             | Publication: Ongoing   |         |
| Cancelled           | Publication: Cancelled | This title was abruptly stopped and will not resume |
| Publishing Finished | Publication: Completed | The title is finished in its original language. However, Translations remain |
| On_Hiatus           | Publication: Hiatus    | The title is not currently receiving any new chapters |
| Completed           | Completed/Cancelled    | All chapters are translated and available |
| Unknown             | Unknown                | There is no info about the Status of this Entry |

### How can I block particular Scanlator Groups?

The **MangaDex** extension allows blocking **Scanlator Groups**. Chapters uploaded by a **Blocked Scanlator Group** will not show up in **Latest** or in **Manga feed** (chapters list). For now, you can only block Groups by entering their UUIDs manually.

Follow the following steps to easily block a group from the Tachiyomi MangaDex extension:

A. Finding the **UUIDs**:
- Go to [https://mangadex.org](https://mangadex.org) and **Search** for the Scanlation Group that you wish to block and view their Group Details
- Using the URL of this page, get the 16-digit alphanumeric string which will be the UUID for that scanlation group
- For Example:
    * The Group *Tristan's test scans* has the URL
        - [https://mangadex.org/group/6410209a-0f39-4f51-a139-bc559ad61a4f/tristan-s-test-scans](https://mangadex.org/group/6410209a-0f39-4f51-a139-bc559ad61a4f/tristan-s-test-scans)
        - Therefore, their UUID will be `6410209a-0f39-4f51-a139-bc559ad61a4f`
    * Other Examples include:
        + Azuki Manga     | `5fed0576-8b94-4f9a-b6a7-08eecd69800d`
        + Bilibili Comics | `06a9fecb-b608-4f19-b93c-7caab06b7f44`
        + Comikey         | `8d8ecf83-8d42-4f8c-add8-60963f9f28d9`
        + INKR            | `caa63201-4a17-4b7f-95ff-ed884a2b7e60`
        + MangaHot        | `319c1b10-cbd0-4f55-a46e-c4ee17e65139`
        + MangaPlus       | `4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb`

B. Blocking a group using their UUID in Tachiyomi MangaDex extension `v1.2.150+`:
1. Go to **Browse** → **Extensions**.
1. Click on **MangaDex** extension and then **Settings** under your Language of choice.
1. Tap on the option **Block Groups by UUID** and enter the UUIDs.
    - By Default, the following groups are blocked:
     ```
     Azuki Manga, Bilibili Comics, Comikey, INKR, MangaHot & MangaPlus
     ```
    - Which are entered as:
     ```
     5fed0576-8b94-4f9a-b6a7-08eecd69800d, 06a9fecb-b608-4f19-b93c-7caab06b7f44,
     8d8ecf83-8d42-4f8c-add8-60963f9f28d9, caa63201-4a17-4b7f-95ff-ed884a2b7e60,
     319c1b10-cbd0-4f55-a46e-c4ee17e65139, 4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb
     ```
