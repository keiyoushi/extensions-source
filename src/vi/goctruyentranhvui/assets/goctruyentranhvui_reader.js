(function() {
    try {
        var isBlocked = false;
        var elems = document.querySelectorAll('p, span, div, h1, h2, h3');
        for (var i = 0; i < elems.length; i++) {
            var el = elems[i];
            if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE') continue;
            var txt = el.innerText || el.textContent || "";
            if (txt.indexOf("Để đọc chương này bạn cần") !== -1 || txt.indexOf("bạn cần đăng nhập") !== -1) {
                isBlocked = true;
                break;
            }
        }

        window.scrollTo(0, document.body.scrollHeight);
        var imgs = document.querySelectorAll('div.image-section .img-block img');
        if (!imgs || imgs.length === 0) {
            imgs = document.querySelectorAll('.img-block img');
        }
        if (!imgs || imgs.length === 0) {
            imgs = document.querySelectorAll('img.image.finished');
        }
        if (!imgs || imgs.length === 0) {
            imgs = document.querySelectorAll('img.image');
        }
        if (!imgs || imgs.length === 0) {
            imgs = document.querySelectorAll('img');
        }

        var urls = [];
        for (var j = 0; j < imgs.length; j++) {
            var img = imgs[j];
            var src = img.src ||
                img.getAttribute('data-src') ||
                img.getAttribute('data-original') ||
                img.getAttribute('data-lazy-src') ||
                img.getAttribute('src');
            if (!src) continue;

            var lowerSrc = src.toLowerCase();
            if (lowerSrc.indexOf('google') !== -1 || lowerSrc.indexOf('logo') !== -1 || lowerSrc.indexOf('avatar') !== -1 || lowerSrc.indexOf('facebook') !== -1) {
                continue;
            }

            if ((src.indexOf('http') === 0 || src.indexOf('/') === 0) && (img.naturalWidth > 100 || img.naturalHeight > 100)) {
                if (urls.indexOf(src) === -1) {
                    urls.push(src);
                }
            }
        }
        return JSON.stringify({ needLogin: isBlocked, urls: urls });
    } catch (e) {
        return JSON.stringify({ needLogin: false, urls: [] });
    }
})()
