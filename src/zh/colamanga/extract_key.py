import urllib.request
import json
import re

custom_url = "https://www.colamanga.com/js/custom.js"
manga_read_url = "https://www.colamanga.com/js/manga.read.js"

def fetch(url):
    response = urllib.request.urlopen(url)
    data = response.read()
    data = data.decode("utf-8")
    return data

def extract_keymap():
    regex = re.compile(r"""if \(G == \"(\d+)\"\W+I = \"(.+?)";""")

    with open("manga.read.js", "r", encoding="utf8") as f:
        data = f.read()

    matches = regex.findall(data)

    key_map = {}
    for match in matches:
        key_map[match[0]] = match[1]
    
    print(len(key_map))
    with open("keymap.json", "w", encoding="utf8") as f:
        json.dump(key_map, f, ensure_ascii=False, indent=4)


extract_keymap()
