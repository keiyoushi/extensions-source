import os
import subprocess as sp
import re
import json
import sys
from pathlib import Path
from typing import List, Tuple, Dict, Any

def get_module_list(commitHash: str) -> Tuple[List[str], List[str]]:
    file_list = sp.run(
        ["git", "diff", "--name-only", commitHash],
        check=True,
        capture_output=True,
        text=True
    ).stdout
            
    modules = set()
    libs = set()
    deleted = set()
    
    ext_regex = re.compile(r"^src/(?P<lang>\w+)/(?P<ext_name>\w+)")
    multisrc_regex = re.compile(r"^lib-multisrc/(?P<multisrc>\w+)")
    libs_regex = re.compile(r"^lib/(?P<lib>\w+)")

    for _file in file_list.splitlines():
        file = Path(_file).as_posix()
                
        ext_match = ext_regex.search(file)
        multisrc_match = multisrc_regex.search(file)
        lib_match = libs_regex.search(file)
        
        if ext_match:
            lang = ext_match.group("lang")
            ext_name = ext_match.group("ext_name")
            if Path("src", lang, ext_name).is_dir():
                modules.add(f':src:{lang}:{ext_name}:assembleRelease')
            deleted.add(f"{lang}.{ext_name}") # need to add here so we can delete old version during commit stage
        elif multisrc_match:
            multisrc = multisrc_match.group("multisrc")
            if Path("lib-multisrc", multisrc).is_dir():
                libs.add(f":lib-multisrc:{multisrc}:getDependents")
        elif lib_match:
            lib = lib_match.group("lib")
            if Path("lib", lib).is_dir():
                libs.add(f":lib:{lib}:getDependents")
        
    result = sp.run(
        ["./gradlew", "-q"] + list(libs),
        check=True,
        capture_output=True,
        text=True
    ).stdout
    
    modules.update([f"{i}:assembleRelease" for i in result.splitlines()])
                
    return list(modules), list(deleted)

def chunker(iterable: List[str], size: int) -> Dict[str, Any]:
    if size < 1:
        raise ValueError('Chunk size must be greater than 0.')
    return {"chunk": [{"num": i, "modules": iterable[i:i + size]} for i in range(0, len(iterable), size)]}

commit = sys.argv[1]
modules, deleted = get_module_list(commit)
chunked = chunker(modules, int(os.getenv("CI_CHUNK_SIZE", 65)))

print(f"Module chunks to build:\n{json.dumps(chunked, indent=2)}\n\nModule to delete:\n{json.dumps(deleted, indent=2)}")

if os.getenv("CI") == "true":
    with open(os.getenv("GITHUB_OUTPUT"), 'a') as out_file:
        out_file.write(f"matrix={json.dumps(chunked)}\n")
        out_file.write(f"delete={json.dumps(deleted)}\n")
