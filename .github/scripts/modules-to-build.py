import os
#import requests
import subprocess
import re
import json

# def getLastSuccessfulCommitHash():
#     worflowRuns = requests.get(
#         url = "https://api.github.com/repos/keiyoushi/extensions-source/actions/runs",
#         params = {"event":"push", "status":"success"},
#     ).json()["workflow_runs"]

#     for run in worflowRuns:
#         if run["name"] == "CI":
#             return run["head_commit"]["id"]

#     return None

extRegex = re.compile(r"(^src/\w+/\w+)")
multisrcRegex = re.compile(r"^lib-multisrc/(\w+)")

def getModuleList(commitHash):
    fileList = subprocess.check_output(
        [
            "git",
            "diff",
            "--name-only",
            commitHash,
        ]
    ).decode()
    srcModules = set()
    deletedModules = set()

    multisrcToCheck = set()

    for file in fileList.splitlines():
        extMatch = extRegex.search(file)
        if extMatch:
            directory = extMatch.group(1)
            if os.path.isdir(directory):
                srcModules.add(f':{directory.replace("/", ":")}:assembleRelease')
            else:
                name = directory.split("src/", 1)[1].replace("/", ".")
                deletedModules.add(name)
        else:
            multisrcMatch = multisrcRegex.search(file)
            if (multisrcMatch):
                multisrc = multisrcMatch.group(1)
                if os.path.isdir(f"lib-multisrc/{multisrc}"):
                    multisrcToCheck.add(multisrc)

    if len(multisrcToCheck) != 0:
        themePkgRegex = r"themePkg\s*=\s*['\"](" + "|".join(multisrcToCheck) + r")['\"]"

        for lang in os.listdir("src"):
            for module in os.listdir(f"src/{lang}/"):
                buildGradle = f"src/{lang}/{module}/build.gradle"
                if os.path.isfile(buildGradle):
                    with open(buildGradle, 'r') as file:
                        data = file.read()
                    if re.search(themePkgRegex, data):
                        srcModules.add(f":src:{lang}:{module}:assembleRelease")

    return list(srcModules), list(deletedModules)

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
