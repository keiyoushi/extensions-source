# Niadd Extension – Developer Notes

## Context
The **Niadd extension** was started with the goal of providing **multi-language support**  
(English, Portuguese, Spanish, French, Italian, German, and Russian), following the `All` pattern.

However, during testing, it was found that only the **English subdomain (`www.niadd.com`)**  
works correctly inside Tachiyomi/Aniyomi.

---

## Issues Found

1. **No search results in non-EN subdomains**
   - On subdomains like `br.niadd.com`, `es.niadd.com`, etc., search returns no results.
   - Tachiyomi shows the source as installed, but no manga list appears when searching.

2. **Root cause**
   - The **initial HTML** for these subdomains is just an empty skeleton.
   - All content is loaded dynamically via **JavaScript**.
   - Tachiyomi **does not execute JS**, it only parses static HTML.
   - Result: the extension never receives the manga list on non-EN versions.

3. **Difference in EN**
   - The English subdomain provides fully-rendered HTML (no JS dependency).
   - Therefore, only EN works properly.

---

## Possible Solutions

- **Keep EN only**  
  Currently the only domain that works 100% with Tachiyomi.

- **Investigate JSON endpoints**  
  The site likely loads its data via AJAX calls.  
  If discovered, those endpoints could be queried directly from the extension.

- **Mobile / fallback version**  
  Check if Niadd has a mobile/light version that serves complete HTML.  
  If available, it might be usable for non-EN subdomains.

---

## Current Status

- `Niadd (EN)` – **Working**
- `Niadd (PT-BR, ES, FR, IT, DE, RU)` – **Broken (JS-dependent)**

---

## Recommendations for Future Devs

- Prioritize **EN support**, since it’s stable.  
- For adding non-EN support:
  - Reverse-engineer the **JavaScript loading process** of those pages.
  - Find the JSON endpoints used by the site.
  - Replace HTML scraping with direct endpoint calls.
- Document any discovered endpoints and payloads to ease future maintenance.

---

**Author:** Zev Lonewolf  
**Last update:** August/2025
