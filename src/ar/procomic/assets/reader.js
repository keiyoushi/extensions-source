// Reads the reader's page list once it has laid out every page: one image, or a grid of pieces positioned in
// percent of the page. The reader lays out pages as they come into view, so the page is zoomed out to bring
// them all into view at once.
(() => {
  if (/\b5\d\d:/.test(document.title)) return { error: true };
  document.documentElement.style.zoom = '0.01';
  const page = 'img[src*="/chapters/"], img[src*=".procomic.im/i/"]';
  const first = document.querySelector(page);
  if (!first) {
    window.scrollBy(0, innerHeight);
    return {};
  }
  // The page list is the closest list holding every page image.
  const images = [...document.querySelectorAll(page)];
  let list = first.closest('.flex-col');
  while (list && images.some((image) => !list.contains(image))) list = list.parentElement.closest('.flex-col');
  if (!list) return {};
  const pages = [];
  for (const slot of list.children) {
    const image = slot.querySelector('img[src*="/chapters/"]');
    if (image) {
      pages.push({ url: image.src });
      continue;
    }
    const pieces = [...slot.querySelectorAll('img[src*=".procomic.im/i/"]')];
    if (!pieces.length) {
      slot.scrollIntoView();
      return { laidOut: pages.length };
    }
    pages.push({
      pieces: pieces.map((piece) => ({
        url: piece.src,
        left: parseFloat(piece.style.left) / 100,
        top: parseFloat(piece.style.top) / 100,
        width: parseFloat(piece.style.width) / 100,
        height: parseFloat(piece.style.height) / 100,
      })),
    });
  }
  return { origin: location.origin, pages };
})()
