let pageCount = 0
const ImgToBlobBase64 = async (i, e) => {
    (await fetch(e.src)).blob().then(blob => {
        const reader = new FileReader()
        reader.onloadend = () => window.__interface__.setPage(i, reader.result)
        reader.readAsDataURL(blob)
    })
}
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
let currentPageIndex = 0
let scrollQueue = Promise.resolve()
function scrollIntoPage(targetPageIndex) {
    scrollQueue = scrollQueue.then(() => {
        return new Promise(resolve => {
            if (targetPageIndex !== currentPageIndex) {
                const step = targetPageIndex > currentPageIndex ? 1 : -1
                let delay = 0
                for (let i = currentPageIndex; i !== targetPageIndex; i += step) {
                    delay += 1000
                    setTimeout(() => {
                        const target = document.querySelector(`div.mh_comicpic[p="${i + step + 1}"] img`)
                        target?.scrollIntoView()
                        const loading = document.querySelector(`div.mh_comicpic[p="${i + step + 1}"] .mh_loading`)
                        if (loading && loading.style.display !== 'none') {
                            loading.scrollIntoView()
                        }
                        if (i + step === targetPageIndex) {
                            currentPageIndex = targetPageIndex
                            resolve()
                        }
                    }, delay)
                }
            }
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
        const button = mh_loaderr.querySelector('.mh_btn')
        if (button) {
            __cr.reloadPic(button, page)
        }
    } else { // fallback
        scrollIntoPage(pageIndex)
    }
}
window.__ad = () => { }
waitForElm("#mangalist").then((elm) => {
    pageCount = parseInt($.cookie(__cad.getCookieValue()[1] + mh_info.pageid) || "0")
    window.__interface__.setPageCount(pageCount)
    const observer = new MutationObserver((mutationRecord) => {
        mutationRecord.forEach(m => {
            if (m.type === 'attributes' && m.target.tagName === 'IMG' && m.attributeName === 'src') {
                ImgToBlobBase64(m.target.parentElement.getAttribute('p') - 1, m.target)
                m.target.scrollIntoView()
            }
        })
    })
    observer.observe(elm, { attributeFilter: ['src'], subtree: true })
})
