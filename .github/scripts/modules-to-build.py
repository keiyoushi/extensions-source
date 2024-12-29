import os
import subprocess as sp
import re
import json
import git

extRegex = re.compile(r"(^src/\w+/\w+)")
multisrcRegex = re.compile(r"^lib-multisrc/(\w+)")
libsRegex = re.compile(r"^lib/(\w+)")

def getModuleList(commitHash):
    repo = git.Repo(search_parent_directories=True)
    fileList = repo.git.diff('--name-only', commitHash)
            
    modules = set()
    deletedModules = set()

    for file in fileList.splitlines():
        extMatch = extRegex.search(file)
        multisrcMatch = multisrcRegex.search(file)
        libMatch = libsRegex.search(file)
        if extMatch:
            directory = extMatch.group(1)
            if os.path.isdir(directory):
                modules.add(f':{directory.replace("/", ":")}:assembleRelease')
            else:
                name = directory.split("src/", 1)[1].replace("/", ".")
                deletedModules.add(name)
        elif multisrcMatch:
            multisrc = multisrcMatch.group(1)
            if os.path.isdir(f"lib-multisrc/{multisrc}"):
                modules.add(f":lib-multisrc:{multisrc}:assembleReleaseAll")
        elif libMatch:
            lib = libMatch.group(1)
            if os.path.isdir(f"lib/{lib}"):
                modules.add(f":lib:{lib}:assembleRelease")
                
    return list(modules), list(deletedModules)

def chunker(iter, size):
    chunks = []
    if size < 1:
        raise ValueError('Chunk size must be greater than 0.')
    num=0
    for i in range(0, len(iter), size):
        chunks.append({"num":num, "modules":iter[i:(i+size)]})
        num+=1
    return {"chunk":chunks}

#commit = getLastSuccessfulCommitHash()
commit = "c9dd5e2a5ca4bb8731fa3afe34862d91772e016a"
modules, deleted = getModuleList(commit)
chunked = chunker(modules, int(os.getenv("CI_CHUNK_SIZE", 65)))

print(f"Module chunks to build:\n{json.dumps(chunked, indent=2)}\n\nModule to delete:\n{json.dumps(deleted, indent=2)}")

if (os.getenv("CI") == "true"):
    with open(os.getenv("GITHUB_OUTPUT"), 'a') as outFile:
        outFile.write(f"individualMatrix={json.dumps(chunked)}\n")
        outFile.write(f"deletedModules={json.dumps(deleted)}")
