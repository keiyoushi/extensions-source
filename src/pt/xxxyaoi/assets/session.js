(entries) => {
    for (const [key, value] of Object.entries(entries)) {
        if (sessionStorage.getItem(key) === null) sessionStorage.setItem(key, value);
    }
}
