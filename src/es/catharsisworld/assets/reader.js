// [pages]: the selector of the page elements; [text]: the selector of the text reader, for chapters that are text only;
// [hd]: whether the site's reader draws its pages in HD
(pages, text, hd) => {
    const quality = 'catharsis_reader_quality';
    if (hd) localStorage.setItem(quality, 'hd');
    else if (localStorage.getItem(quality) === 'hd') localStorage.removeItem(quality);

    const sent = new Set();
    const prints = new Map();

    addEventListener(
        'click',
        (event) => {
            const target = event.target;
            if (!document.querySelector(pages) || !(target instanceof Element) || target.closest(pages)) return;
            for (let node = target; node && node !== document.documentElement; node = node.parentElement) {
                if (getComputedStyle(node).position === 'fixed') return;
            }
            event.preventDefault();
            event.stopImmediatePropagation();
        },
        true,
    );

    // A sample of a canvas's pixels across its height, or null while any sampled spot is still empty
    const fingerprint = (canvas) => {
        const context = canvas.getContext('2d', { willReadFrequently: true });
        let hash = 7;
        for (const f of [0.02, 0.25, 0.5, 0.75, 0.98]) {
            const y = Math.min(canvas.height - 1, Math.floor(canvas.height * f));
            const row = context.getImageData(0, y, canvas.width, 1).data;
            for (let x = 0; x < canvas.width; x += 37) {
                if (row[x * 4 + 3] === 0) return null;
                hash = (hash * 31 + row[x * 4] * 65536 + row[x * 4 + 1] * 256 + row[x * 4 + 2]) | 0;
            }
        }
        return hash;
    };

    const point = (el, box) => {
        const x = box.left + box.width / 2;
        const y = box.top + box.height / 2;
        return { x, y, clear: y > 0 && y < innerHeight && el.contains(document.elementFromPoint(x, y)) };
    };

    return () => {
        const base = { scale: devicePixelRatio, width: innerWidth, height: innerHeight };
        const visible = (el) => el && el.getClientRects().length > 0;
        const ageCheck = [...document.querySelectorAll('input[type=checkbox]')].find((box) => visible(box) && box.closest('.fixed'));
        if (ageCheck) {
            const target = ageCheck.checked
                ? [...ageCheck.closest('.fixed').querySelectorAll('button')].find(
                      (button) => visible(button) && button.innerText.trim().length > 2,
                  )
                : (ageCheck.closest('div') ?? ageCheck);
            if (target) return { ...base, gate: point(target, target.getBoundingClientRect()) };
        }
        const items = [...document.querySelectorAll(pages)];
        if (!items.length) {
            if (document.querySelector('cap-widget')) return { ...base, check: true };
            if (text && document.querySelector(text)) return { ...base, textOnly: true };
            const turnstile = document.querySelector('input[name="cf-turnstile-response"]');
            if (turnstile && !turnstile.value) return { ...base, turnstile: true };
            return { ...base, pages: [], text: document.body.innerText.slice(0, 500) };
        }
        let image = null;
        let bottom = 0;
        const found = items.map((item, index) => {
            const canvas = item.matches('canvas') ? item : item.querySelector('canvas');
            // A page still loading may have no box of its own: it sits where the previous one ends
            let box = canvas?.getBoundingClientRect();
            if (!box || box.height === 0) box = item.getBoundingClientRect();
            const top = box.height > 0 ? box.top : bottom;
            bottom = box.height > 0 ? box.bottom : bottom + 1;
            let ready = false;
            if (canvas && canvas.width > 0 && canvas.height > 0) {
                const print = fingerprint(canvas);
                // Finished once two reads in a row see the same pixels
                ready = print !== null && prints.get(index) === print;
                prints.set(index, print);
            }
            if (ready && !sent.has(index) && image === null) {
                // An encoding that fails gives "data:,"
                const data = canvas.toDataURL('image/jpeg', 0.92);
                if (data.startsWith('data:image/')) {
                    sent.add(index);
                    image = { index, data };
                }
            }
            const button = item.querySelector('button');
            const retry = button?.getBoundingClientRect();
            return {
                index,
                top,
                height: box.height,
                ready,
                retry: retry && retry.height > 0 ? point(button, retry) : null,
            };
        });
        let scroller = items[0].parentElement;
        while (
            scroller &&
            scroller !== document.body &&
            !(scroller.scrollHeight > scroller.clientHeight && /auto|scroll/.test(getComputedStyle(scroller).overflowY))
        ) {
            scroller = scroller.parentElement;
        }
        const area = scroller && scroller !== document.body ? scroller : document.documentElement;
        const floating = (el) => {
            for (let node = el; node && node !== area; node = node.parentElement) {
                if (node.tagName === 'IFRAME' || getComputedStyle(node).position === 'fixed') return true;
            }
            return false;
        };
        let grip = null;
        for (const fy of [0.75, 0.6, 0.85, 0.45, 0.3]) {
            for (const fx of [0.5, 0.3, 0.7, 0.15, 0.85]) {
                const hit = document.elementFromPoint(innerWidth * fx, innerHeight * fy);
                if (!grip && hit && area.contains(hit) && !floating(hit)) grip = { x: innerWidth * fx, y: innerHeight * fy };
            }
        }
        return { ...base, pages: found, image, grip };
    };
}
