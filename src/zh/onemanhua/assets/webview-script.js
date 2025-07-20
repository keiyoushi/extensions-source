function waitForElm(selector) {
    return new Promise(resolve => {
        if (document.querySelector(selector)) {
            return resolve(document.querySelector(selector))
        }
        const observer = new MutationObserver(() => {
            if (document.querySelector(selector)) {
                observer.disconnect()
                resolve(document.querySelector(selector))
            }
        })
        observer.observe(document.body, {
            childList: true,
            subtree: true
        })
    })
}
function loadPic(pageIndex) {
    const page = pageIndex + 1
    const target = document.querySelector(`div.mh_comicpic[p="${page}"] img[src]`)
    const mh_loading = document.querySelector(`div.mh_comicpic[p="${page}"] .mh_loading`)
    const mh_loaderr = document.querySelector(`div.mh_comicpic[p="${page}"] .mh_loaderr`)
    if (target) {
        target.scrollIntoView()
    } else if (mh_loading?.style.display != 'none') {
        mh_loading.scrollIntoView()
    } else if (mh_loaderr?.style.display != 'none') {
        mh_loaderr.scrollIntoView()
        const button = mh_loaderr.querySelector('.mh_btn')
        if (button) {
            __cr.reloadPic(button, page)
        }
    }
}
window.__ad = () => { }
let pageCount = 0
waitForElm("#mangalist").then(() => {
    pageCount = parseInt($.cookie(__cad.getCookieValue()[1] + mh_info.pageid) || "0")
    window.__interface__.setPageCount(pageCount)
})
const observer = new MutationObserver(() => {
    if (document.querySelector("div.mh_comicpic img")) {
        const images = document.querySelectorAll("div.mh_comicpic img")
        images.forEach(img => {
            if (!img._Hijacked) {
                const originalSet = Object.getOwnPropertyDescriptor(img['__proto__'], 'src')
                img._src = ''
                Object.defineProperty(img, 'src', {
                    enumerable: originalSet.enumerable,
                    get: function () {
                        return this._src
                    },
                    set: function (value) {
                        fetch(value).then(response => {
                            return response.blob()
                        }).then(blob => {
                            const reader = new FileReader()
                            reader.onloadend = () => { window.__interface__.setPage(this.parentElement.getAttribute('p') - 1, reader.result) }
                            reader.readAsDataURL(blob)
                        })
                        this._src = value
                        originalSet.set.call(this, this._src)
                    }
                })
                img._Hijacked = true
            }
        })
        if (images.length >= pageCount) {
            observer.disconnect()
        }
    }
})
observer.observe(document.body, { subtree: true, childList: true })
