// The series page once the site has rendered it.
() => (document.readyState === 'complete' && document.getElementById('series-title') ? { html: document.documentElement.outerHTML } : null)
