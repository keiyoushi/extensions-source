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
let hasImgReady = false
let scrolledOnce = false
let scrolling = false
async function scroll() {
    if (scrolling || !hasImgReady) return
    scrolling = true
    window.scrollTo(0, 0)
    await new Promise((resolve, reject) => {
        try {
            const maxScroll = Number.MAX_SAFE_INTEGER
            let lastScroll = 0
            const interval = setInterval(() => {
                window.scrollBy(0, 100)
                const scrollTop = document.documentElement.scrollTop
                if (scrollTop === maxScroll || scrollTop === lastScroll) {
                    clearInterval(interval)
                    scrolling = false
                    scrolledOnce = true
                    resolve()
                } else {
                    lastScroll = scrollTop
                }
            }, 100)
        } catch (err) {
            reject(err.toString())
        }
    })
}
function loadPic(pageIndex) {
    if (scrolling || !hasImgReady) return
    if (!scrolledOnce) {
        scroll()
        return
    }
    document.querySelector("#mangalist").dispatchEvent(new CustomEvent('scroll'))
    const page = pageIndex + 1
    const target = document.querySelector(`div.mh_comicpic[p="${page}"] img[src]`)
    const mh_loaderr = document.querySelector(`div.mh_comicpic[p="${page}"] .mh_loaderr`)
    if (target) {
        target.scrollIntoView()
    } else if (mh_loaderr?.style.display != 'none') {
        mh_loaderr.scrollIntoView()
        mh_loaderr.querySelector('.mh_btn')?.click()
    } else {
        scroll()
    }
}
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
                const originalSrc = Object.getOwnPropertyDescriptor(img.__proto__, "src")
                Object.defineProperty(img, "src", {
                    ...originalSrc,
                    set: function (value) {
                        fetch(value).then(response => {
                            return response.blob()
                        }).then(blob => {
                            const reader = new FileReader()
                            reader.onloadend = () => {
                                window.__interface__.setPage(this.parentElement.getAttribute('p') - 1, reader.result)
                                hasImgReady = true
                            }
                            reader.readAsDataURL(blob)
                        })
                        originalSrc.set.call(this, value)
                    }
                })
                img._Hijacked = true
            }
        })
        if (pageCount>0 && images.length >= pageCount) {
            observer.disconnect()
        }
    }
})
observer.observe(document.body, { subtree: true, childList: true })
