#!/usr/bin/env python3

import argparse
import re
from pathlib import Path
from urllib.parse import urlparse


def ascii_validator(value: str) -> str:
    value = value.strip()
    try:
        value.encode("ascii")
    except UnicodeEncodeError:
        raise argparse.ArgumentTypeError(f"'{value}' is not ASCII")

    return value


def validate_args(args: argparse.Namespace, parser: argparse.ArgumentParser):
    # Validate language
    lang = args.lang
    if not re.fullmatch(r"(?:[a-z]{2,3}(?:-[A-Z]{2,3})?|all)", lang):
        parser.error(
            f"invalid language: '{lang}' (must be a 2- or 3-letter ISO language code)"
        )

    # Validate base URL
    baseurl = urlparse(args.baseurl)
    if baseurl.scheme != "https" or not baseurl.netloc:
        parser.error(
            f"invalid baseurl: '{baseurl}' (must include scheme 'https://' and a host)"
        )
    args.baseurl = f"{baseurl.scheme}://{baseurl.netloc}"

    # Validate extension repo path
    path = Path(args.path).resolve()
    if not path.is_dir():
        parser.error(f"invalid path: '{path}' (directory does not exist)")
    elif "common" not in [p.name for p in path.iterdir()]:
        parser.error(f"invalid path: '{path}' (not a valid extension repo directory)")
    args.path = path

    # Validate multisrc theme if provided
    if args.multisrc:
        multisrc_dir = path / "lib-multisrc"
        if not multisrc_dir.exists() or not multisrc_dir.is_dir():
            parser.error(f"No multisrc found in extension repo path: '{path}'")

        multisrc_theme = args.multisrc.lower()
        if multisrc_theme not in [p.name for p in multisrc_dir.iterdir() if p.is_dir()]:
            parser.error(
                f"invalid multisrc theme: '{multisrc_theme}' (not found in '{multisrc_dir}')"
            )
        args.multisrc = multisrc_theme


def get_ext_classname(extname: str) -> str:
    sanitized = re.sub(r"[^A-Za-z0-9 ]", "", extname)
    return "".join(word.capitalize() for word in re.findall(r"[A-Za-z0-9]+", sanitized))


