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
MODULE_REGEX = re.compile(r"^:src:(?P<lang>\w+):(?P<extension>\w+)$")
CORE_FILES_REGEX = re.compile(
    r"^(buildSrc/|core/|gradle/|build\.gradle\.kts|common\.gradle|gradle\.properties|settings\.gradle\.kts|.github/scripts)"
)

def run_command(command: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True, shell=True)
    if result.returncode != 0:
        print(result.stderr.strip())
        sys.exit(result.returncode)
    return result.stdout.strip()


def resolve_dependent_libs(libs: set[str]) -> set[str]:
    """
    returns all libs which depend on any of the passed libs (/lib),
    recursively resolving transitive dependencies
    """
    if not libs:
        return set()
    
    all_dependent_libs = set()
    to_process = set(libs)
    
    while to_process:
        current_libs = to_process
        to_process = set()
        
        lib_dependency = re.compile(
            rf"project\([\"']:(?:lib):({'|'.join(map(re.escape, current_libs))})[\"']\)"
        )
        
        for lib in Path("lib").iterdir():
            if lib.name in all_dependent_libs or lib.name in libs:
                continue
                
            build_file = lib / "build.gradle.kts"
            if not build_file.is_file():
                continue
            
            content = build_file.read_text("utf-8")
                
            if lib_dependency.search(content):
                all_dependent_libs.add(lib.name)
                to_process.add(lib.name)
    
    return all_dependent_libs


def resolve_multisrc_lib(libs: set[str]) -> set[str]:
    """
    returns all multisrc which depend on any of the
    passed libs (/lib)
    """
    if not libs:
        return set()
    
    lib_dependency = re.compile(
        rf"project\([\"']:(?:lib):({'|'.join(map(re.escape, libs))})[\"']\)"
    )
    
    multisrcs = set()
    
    for multisrc in Path("lib-multisrc").iterdir():
        build_file = multisrc / "build.gradle.kts"
        if not build_file.is_file():
            continue
        
        content = build_file.read_text("utf-8")
            
        if (lib_dependency.search(content)):
            multisrcs.add(multisrc.name)
                
    return multisrcs
            
def resolve_ext(multisrcs: set[str], libs: set[str]) -> set[tuple[str, str]]:
    """
    returns all extensions which depend on any of the
    passed multisrcs or libs
    """
    if not multisrcs and not libs:
        return set()
    
    multisrc_pattern = '|'.join(map(re.escape, multisrcs)) if multisrcs else None
    lib_pattern = '|'.join(map(re.escape, libs)) if libs else None
    
    patterns = []
    if multisrc_pattern:
        patterns.append(rf"themePkg\s*=\s*['\"]({multisrc_pattern})['\"]")
    if lib_pattern:
        patterns.append(rf"project\([\"']:(?:lib):({lib_pattern})[\"']\)")
    
    regex = re.compile('|'.join(patterns))
    
    extensions = set()
    
    for lang in Path("src").iterdir():
        for extension in lang.iterdir():
            build_file = extension / "build.gradle"
            if not build_file.is_file():
                continue
            
            content = build_file.read_text("utf-8")
                
            if regex.search(content):
                extensions.add((lang.name, extension.name))
    
    return extensions

def get_module_list(ref: str) -> tuple[list[str], list[str]]:
    diff_output = run_command(f"git diff --name-status {ref}").splitlines()
    
    changed_files = [
        file
        for line in diff_output
        for file in line.split("\t", 2)[1:]
    ]
        
    modules = set()
    multisrcs = set()
    libs = set()
    deleted = set()
    core_files_changed = False

    for file in map(lambda x: Path(x).as_posix(), changed_files):
        if CORE_FILES_REGEX.search(file):
            core_files_changed = True
        
        elif match := EXTENSION_REGEX.search(file):
            lang = match.group("lang")
            extension = match.group("extension")
            if Path("src", lang, extension).is_dir():
                modules.add(f':src:{lang}:{extension}')
            deleted.add(f"{lang}.{extension}")
            
        elif match := MULTISRC_LIB_REGEX.search(file):
            multisrc = match.group("multisrc")
            if Path("lib-multisrc", multisrc).is_dir():
                multisrcs.add(multisrc)
                
        elif match := LIB_REGEX.search(file):
            lib = match.group("lib")
            if Path("lib", lib).is_dir():
                libs.add(lib)
              
    if core_files_changed:
        (all_modules, all_deleted) = get_all_modules()

        # update existing set so we include deleted extensions
        modules.update(all_modules)
        deleted.update(all_deleted)
        
        return list(modules), list(deleted)
    
    # Resolve libs that depend on the changed libs (recursively)
    libs.update(
        resolve_dependent_libs(libs)
    )
                
    # Resolve multisrcs that depend on the changed libs
    multisrcs.update(
        resolve_multisrc_lib(libs)
    )
    
    # Resolve extensions that depend on the changed multisrcs or libs
    extensions = resolve_ext(multisrcs, libs)
    modules.update([f":src:{lang}:{extension}" for lang, extension in extensions])
    deleted.update([f"{lang}.{extension}" for lang, extension in extensions])

    if os.getenv("IS_PR_CHECK") != "true":
        with Path(".github/always_build.json").open() as always_build_file:
            always_build = json.load(always_build_file)
        for extension in always_build:
            modules.add(":src:" + extension.replace(".", ":"))
            deleted.add(extension)

    return list(modules), list(deleted)

def get_all_modules() -> tuple[list[str], list[str]]:
    modules = []
    deleted = []
    for lang in Path("src").iterdir():
        for extension in lang.iterdir():
            modules.append(f":src:{lang.name}:{extension.name}")
            deleted.append(f"{lang.name}.{extension.name}")
    return modules, deleted

def main() -> NoReturn:
    _, ref, build_type = sys.argv
    modules, deleted = get_module_list(ref)

    chunked = {
        "chunk": [
            {"number": i + 1, "modules": modules}
            for i, modules in
            enumerate(itertools.batched(
                map(lambda x: f"{x}:assemble{build_type}", modules),
                int(os.getenv("CI_CHUNK_SIZE", 65))
            ))
        ]
    }

    print(f"Module chunks to build:\n{json.dumps(chunked, indent=2)}\n\nModule to delete:\n{json.dumps(deleted, indent=2)}")

    if os.getenv("CI") == "true":
        with open(os.getenv("GITHUB_OUTPUT"), 'a') as out_file:
            out_file.write(f"matrix={json.dumps(chunked)}\n")
            out_file.write(f"delete={json.dumps(deleted)}\n")

if __name__ == '__main__':
    main()
