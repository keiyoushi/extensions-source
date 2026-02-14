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
            
            with open(build_file) as f:
                content = f.read()
                
            if lib_dependency.search(content):
                all_dependent_libs.add(lib.name)
                to_process.add(lib.name)
    
    return all_dependent_libs


def resolve_multisrc_lib(libs: set[str]) -> set[str]:
    """
    returns all multisrc which depend on any of the
    passed libs (/lib)
    """
    
    lib_dependency = re.compile(
        rf"project\([\"']:(?:lib):({'|'.join(map(re.escape, libs))})[\"']\)"
    )
    
    multisrcs = set()
    
    for multisrc in Path("lib-multisrc").iterdir():
        build_file = multisrc / "build.gradle.kts"
        if not build_file.is_file():
            continue
        
        with open(build_file) as f:
            content = f.read()
            
        if (lib_dependency.search(content)):
            multisrcs.add(multisrc.name)
                
    return multisrcs
            
def resolve_ext(multisrcs: set[str], libs: set[str]) -> set[tuple[str, str]]:
    """
    returns all extensions which depend on any of the
    passed multisrcs or libs
    """
    
    multisrc_dependency = re.compile(
        rf"themePkg\s*=\s*['\"]({'|'.join(map(re.escape, multisrcs))})['\"]"
    )
    
    lib_dependency = re.compile(
        rf"project\([\"']:(?:lib):({'|'.join(map(re.escape, libs))})[\"']\)"
    )
    
    extensions = set()
    
    for lang in Path("src").iterdir():
        for extension in lang.iterdir():
            build_file = extension / "build.gradle"
            if not build_file.is_file():
                continue
            
            with open(build_file) as f:
                content = f.read()
                
            if (multisrc_dependency.search(content) or lib_dependency.search(content)):
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
            break
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
        return get_all_modules()
    
    # Resolve libs that depend on the changed libs (recursively)
    dependent_libs = resolve_dependent_libs(libs)
    libs.update(dependent_libs)
                
    multisrcs.update(resolve_multisrc_lib(libs))
    
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