if __name__ == "__main__":
    argparser = argparse.ArgumentParser()
    argparser.add_argument(
        "-n", "--extname", type=ascii_validator, help="Extension name", required=True
    )
    argparser.add_argument(
        "--source-name",
        type=str,
        help="Source Name (defaults to extname)",
        required=False,
    )
    argparser.add_argument(
        "-l",
        "--lang",
        "--language",
        type=ascii_validator,
        help="Extension language",
        required=True,
    )
    argparser.add_argument(
        "-u",
        "--baseurl",
        type=ascii_validator,
        help="Extension BaseUrl (must be an https URL)",
        required=True,
    )
    argparser.add_argument(
        "-c",
        "--content-warning",
        choices=["SAFE", "MIXED", "NSFW"],
        type=str.upper,
        default="SAFE",
        help="Content warning level",
    )
    argparser.add_argument(
        "-m",
        "--multisrc",
        type=ascii_validator,
        help="multisrc theme",
        required=False,
    )
    argparser.add_argument(
        "--path",
        type=str,
        help="Path to extension repo directory (defaults to cwd)",
        required=False,
        default=".",
    )
    args = argparser.parse_args()
    validate_args(args, argparser)

    ext_dir_name = re.sub(r"[^A-Za-z0-9]", "", args.extname).lower()
    ext_dir_lang = args.lang.split("-")[0]
    ext_dir = args.path / "src" / ext_dir_lang / ext_dir_name

    if ext_dir.exists():
        print(f"Extension directory already exists: '{ext_dir}'")
        exit(1)

    ext_dir.mkdir(parents=True)
    print(f"Created extension directory: '{ext_dir}'")

    with open(ext_dir / "build.gradle.kts", "w") as f:
        f.write("plugins {\n")
        f.write("\talias(kei.plugins.extension)\n")
        f.write("}\n\n")
        f.write("keiyoushi {\n")
        f.write(f'\tname = "{args.extname}"\n')

        if args.multisrc:
            f.write(f'\ttheme = "{args.multisrc}"\n')
            f.write("\tversionCode = 0\n")
        else:
            f.write("\tversionCode = 1\n")

        f.write(f"\tcontentWarning = ContentWarning.{args.content_warning}\n")
        f.write('\tlibVersion = "1.4"\n\n')

        f.write("\tsource {\n")
        if args.source_name:
            f.write(f'\t\tname = "{args.source_name}"\n')
        f.write(f'\t\tlang = "{args.lang}"\n')
        f.write(f'\t\tbaseUrl = "{args.baseurl}"\n')
        f.write("\t}\n")

        if not args.multisrc:
            f.write("\n\tdeeplink {\n")
            f.write('\t\tpath("/..*")\n')
            f.write("\t}\n")

        f.write("}\n")

    ext_package_dir = (
        ext_dir
        / "src"
        / "eu"
        / "kanade"
        / "tachiyomi"
        / "extension"
        / ext_dir_lang
        / ext_dir_name
    )
    ext_package_dir.mkdir(parents=True)
    print(f"Created extension package directory: '{ext_package_dir}'")

    with open(ext_package_dir / f"{get_ext_classname(args.extname)}.kt", "w") as f:
        f.write(
            f"""package eu.kanade.tachiyomi.extension.{ext_dir_lang}.{ext_dir_name}\n\n"""
        )

        if args.multisrc:
            f.write(
                f"import eu.kanade.tachiyomi.multisrc.{args.multisrc}.{get_ext_classname(args.multisrc)}\n"
            )
            f.write("import keiyoushi.annotation.Source\n\n")
            f.write("@Source\n")
            f.write(
                f"abstract class {get_ext_classname(args.extname)} : {get_ext_classname(args.multisrc)}()\n"
            )
        else:
            f.write("import eu.kanade.tachiyomi.source.model.FilterList\n")
            f.write("import eu.kanade.tachiyomi.source.model.MangasPage\n")
            f.write("import eu.kanade.tachiyomi.source.model.Page\n")
            f.write("import eu.kanade.tachiyomi.source.model.SChapter\n")
            f.write("import eu.kanade.tachiyomi.source.model.SManga\n")
            f.write("import eu.kanade.tachiyomi.source.online.HttpSource\n")
            f.write("import keiyoushi.annotation.Source\n")
            f.write("import okhttp3.Request\n")
            f.write("import okhttp3.Response\n")
            f.write("import rx.Observable\n")
            f.write("import java.lang.UnsupportedOperationException\n\n")

            f.write("@Source\n")
            f.write(
                f"abstract class {get_ext_classname(args.extname)} : HttpSource() {{\n\n"
            )
            f.write("\toverride val supportsLatest = true\n\n")
            f.write("\toverride val client = network.client\n\n")

            f.write("\toverride fun headersBuilder() = super.headersBuilder()\n")
            f.write('\t\t.set("Referer", "$baseUrl/")\n\n')

            f.write(
                "\toverride fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {\n"
            )
            f.write('\t\tif (query.startsWith("https://")) {\n')
            f.write('\t\t\tthrow Exception("Deeplink not implemented")\n')
            f.write("\t\t}\n\n")
            f.write("\t\treturn super.fetchSearchManga(page, query, filters)\n")
            f.write("\t}\n\n")

            f.write(
                "\toverride fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun mangaDetailsRequest(manga: SManga): Request = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun getMangaUrl(manga: SManga): String = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun getChapterUrl(chapter: SChapter): String = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()\n\n"
            )

            f.write(
                "\toverride fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()\n"
            )

            f.write("}\n")
