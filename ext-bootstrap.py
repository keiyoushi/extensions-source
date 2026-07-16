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
    args.is_keisource = True
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

        if (
            'libVersion = "1.4"'
            in (multisrc_dir / multisrc_theme / "build.gradle.kts").read_text()
        ):
            args.is_keisource = False


def get_ext_classname(extname: str) -> str:
    sanitized = re.sub(r"[^A-Za-z0-9 ]", "", extname)
    return "".join(word.capitalize() for word in re.findall(r"[A-Za-z0-9]+", sanitized))


def write_gradle_file(args: argparse.Namespace, ext_dir: Path) -> None:
    with open(ext_dir / "build.gradle.kts", "w") as f:
        f.write("import io.github.keiyoushi.gradle.api.ContentWarning\n\n")
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
        f.write("\tlibVersion = ")
        f.write('"1.6"' if args.is_keisource else '"1.4"')
        f.write("\n\n")

        f.write("\tsource {\n")
        if args.source_name:
            f.write(f'\t\tname = "{args.source_name}"\n')

        f.write(f'\t\tbaseUrl = "{args.baseurl}"\n')
        f.write(f'\t\tlang = "{args.lang}"\n')
        f.write("\t}\n")

        if not args.multisrc:
            f.write("\n\tdeeplink {\n")
            f.write('\t\tpath("/..*")\n')
            f.write("\t}\n")

        f.write("}\n")


def write_multisrc_source(f, args: argparse.Namespace, classname: str) -> None:
    multisrc_classname = get_ext_classname(args.multisrc)
    f.write(
        f"import eu.kanade.tachiyomi.multisrc.{args.multisrc}.{multisrc_classname}\n"
    )
    f.write("import keiyoushi.annotation.Source\n\n")
    f.write("@Source\n")
    f.write(f"abstract class {classname} : {multisrc_classname}()\n")


def write_keisource_source(f, classname: str) -> None:
    """Extension stub targeting lib 1.6."""
    f.write("import eu.kanade.tachiyomi.source.model.FilterList\n")
    f.write("import eu.kanade.tachiyomi.source.model.MangasPage\n")
    f.write("import eu.kanade.tachiyomi.source.model.Page\n")
    f.write("import eu.kanade.tachiyomi.source.model.SChapter\n")
    f.write("import eu.kanade.tachiyomi.source.model.SManga\n")
    f.write("import eu.kanade.tachiyomi.source.model.SMangaUpdate\n")
    f.write("import keiyoushi.annotation.Source\n")
    f.write("import keiyoushi.source.KeiSource\n")
    f.write("import okhttp3.HttpUrl\n")
    f.write("import java.lang.UnsupportedOperationException\n\n")

    f.write("@Source\n")
    f.write(f"abstract class {classname} : KeiSource() {{\n\n")

    f.write(
        "\toverride suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()\n\n"
    )
    f.write(
        "\toverride suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()\n\n"
    )
    f.write(
        "\toverride suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage =\n"
        "\t\tthrow UnsupportedOperationException()\n\n"
    )
    f.write(
        "\toverride suspend fun getMangaByUrl(url: HttpUrl): SManga? = throw UnsupportedOperationException()\n\n"
    )

    f.write("\toverride suspend fun fetchMangaUpdate(\n")
    f.write("\t\tmanga: SManga,\n")
    f.write("\t\tchapters: List<SChapter>,\n")
    f.write("\t\tfetchDetails: Boolean,\n")
    f.write("\t\tfetchChapters: Boolean,\n")
    f.write("\t): SMangaUpdate = throw UnsupportedOperationException()\n\n")

    f.write(
        "\toverride suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException()\n\n"
    )
    f.write(
        "\toverride fun getMangaUrl(manga: SManga): String = throw UnsupportedOperationException()\n\n"
    )
    f.write(
        "\toverride fun getChapterUrl(chapter: SChapter): String = throw UnsupportedOperationException()\n\n"
    )
    f.write(
        "\toverride suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()\n"
    )
    f.write("}\n")


def write_source_file(
    args: argparse.Namespace,
    ext_package_dir: Path,
    ext_dir_lang: str,
    ext_dir_name: str,
) -> None:
    classname = get_ext_classname(args.extname)

    with open(ext_package_dir / f"{classname}.kt", "w") as f:
        f.write(
            f"package eu.kanade.tachiyomi.extension.{ext_dir_lang}.{ext_dir_name}\n\n"
        )

        if args.multisrc:
            write_multisrc_source(f, args, classname)
        else:
            write_keisource_source(f, classname)


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
        choices=("SAFE", "MIXED", "NSFW"),
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

    write_gradle_file(args, ext_dir)

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

    write_source_file(args, ext_package_dir, ext_dir_lang, ext_dir_name)
    print(f"Created source file: '{ext_package_dir}'")
