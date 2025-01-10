import itertools
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import NoReturn

EXTENSION_REGEX = re.compile(r"^src/(?P<lang>\w+)/(?P<extension>\w+)")
MULTISRC_LIB_REGEX = re.compile(r"^lib-multisrc/(?P<multisrc>\w+)")
LIB_REGEX = re.compile(r"^lib/(?P<lib>\w+)")

def run_command(command: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True, shell=True)
    if result.returncode != 0:
        print(result.stderr.strip())
        sys.exit(result.returncode)
    return result.stdout.strip()

def get_module_list(ref: str) -> tuple[list[str], list[str]]:
    changed_files = run_command(f"git diff --name-only {ref}").splitlines()

    modules = set()
    libs = set()
    deleted = set()

    for file in map(lambda x: Path(x).as_posix(), changed_files):
        if match := EXTENSION_REGEX.search(file):
            lang = match.group("lang")
            extension = match.group("extension")
            if Path("src", lang, extension).is_dir():
                modules.add(f':src:{lang}:{extension}')
            deleted.add(f"{lang}.{extension}")
        elif match := MULTISRC_LIB_REGEX.search(file):
            multisrc = match.group("multisrc")
            if Path("lib-multisrc", multisrc).is_dir():
                libs.add(f":lib-multisrc:{multisrc}:getDependents")
        elif match := LIB_REGEX.search(file):
            lib = match.group("lib")
            if Path("lib", lib).is_dir():
                libs.add(f":lib:{lib}:getDependents")

    modules.update(run_command("./gradlew -q " + " ".join(libs)).splitlines())

    return list(modules), list(deleted)

def main() -> NoReturn:
    _, ref, build_type = sys.argv
    modules, deleted = get_module_list(ref)
    modules = [f"{module}:assemble{build_type}" for module in modules]
    chunked = {
        "chunk": [
            {"num": i, "modules": modules}
            for i, modules in
            enumerate(itertools.batched(modules, int(os.getenv("CI_CHUNK_SIZE", 65))))
        ]
    }

    print(f"Module chunks to build:\n{json.dumps(chunked, indent=2)}\n\nModule to delete:\n{json.dumps(deleted, indent=2)}")

    if os.getenv("CI") == "true":
        with open(os.getenv("GITHUB_OUTPUT"), 'a') as out_file:
            out_file.write(f"matrix={json.dumps(chunked)}\n")
            out_file.write(f"delete={json.dumps(deleted)}\n")

if __name__ == '__main__':
    main()
