# Niadd Extension – Developer Notes

## Context
The **Niadd extension** was started with the goal of providing **multi-language support**  
(English, Portuguese, Spanish, French, Italian, German, and Russian), following the `All` pattern.

During testing, it was found that only the **English subdomain (`www.niadd.com`)** could partially function inside Tachiyomi/Aniyomi. Non-English subdomains depend entirely on JavaScript to load content dynamically.

---

## Issues Found

1. **No search results in non-EN subdomains**
   - Subdomains like `br.niadd.com`, `es.niadd.com`, etc., return no results.
   - Tachiyomi shows the source as installed, but no manga list appears when searching.

2. **Root cause**
   - The **initial HTML** for non-EN subdomains is mostly empty.
   - All content is dynamically loaded via **JavaScript**.
   - Tachiyomi **does not execute JS**, it only parses static HTML.
   - Result: non-EN manga lists **cannot be fetched**.  
   - Even using headers or tricks does **not change this**, as the page content is only generated in the browser.

3. **Difference in EN**
   - English subdomain provides fully-rendered HTML (no JS dependency).
   - However, **chapter pages often fail to load** due to **Cloudflare protection, ads, or redirects**.
   - EN is **partially functional**, but cannot reliably fetch chapters without extra handling.

4. **NineAnime Update**
   - Chapters are now being redirected to a new website: [https://www.nineanime.com/](https://www.nineanime.com/).  
   - Example of a loaded chapter page:  
     `https://www.nineanime.com/chapter/The_God_Of_High_School_Chapter_572/8457139/`  
   - Some pages redirect to unrelated content (likely advertisements):  
     `https://porcupinepress.com/how-to-build-a-six-figure-freelance-business-in-one-year/3274640.html`  
   - Implication: the current Niadd extension **cannot parse these pages**; a **new extension** would be required for `nineanime.com`.

---

## Current Status (Visual Overview)

| Language | Manga List | Chapters | Notes |
|----------|------------|----------|-------|
| EN       | ✅ Works  | ⚠️ Partially broken | Chapters may fail due to Cloudflare, redirects, or ads. |
| PT-BR    | ❌ Broken | ❌ Broken | JS-dependent, no static HTML. |
| ES       | ❌ Broken | ❌ Broken | JS-dependent, no static HTML. |
| FR       | ❌ Broken | ❌ Broken | JS-dependent, no static HTML. |
| IT       | ❌ Broken | ❌ Broken | JS-dependent, no static HTML. |
| DE       | ❌ Broken | ❌ Broken | JS-dependent, no static HTML. |
| RU       | ❌ Broken | ❌ Broken | JS-dependent, no static HTML. |

- ✅ = Fully functional  
- ⚠️ = Partially functional / unreliable  
- ❌ = Broken / does not work

---

## Possible Solutions

- **Keep EN only**  
  - Currently the only subdomain that can partially function.  

- **Investigate JSON endpoints**  
  - Chapters and manga data are likely fetched via AJAX calls.  
  - If discovered, these endpoints could be queried directly, bypassing JS.

- **Mobile / fallback version**  
  - Check if Niadd provides a mobile/light version with complete HTML content.  

- **For nineanime.com**
  - Consider creating a **new, separate extension**.  
  - Analyze redirects, chapter URLs, and page structures to properly parse chapters.  

---

## Recommendations for Future Devs

- **EN support**
  - Accept that chapters may fail due to **Cloudflare, redirects, or ads**.  
  - Explore ways to bypass or handle these issues (e.g., proxy requests, Cloudflare bypass, or endpoint extraction).

- **Non-EN support**
  - Reverse-engineer the **JavaScript loading process**.  
  - Locate JSON endpoints and parse data directly.  
  - Document all findings carefully.

- **New NineAnime site**
  - Track redirects and inconsistent pages.  
  - Create a separate extension rather than modifying Niadd EN.  

- **Documentation**
  - Always record discovered endpoints, payloads, and page structures.  
  - Ensure clarity about **what works and what doesn’t**.  
  - Transparency is key for future maintenance.

---

**Author:** Zev Lonewolf  
**Last update:** August/2025
