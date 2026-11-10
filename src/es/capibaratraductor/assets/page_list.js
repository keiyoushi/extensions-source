// Page array in the reader's React state, null until the reader has decoded it
(function () {
  function isPages(list) {
    return Array.isArray(list) && list.length > 0 && list.every(function (page) {
      return page && typeof page.imageUrl === 'string' && /^https?:/.test(page.imageUrl) && typeof page.number === 'number' && typeof page.chapterId === 'number';
    });
  }
  var pages = null;
  function walk(fiber) {
    for (; fiber; fiber = fiber.sibling) {
      for (var hook = fiber.memoizedState; hook && typeof hook === 'object' && 'next' in hook && 'queue' in hook; hook = hook.next) {
        if (isPages(hook.memoizedState) && (!pages || hook.memoizedState.length > pages.length)) pages = hook.memoizedState;
      }
      if (fiber.child) walk(fiber.child);
    }
  }
  document.querySelectorAll('*').forEach(function (element) {
    Object.keys(element).forEach(function (key) {
      if (key.indexOf('__reactContainer$') === 0) {
        var fiber = element[key];
        walk(fiber.stateNode && fiber.stateNode.current || fiber);
      }
    });
  });
  if (!pages) return null;
  // The reader leaves one placeholder on every page when its own decode fails
  var urls = pages.map(function (page) { return page.imageUrl; });
  return urls.length > 1 && new Set(urls).size === 1 ? null : urls;
})();
