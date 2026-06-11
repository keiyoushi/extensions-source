/* =========================
   HOME
========================= */

/* =========================
   SAFE STATIC GLOBALS
========================= */

const STATIC_JSON_BASE =
  (typeof STATIC_BASE !== "undefined" && STATIC_BASE)
    ? STATIC_BASE
    : (window.STATIC_BASE || "https://static.comicaso.pro/static");

const STATIC_JSON_SOURCES =
  (typeof STATIC_SOURCES !== "undefined" && Array.isArray(STATIC_SOURCES))
    ? STATIC_SOURCES
    : (Array.isArray(window.STATIC_SOURCES) ? window.STATIC_SOURCES : ["comicazen", "medusa"]);

/* =========================
   STATIC JSON HELPERS
========================= */

const staticJsonCache = new Map();

function staticJsonUrl(path) {
  const base = String(STATIC_JSON_BASE || "").replace(/\/+$/, "");
  const cleanPath = String(path || "").replace(/^\/+/, "");

  return `${base}/${cleanPath}`;
}

async function fetchStaticJson(path, { cache = true } = {}) {
  const url = staticJsonUrl(path);

  if (cache && staticJsonCache.has(url)) {
    return staticJsonCache.get(url);
  }

  const res = await fetch(url, {
    cache: "force-cache"
  });

  if (!res.ok) {
    throw new Error(`JSON tidak ditemukan: ${url}`);
  }

  const json = await res.json();

  if (cache) {
    staticJsonCache.set(url, json);
  }

  return json;
}

function normalizeStaticSource(source) {
  const clean = normalizeSourceName(source);

  return clean === "comicazen" || clean === "medusa"
    ? clean
    : "";
}

function getActiveStaticSources(source = state.appSettings?.source || "all") {
  const clean = normalizeStaticSource(source);

  return clean
    ? [clean]
    : [...STATIC_JSON_SOURCES];
}

function pickStaticList(json) {
  if (Array.isArray(json)) {
    return json;
  }

  if (Array.isArray(json?.data)) {
    return json.data;
  }

  if (Array.isArray(json?.items)) {
    return json.items;
  }

  if (Array.isArray(json?.manga)) {
    return json.manga;
  }

  if (Array.isArray(json?.results)) {
    return json.results;
  }

  return [];
}

async function fetchStaticIndexSource(source) {
  const cleanSource = normalizeStaticSource(source);

  if (!cleanSource) {
    return [];
  }

  const rows = await fetchStaticJson(
    `${cleanSource}/manga/index.json`
  );

  return pickStaticList(rows).map((item) => ({
    ...item,
    source: cleanSource
  }));
}

async function fetchStaticHomeItems(source = "all") {
  const sources = getActiveStaticSources(source);

  const lists = await Promise.all(
    sources.map(fetchStaticIndexSource)
  );

  return lists.flat();
}

async function fetchStaticMangaDetail(source, slug) {
  const cleanSource = normalizeStaticSource(source);
  const cleanSlug = String(slug || "").trim();

  if (!cleanSource || !cleanSlug) {
    throw new Error("Source atau slug manga tidak valid.");
  }

  const manga = await fetchStaticJson(
    `${cleanSource}/manga/${encodeURIComponent(cleanSlug)}.json`
  );

  return {
    ...manga,
    source: cleanSource,
    slug: manga.slug || cleanSlug
  };
}

async function fetchStaticChapter(source, mangaSlug, chapterSlug) {
  const cleanSource = normalizeStaticSource(source);
  const cleanManga = String(mangaSlug || "").trim();
  const cleanChapter = String(chapterSlug || "").trim();

  if (!cleanSource || !cleanManga || !cleanChapter) {
    throw new Error("Source, manga, atau chapter tidak valid.");
  }

  const chapter = await fetchStaticJson(
    `${cleanSource}/chapter/${encodeURIComponent(cleanManga)}/${encodeURIComponent(cleanChapter)}.json`
  );

  return {
    ...chapter,
    slug: chapter.slug || cleanChapter,
    title: chapter.title || cleanChapter,
    images: Array.isArray(chapter.images) ? chapter.images : []
  };
}

function itemMatchesSearch(item, q) {
  const keyword = String(q || "").trim().toLowerCase();

  if (!keyword) {
    return true;
  }

  const text = [
    item.title,
    item.slug,
    item.alternative,
    item.author,
    item.artist,
    stripHtml(item.synopsis || ""),
    ...getItemGenres(item)
  ]
    .join(" ")
    .toLowerCase();

  return text.includes(keyword);
}

function itemMatchesType(item, type) {
  const cleanType = String(type || "all").toLowerCase();

  if (cleanType === "all") {
    return true;
  }

  return String(item.type || "").toLowerCase() === cleanType;
}

function itemMatchesGenre(item, genre) {
  const cleanGenre = normalizeGenreName(cleanGenreName(genre));

  if (!cleanGenre) {
    return true;
  }

  return getItemGenres(item).some((g) => (
    normalizeGenreName(cleanGenreName(g)) === cleanGenre
  ));
}

function itemMatchesHomeMode(item, mode) {
  const cleanMode = String(mode || "update").toLowerCase();

  if (cleanMode !== "completed") {
    return true;
  }

  const status = String(item.status || "").toLowerCase();

  return [
    "completed",
    "complete",
    "tamat",
    "end",
    "ended",
    "finish",
    "finished"
  ].some((word) => status.includes(word));
}

function getStaticUpdateTime(item) {
  return Number(item?.updated_at || item?.source_updated_at || item?.manga_date || item?.date || 0);
}

function getStaticNewTime(item) {
  return Number(item?.manga_date || item?.created_at || item?.updated_at || item?.date || 0);
}

function sortStaticHomeItems(items, mode = "update") {
  const cleanMode = String(mode || "update").toLowerCase();

  return [...items].sort((a, b) => {
    const av =
      cleanMode === "new"
        ? getStaticNewTime(a)
        : getStaticUpdateTime(a);

    const bv =
      cleanMode === "new"
        ? getStaticNewTime(b)
        : getStaticUpdateTime(b);

    return bv - av;
  });
}

function paginateStaticItems(items, limit, offset) {
  const safeLimit = Math.max(1, Number(limit || 60));
  const safeOffset = Math.max(0, Number(offset || 0));
  const data = items.slice(safeOffset, safeOffset + safeLimit);
  const nextOffset = safeOffset + data.length;

  return {
    data,
    total: items.length,
    next_offset: nextOffset,
    has_more: nextOffset < items.length
  };
}

function filterStaticHomeItems(items, {
  q = "",
  type = "all",
  mode = "update",
  genre = ""
} = {}) {
  return items.filter((item) => (
    itemMatchesSearch(item, q) &&
    itemMatchesType(item, type) &&
    itemMatchesHomeMode(item, mode) &&
    itemMatchesGenre(item, genre)
  ));
}

function resetHomeList() {
  state.offset = 0;
  state.total = 0;
  state.hasMore = false;

  if (grid) {
    grid.innerHTML = "";
  }
}


/* =========================
   PAGE RESTORE STATE
========================= */

const LIST_RESTORE_KEY = "comicaso_list_restore_v1";

function getCurrentPageName() {
  const params = getParams?.() || new URLSearchParams(window.location.search);
  return params.get("page") || "home";
}

function getHorizontalScrollLeft(selector) {
  return Number(document.querySelector(selector)?.scrollLeft || 0);
}

function setHorizontalScrollLeft(selector, value) {
  const el = document.querySelector(selector);
  if (el) {
    el.scrollLeft = Number(value || 0);
  }
}

function saveListRestore(page = getCurrentPageName()) {
  try {
    const base = {
      page,
      source: state.appSettings?.source || "all",
      scrollY: window.scrollY || 0,
      savedAt: Date.now()
    };

    if (page === "genre") {
      sessionStorage.setItem(LIST_RESTORE_KEY, JSON.stringify({
        ...base,
        genre: state.genrePageGenre || cleanGenreName(getParams?.().get("genre") || ""),
        offset: Number(state.genrePageOffset || 0),
        total: Number(state.genrePageTotal || 0),
        genreScrollLeft: getHorizontalScrollLeft(".genre-page-tabs")
      }));
      return;
    }

    sessionStorage.setItem(LIST_RESTORE_KEY, JSON.stringify({
      ...base,
      page: "home",
      mode: state.homeMode || "update",
      type: state.homeType || "all",
      q: state.q || "",
      homeGenre: state.homeGenre || "",
      offset: Number(state.offset || 0),
      total: Number(state.total || 0),
      homeGenreScrollLeft: getHorizontalScrollLeft(".home-genre-tabs")
    }));
  } catch (err) {}
}

function readListRestore() {
  try {
    const raw = sessionStorage.getItem(LIST_RESTORE_KEY);
    if (!raw) return null;

    const data = JSON.parse(raw);
    if (!data || typeof data !== "object") return null;

    // Restore hanya dianggap segar 2 jam, biar tidak nyangkut lama.
    if (data.savedAt && Date.now() - Number(data.savedAt) > 2 * 60 * 60 * 1000) {
      sessionStorage.removeItem(LIST_RESTORE_KEY);
      return null;
    }

    return data;
  } catch (err) {
    return null;
  }
}

function prepareHomeRestore() {
  const data = readListRestore();

  if (!data || data.page !== "home") {
    return;
  }

  if (data.source) {
    saveAppSettings({
      ...state.appSettings,
      source: data.source
    });
  }

  state.homeMode = data.mode || "update";
  state.homeType = data.type || "all";
  state.q = data.q || "";
  state.homeGenre = data.homeGenre || state.homeGenre || "";
  state.__homeRestoreTargetOffset = Math.max(0, Number(data.offset || 0));
  state.__homeRestoreScrollY = Math.max(0, Number(data.scrollY || 0));
  state.__homeGenreScrollLeft = Math.max(0, Number(data.homeGenreScrollLeft || 0));

  if (searchInput) {
    searchInput.value = state.q || "";
  }
}

function prepareGenreRestore(genre) {
  const data = readListRestore();
  const clean = cleanGenreName(genre);

  if (!data || data.page !== "genre") {
    return;
  }

  if (normalizeGenreName(data.genre || "") !== normalizeGenreName(clean || "")) {
    return;
  }

  if (data.source) {
    saveAppSettings({
      ...state.appSettings,
      source: data.source
    });
  }

  state.__genreRestoreTargetOffset = Math.max(0, Number(data.offset || 0));
  state.__genreRestoreScrollY = Math.max(0, Number(data.scrollY || 0));
  state.__genreScrollLeft = Math.max(0, Number(data.genreScrollLeft || 0));
}

function restoreWindowScroll(value) {
  const y = Math.max(0, Number(value || 0));
  if (!y) return;

  requestAnimationFrame(() => {
    setTimeout(() => {
      window.scrollTo({ top: y, left: 0, behavior: "auto" });
    }, 80);
  });
}

function scrollActiveChipIntoView(selector, savedLeft = null) {
  requestAnimationFrame(() => {
    setTimeout(() => {
      const wrap = document.querySelector(selector);
      if (!wrap) return;

      if (savedLeft !== null && Number(savedLeft) > 0) {
        wrap.scrollLeft = Number(savedLeft);
        return;
      }

      const active = wrap.querySelector(".active");
      if (!active) return;

      // Jangan pakai scrollIntoView di genre bar.
      // Di beberapa browser/Telegram WebView, scrollIntoView ikut menggeser
      // scroll vertikal halaman ke paling atas. Yang kita butuhkan hanya
      // geser horizontal chip aktif supaya genre terpilih terlihat di kiri.
      const wrapRect = wrap.getBoundingClientRect();
      const activeRect = active.getBoundingClientRect();
      const deltaLeft = activeRect.left - wrapRect.left;
      const targetLeft = Math.max(0, wrap.scrollLeft + deltaLeft - 8);

      if (typeof wrap.scrollTo === "function") {
        wrap.scrollTo({
          left: targetLeft,
          behavior: "smooth"
        });
      } else {
        wrap.scrollLeft = targetLeft;
      }
    }, 80);
  });
}


function normalizeLatestChapterLabel(item) {
  const candidates = [
    item?.latest_chapter_title,
    item?.latest_chapter,
    item?.last_chapter_title,
    item?.last_chapter,
    item?.latest,
    item?.chapter_title,
    item?.chapter
  ];

  for (const value of candidates) {
    const text = String(value || "").trim();

    if (!text) continue;

    // Hindari field tanggal/timestamp ikut tampil sebagai chapter.
    if (/^\d{9,13}$/.test(text)) continue;
    if (/^\d{4}-\d{2}-\d{2}/.test(text)) continue;

    return text;
  }

  const slug = String(
    item?.latest_chapter_slug ||
    item?.last_chapter_slug ||
    item?.chapter_slug ||
    ""
  ).trim();

  if (!slug) return "";

  return slug
    .replace(/[-_]+/g, " ")
    .replace(/\bchapter\b/i, "Chapter")
    .replace(/\s+/g, " ")
    .trim();
}

function formatMangaDate(value) {
  if (!value) return "";

  if (/^\d+$/.test(String(value))) {
    let ts = Number(value);

    if (ts < 10000000000) {
      ts *= 1000;
    }

    const date = new Date(ts);

    if (Number.isNaN(date.getTime())) {
      return "";
    }

    return date.toLocaleDateString("id-ID", {
      day: "2-digit",
      month: "short",
      year: "numeric"
    });
  }

  const date = new Date(value);

  if (!Number.isNaN(date.getTime())) {
    return date.toLocaleDateString("id-ID", {
      day: "2-digit",
      month: "short",
      year: "numeric"
    });
  }

  return String(value);
}

function escapeJs(value) {
  return String(value || "")
    .replace(/\\/g, "\\\\")
    .replace(/'/g, "\\'")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "");
}

function userHasVvip(author = {}) {
  return Boolean(author?.is_vvip || author?.badge === "VVIP");
}

function renderVvipBadge(author = {}) {
  return userHasVvip(author)
    ? `<span class="vvip-badge">VVIP</span>`
    : "";
}

function isRestrictedMedusaContent(source) {
  return normalizeSourceName(source) === "medusa" && !isWebAuthenticated();
}

function renderMedusaLoginGate(kind = "detail") {
  setNormalMode();
  renderBottomNav?.("home");

  if (statusText) {
    statusText.textContent = "Login diperlukan";
  }

  if (grid) {
    grid.innerHTML = `
      <section class="profile-card content-gate-card">
        <div class="content-gate-icon">
          ${icon("lock") || icon("profile")}
        </div>

        <div class="content-gate-copy">
          <span>Konten terbatas</span>
          <h2>Login diperlukan untuk membuka konten ini</h2>
          <p>
            Mohon maaf, konten dari sumber ini mengandung materi untuk pembaca dewasa.
            Silakan login dengan Google untuk membuka ${kind === "reader" ? "chapter" : "manga"} ini.
          </p>
        </div>

        <div class="profile-actions">
          <button
            type="button"
            class="action-btn primary-action"
            onclick="loginWithGoogle()">
            ${icon("profile")}
            <span>Login dengan Google</span>
          </button>

          <button
            type="button"
            class="action-btn"
            onclick="goHomeFresh()">
            ${icon("home")}
            <span>Kembali ke Home</span>
          </button>
        </div>
      </section>
    `;
  }

  hideActionLoading();
}

function homeChatPlatform() {
  return "web";
}

function homeChatIsLoggedIn() {
  return isWebAuthenticated();
}

function homeChatUserPayload() {
  const user =
    state.authUser || {};

  return {
    platform: homeChatPlatform(),
    user_id: state.userId || 0,
    display_name:
      user.display_name ||
      user.email ||
      state.firstName ||
      "Web Reader",
    username: user.username || "",
    avatar_url: user.avatar_url || ""
  };
}

function renderHomeChatbox() {
  const loggedIn =
    homeChatIsLoggedIn();

  return `
    <section id="homeChatbox" class="home-chatbox">
      <div class="home-chat-head">
        <div>
          <h2>Chatbox</h2>
          <p>Obrolan web dan mini app</p>
        </div>
        <button
          type="button"
          onclick="loadHomeChatbox({ force: true })"
          aria-label="Muat ulang chat">
          ${icon("history")}
        </button>
      </div>

      <div id="homeChatList" class="home-chat-list">
        ${
          Array.isArray(state.chatMessages) && state.chatMessages.length
            ? state.chatMessages.map(renderHomeChatMessage).join("")
            : `<div class="home-chat-empty">Memuat chat...</div>`
        }
      </div>

      ${
        loggedIn
          ? `<form class="home-chat-form" onsubmit="submitHomeChatbox(event)">
              <textarea
                id="homeChatInput"
                rows="2"
                maxlength="500"
                placeholder="Tulis pesan..."></textarea>
              <button id="homeChatSendBtn" type="submit">${icon("share")}<span data-chat-send-label>Kirim</span></button>
            </form>`
          : `<div class="home-chat-login">
              <p>Login Google untuk ikut chat.</p>
              <button type="button" onclick="loginWithGoogle()">
                ${icon("profile")}
                <span>Login</span>
              </button>
            </div>`
      }
    </section>
  `;
}

function renderHomeChatMessage(item) {
  const author =
    item.author || {};

  const name =
    author.display_name ||
    author.username ||
    "Pembaca";

  const isVvip =
    userHasVvip(author);

  const localStatus = item.pending
    ? " · mengirim..."
    : item.failed
      ? " · gagal"
      : "";

  const canDelete =
    item.owned && !item.pending && !item.failed && Number(item.id) > 0;

  return `
    <article class="home-chat-message ${item.owned ? "owned" : ""} ${isVvip ? "is-vvip" : ""} ${item.pending ? "is-pending" : ""} ${item.failed ? "is-failed" : ""}">
      <div class="home-chat-avatar ${isVvip ? "vvip-avatar" : ""}">
        ${renderCommentAvatar(author)}
      </div>
      <div class="home-chat-bubble">
        <div class="home-chat-top">
          <strong>${escapeHtml(name)}</strong>
          ${renderVvipBadge(author)}
          <small>${escapeHtml(item.platform || "web")} · ${escapeHtml(formatCommentDate(item.created_at))}${escapeHtml(localStatus)}</small>
        </div>
        <p>${escapeHtml(item.body || "")}</p>
        ${
          canDelete
            ? `<button type="button" onclick="deleteHomeChatMessage(${Number(item.id)})">Hapus</button>`
            : item.failed
              ? `<button type="button" onclick="removeLocalFailedChat('${escapeJs(String(item.local_id || item.id || ''))}')">Buang</button>`
              : ""
        }
      </div>
    </article>
  `;
}

function renderHomeChatMessages(items) {
  const list =
    document.getElementById("homeChatList");

  if (!list) return;

  if (!Array.isArray(items) || !items.length) {
    list.innerHTML =
      `<div class="home-chat-empty">Belum ada pesan.</div>`;
    return;
  }

  list.innerHTML =
    items.map(renderHomeChatMessage).join("");
  list.scrollTop = list.scrollHeight;
}

async function loadHomeChatbox({ force = false } = {}) {
  const list =
    document.getElementById("homeChatList");

  if (!list || (state.chatLoading && !force)) {
    return;
  }

  state.chatLoading = true;

  try {
    const json =
      await apiGet(
        `/api/chatbox.php?platform=${encodeURIComponent(homeChatPlatform())}&user_id=${encodeURIComponent(state.userId || 0)}&limit=50`
      );

    state.chatMessages =
      Array.isArray(json.data)
        ? json.data
        : [];

    renderHomeChatMessages(state.chatMessages);
  } catch (err) {
    list.innerHTML =
      `<div class="home-chat-empty">${escapeHtml(err.message || "Gagal memuat chat.")}</div>`;
  } finally {
    state.chatLoading = false;
  }
}

function initHomeChatbox() {
  if (!document.getElementById("homeChatbox")) {
    return;
  }

  if (Array.isArray(state.chatMessages) && state.chatMessages.length) {
    renderHomeChatMessages(state.chatMessages);
  } else {
    loadHomeChatbox().catch(() => {});
  }

  if (window.__homeChatPoller) {
    return;
  }

  window.__homeChatPoller =
    setInterval(() => {
      if (!document.getElementById("homeChatbox")) {
        clearInterval(window.__homeChatPoller);
        window.__homeChatPoller = null;
        return;
      }

      if (!document.hidden) {
        loadHomeChatbox().catch(() => {});
      }
    }, 15000);
}


let homeChatCooldownTimer = null;

function getHomeChatSendButton() {
  return document.getElementById("homeChatSendBtn");
}

function setHomeChatSendButtonLabel(text, disabled = false) {
  const btn = getHomeChatSendButton();
  if (!btn) return;

  const label = btn.querySelector("[data-chat-send-label]");
  if (label) {
    label.textContent = text;
  } else {
    btn.textContent = text;
  }

  btn.disabled = disabled;
}

function startHomeChatCooldown(seconds = 5) {
  const total = Math.max(1, Number(seconds || 5));
  state.chatCooldownUntil = Date.now() + total * 1000;

  clearInterval(homeChatCooldownTimer);

  const tick = () => {
    const left = Math.max(0, Math.ceil((state.chatCooldownUntil - Date.now()) / 1000));

    if (left <= 0) {
      clearInterval(homeChatCooldownTimer);
      homeChatCooldownTimer = null;
      state.chatCooldownUntil = 0;
      setHomeChatSendButtonLabel("Kirim", false);
      return;
    }

    setHomeChatSendButtonLabel(`${left}s`, true);
  };

  tick();
  homeChatCooldownTimer = setInterval(tick, 250);
}

function isHomeChatCoolingDown() {
  return Number(state.chatCooldownUntil || 0) > Date.now();
}

function makeLocalHomeChatMessage(body) {
  const payload = homeChatUserPayload();
  const localId = `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;

  return {
    id: localId,
    local_id: localId,
    platform: payload.platform || homeChatPlatform(),
    body,
    created_at: new Date().toISOString(),
    owned: true,
    pending: true,
    failed: false,
    author: {
      id: payload.user_id || state.userId || 0,
      display_name: payload.display_name || state.firstName || "Web Reader",
      username: payload.username || "",
      avatar_url: payload.avatar_url || "",
      subscription: state.authUser?.subscription || state.authUser?.plan || ""
    }
  };
}

function pushLocalHomeChatMessage(message) {
  state.chatMessages = Array.isArray(state.chatMessages)
    ? [...state.chatMessages, message]
    : [message];

  renderHomeChatMessages(state.chatMessages);
}

function updateLocalHomeChatMessage(localId, patch) {
  if (!Array.isArray(state.chatMessages)) return;

  state.chatMessages = state.chatMessages.map((item) => {
    if (String(item.local_id || item.id || "") !== String(localId)) {
      return item;
    }

    return {
      ...item,
      ...patch
    };
  });

  renderHomeChatMessages(state.chatMessages);
}

function removeLocalFailedChat(localId) {
  if (!Array.isArray(state.chatMessages)) return;

  state.chatMessages = state.chatMessages.filter((item) => {
    return String(item.local_id || item.id || "") !== String(localId);
  });

  renderHomeChatMessages(state.chatMessages);
}

async function submitHomeChatbox(event) {
  event?.preventDefault?.();

  if (!homeChatIsLoggedIn()) {
    requireGoogleLogin();
    return;
  }

  if (isHomeChatCoolingDown()) {
    return;
  }

  const input =
    document.getElementById("homeChatInput");

  const body =
    String(input?.value || "").trim();

  if (!body) {
    toast("Pesan masih kosong.");
    return;
  }

  if (input) {
    input.value = "";
    input.focus?.();
  }

  const localMessage = makeLocalHomeChatMessage(body);
  pushLocalHomeChatMessage(localMessage);
  startHomeChatCooldown(5);

  try {
    const json = await apiPost("/api/chatbox.php", {
      ...homeChatUserPayload(),
      body
    });

    const saved = json?.data || json?.message || null;

    updateLocalHomeChatMessage(localMessage.local_id, {
      ...(saved && typeof saved === "object" ? saved : {}),
      id: Number(saved?.id || 0) || localMessage.id,
      local_id: localMessage.local_id,
      body,
      owned: true,
      pending: false,
      failed: false
    });

    setTimeout(() => {
      loadHomeChatbox({ force: true }).catch(() => {});
    }, 900);
  } catch (err) {
    updateLocalHomeChatMessage(localMessage.local_id, {
      pending: false,
      failed: true
    });
    toast(err.message || "Gagal kirim chat.");
  }
}

async function deleteHomeChatMessage(messageId) {
  if (!homeChatIsLoggedIn()) {
    requireGoogleLogin();
    return;
  }

  try {
    await apiDelete("/api/chatbox.php", {
      platform: homeChatPlatform(),
      user_id: state.userId || 0,
      message_id: messageId
    });

    await loadHomeChatbox({ force: true });
  } catch (err) {
    toast(err.message || "Gagal hapus chat.");
  }
}

function getChapterTitleForSort(chapter) {
  return String(
    chapter?.title ||
    chapter?.name ||
    chapter?.chapter_title ||
    chapter?.slug ||
    ""
  ).trim();
}

function sortChapters(chapters, direction = state.chapterSort || "asc") {
  const list = [...(Array.isArray(chapters) ? chapters : [])];

  list.sort((a, b) => {
    const titleA = getChapterTitleForSort(a);
    const titleB = getChapterTitleForSort(b);

    return titleA.localeCompare(titleB, "id", {
      numeric: true,
      sensitivity: "base"
    });
  });

  if (direction === "desc") {
    list.reverse();
  }

  return list;
}

function toggleChapterSort() {
  state.chapterSort =
    state.chapterSort === "desc"
      ? "asc"
      : "desc";

  if (state.currentManga?.chapters) {
    const chapters =
      sortChapters(state.currentManga.chapters);

    const list =
      document.getElementById("chapterList");

    if (list) {
      list.innerHTML = renderChapterList(
        chapters,
        state.currentManga.source,
        state.currentManga.slug,
        state.currentManga.title,
        state.currentManga.thumbnail
      );
    }

    const btn =
      document.getElementById("chapterSortBtn");

    if (btn) {
      btn.innerHTML =
        state.chapterSort === "desc"
          ? (icon("up") || "↑")
          : (icon("down") || "↓");

      btn.title =
        state.chapterSort === "desc"
          ? "Urutan Z-A"
          : "Urutan A-Z";
    }
  }
}

function homeGridIcon() {
  return icon("grid") || `
    <svg viewBox="0 0 24 24">
      <path d="M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z"/>
    </svg>
  `;
}

function homeListIcon() {
  return icon("list") || `
    <svg viewBox="0 0 24 24">
      <path d="M4 6h3v3H4V6Zm5 0h11v2H9V6Zm-5 5h3v3H4v-3Zm5 0h11v2H9v-2Zm-5 5h3v3H4v-3Zm5 0h11v2H9v-2Z"/>
    </svg>
  `;
}

function renderHomeControls() {
  const mode = state.homeMode || "update";
  const type = state.homeType || "all";
  const view = state.homeView || "grid";

  const modes = [
    { key: "update", label: "Update" },
    { key: "new", label: "New" },
    { key: "completed", label: "Completed" }
  ];

  const types = [
    { key: "all", label: "All" },
    { key: "manhwa", label: "Manhwa" },
    { key: "manga", label: "Manga" },
    { key: "manhua", label: "Manhua" }
  ];

  return `
    <section class="home-feed-head">
      <div class="home-mode-tabs">
        ${modes.map((item) => `
          <button
            type="button"
            class="home-mode-tab ${mode === item.key ? "active" : ""}"
            onclick="setHomeMode('${item.key}')">
            ${escapeHtml(item.label)}
          </button>
        `).join("")}
      </div>

      <div class="home-filter-row">
        <div class="home-type-tabs">
          ${types.map((item) => `
            <button
              type="button"
              class="home-type-tab ${type === item.key ? "active" : ""}"
              onclick="setHomeType('${item.key}')">
              ${escapeHtml(item.label)}
            </button>
          `).join("")}
        </div>

        <div class="home-view-tabs">
          <button
            type="button"
            class="home-view-btn ${view === "grid" ? "active" : ""}"
            onclick="setHomeView('grid')"
            aria-label="Grid view">
            ${homeGridIcon()}
          </button>

          <button
            type="button"
            class="home-view-btn ${view === "list" ? "active" : ""}"
            onclick="setHomeView('list')"
            aria-label="List view">
            ${homeListIcon()}
          </button>
        </div>
      </div>
    </section>
  `;
}

function softReloadHome() {
  if (grid) {
    grid.classList.add("is-soft-loading");
  }

  if (statusText) {
    statusText.textContent = "Memuat data...";
  }

  loadHome({ soft: true });
}

function setHomeMode(mode) {
  const allowed = ["update", "new", "completed"];

  state.homeMode =
    allowed.includes(mode)
      ? mode
      : "update";

  softReloadHome();
}

function setHomeType(type) {
  const allowed = ["all", "manhwa", "manga", "manhua"];

  state.homeType =
    allowed.includes(type)
      ? type
      : "all";

  softReloadHome();
}

function setHomeView(view) {
  state.homeView =
    view === "list"
      ? "list"
      : "grid";

  if (Array.isArray(state.homeItems)) {
    renderManga(state.homeItems, false, {
      keepTrending: true
    });
    renderLoadMore();
    return;
  }

  softReloadHome();
}

async function loadHome({
  append = false,
  soft = false
} = {}) {
  if (state.loading) return;

  state.loading = true;

  if (!append && !soft) {
    resetHomeList();

    if (statusText) {
      statusText.textContent = "Memuat data...";
    }
  } else if (append) {
    const btn =
      document.getElementById("loadMoreBtn");

    if (btn) {
      btn.disabled = true;
      btn.textContent = "Memuat...";
    }
  }

  const activeSource =
    state.appSettings?.source || "all";

  state.source = activeSource;

  const restoreTargetOffset =
    !append && !soft
      ? Math.max(0, Number(state.__homeRestoreTargetOffset || 0))
      : 0;

  const loadLimit =
    restoreTargetOffset > Number(state.limit || 0)
      ? restoreTargetOffset
      : Number(state.limit || 60);

  const params = new URLSearchParams({
    source: activeSource,
    q: state.q,
    mode: state.homeMode || "update",
    type: state.homeType || "all",
    limit: String(loadLimit),
    offset: String(state.offset)
  });

  if (!append) {
    state.offset = 0;
    params.set("offset", "0");
  }

  try {
    const allStaticItems =
      await fetchStaticHomeItems(activeSource);

    const filteredStaticItems =
      filterStaticHomeItems(allStaticItems, {
        q: state.q,
        mode: state.homeMode || "update",
        type: state.homeType || "all"
      });

    /*
    | Filter setting user seperti blocked genre harus dilakukan SEBELUM pagination.
    | Kalau dilakukan setelah pagination, tab Update bisa terlihat tidak lengkap
    | karena sebagian item halaman pertama kebuang setelah data dipotong.
    */
    const visibleStaticItems =
      filterMangaItemsBySettings(filteredStaticItems);

    const sortedStaticItems =
      sortStaticHomeItems(
        visibleStaticItems,
        state.homeMode || "update"
      );

    const json =
      paginateStaticItems(
        sortedStaticItems,
        loadLimit,
        state.offset
      );

    const rawItems =
      Array.isArray(json.data)
        ? json.data
        : [];

    updateGenresFromHomeItems(
      allStaticItems,
      activeSource
    );

    if (!state.q && !append) {
      await loadGenresFromIndexJson();
      await loadHomeGenrePreview(activeSource);
    }

    const filteredItems =
      rawItems;

    state.homeItems =
      append && Array.isArray(state.homeItems)
        ? [...state.homeItems, ...filteredItems]
        : filteredItems;

    state.total =
      Number(json.total || filteredItems.length || 0);

    state.offset =
      Number(json.next_offset || 0);

    state.hasMore =
      Boolean(json.has_more);

    renderManga(
      filteredItems,
      append,
      {
        keepTrending: soft
      }
    );

    const shown =
      Math.min(
        state.offset || state.homeItems.length,
        state.total
      );

    const sourceLabel =
      activeSource === "comicazen"
        ? "Comicazen"
        : activeSource === "medusa"
          ? "Medusa"
          : "Semua";

    if (statusText) {
      statusText.textContent =
        `${shown} dari ${state.total} judul · ${sourceLabel}`;
    }

    renderLoadMore();

    if (!append && !soft && state.__homeRestoreScrollY) {
      restoreWindowScroll(state.__homeRestoreScrollY);
      state.__homeRestoreScrollY = 0;
      state.__homeRestoreTargetOffset = 0;
    }

    if (!append && !state.q && state.__homeGenreScrollLeft) {
      scrollActiveChipIntoView(".home-genre-tabs", state.__homeGenreScrollLeft);
      state.__homeGenreScrollLeft = 0;
    } else if (!append && !state.q) {
      scrollActiveChipIntoView(".home-genre-tabs");
    }

    if (!append && !state.q && !soft) {
      bindTrendingTabs();
      loadTrending(state.trendingPeriod);
    }

  } catch (err) {
    if (statusText) {
      statusText.textContent =
        "Gagal memuat data.";
    }

    if (grid) {
      grid.innerHTML =
        `<div class="empty">${escapeHtml(err.message || "Terjadi kesalahan.")}</div>`;
    }

  } finally {
    state.loading = false;

    if (grid) {
      grid.classList.remove("is-soft-loading");
    }
  }
}

function renderMangaCard(item) {
  const title =
    escapeHtml(
      item.title ||
      item.alternative ||
      "Untitled"
    );

  const source =
    escapeHtml(item.source || "");

  const slug =
    escapeHtml(item.slug || "");

  const thumb =
    escapeHtml(item.thumbnail || "");

  const latest =
    escapeHtml(
      normalizeLatestChapterLabel(item)
    );

  const rawDate =
    state.homeMode === "new"
      ? (item.manga_date || item.updated_at || item.date || "")
      : (item.updated_at || item.manga_date || item.date || "");

  const updated =
    escapeHtml(formatMangaDate(rawDate));

  const synopsis =
    escapeHtml(
      stripHtml(
        item.synopsis ||
        item.description ||
        ""
      )
    );

  if (state.homeView === "list") {
    return `
      <article
        class="card manga-list-card"
        onclick="openManga('${source}', '${slug}')">

        <div class="cover manga-list-cover">
          ${
            thumb
              ? `<img
                  src="${thumb}"
                  alt="${title}"
                  loading="lazy"
                  onerror="this.style.display='none'; this.nextElementSibling.style.display='grid';"
                />`
              : ""
          }

          <div
            class="no-cover"
            style="${thumb ? "display:none" : ""}">
            No Image
          </div>
        </div>

        <div class="card-body manga-list-body">
          <div class="manga-list-top">
            <span class="badge manga-list-badge">${source}</span>
            <span class="manga-list-type">${escapeHtml(item.type || "")}</span>
          </div>

          <h2>${title}</h2>

          <p class="manga-list-meta">
            ${latest || "Belum ada info chapter"}
          </p>

          <p class="manga-list-synopsis">
            ${synopsis || "Belum ada sinopsis."}
          </p>
        </div>
      </article>
    `;
  }

  return `
    <article
      class="card"
      onclick="openManga('${source}', '${slug}')">

      <div class="cover">
        ${
          thumb
            ? `<img
                src="${thumb}"
                alt="${title}"
                loading="lazy"
                onerror="this.style.display='none'; this.nextElementSibling.style.display='grid';"
              />`
            : ""
        }

        <div
          class="no-cover"
          style="${thumb ? "display:none" : ""}">
          No Image
        </div>

        <span class="badge">${source}</span>
      </div>

      <div class="card-body">
        <h2>${title}</h2>
        <p>${latest || "Belum ada info chapter"}</p>
      </div>
    </article>
  `;
}

function renderManga(
  items,
  append = false,
  options = {}
) {
  if (!grid) return;

  const listClass =
    state.homeView === "list"
      ? "home-list-mode"
      : "home-grid-mode";

  if (!append && !items.length) {
    if (statusText) {
      statusText.textContent =
        "Belum ada data manga.";
    }

    grid.classList.remove("home-grid-mode", "home-list-mode");
    grid.classList.add(listClass);

    grid.innerHTML = `
      ${!state.q ? trendingShell() : ""}
      ${!state.q ? renderHomeGenreSection() : ""}
      ${!state.q ? renderHomeChatbox() : ""}
      ${!state.q ? renderHomeControls() : ""}
      <div class="empty">
        Tidak ada judul yang cocok dengan pengaturan saat ini.
      </div>
    `;

    if (!state.q) {
      initHomeChatbox();
    }

    return;
  }

  const html =
    items.map(renderMangaCard).join("");

  if (append) {
    const oldBtnWrap =
      document.getElementById("loadMoreWrap");

    if (oldBtnWrap) {
      oldBtnWrap.remove();
    }

    grid.insertAdjacentHTML(
      "beforeend",
      html
    );

  } else {
    grid.classList.remove("home-grid-mode", "home-list-mode");
    grid.classList.add(listClass);

    const trendingHtml =
      !state.q
        ? (
            options.keepTrending &&
            document.getElementById("trendingSection")
              ? document.getElementById("trendingSection").outerHTML
              : trendingShell()
          )
        : "";

    grid.innerHTML =
      `${trendingHtml}${!state.q ? renderHomeGenreSection() : ""}${!state.q ? renderHomeChatbox() : ""}${!state.q ? renderHomeControls() : ""}${html}`;

    if (
      !state.q &&
      options.keepTrending &&
      document.getElementById("trendingSection")
    ) {
      bindTrendingTabs();
    }

    if (!state.q) {
      initHomeChatbox();
    }
  }
}

function renderLoadMore() {
  if (!grid) return;

  const old =
    document.getElementById("loadMoreWrap");

  if (old) old.remove();

  if (!state.hasMore) return;

  grid.insertAdjacentHTML("beforeend", `
    <div id="loadMoreWrap" class="load-more-wrap">
      <button
        id="loadMoreBtn"
        class="load-more-btn"
        type="button">
        Lihat Selengkapnya
      </button>
    </div>
  `);

  document
    .getElementById("loadMoreBtn")
    ?.addEventListener("click", () => {
      loadHome({ append: true });
    });
}

function openManga(source, slug) {
  if (!source || !slug) return;

  if (!showActionLoading("Memuat...")) {
    return;
  }

  const currentPage = getCurrentPageName();
  saveListRestore(currentPage === "genre" ? "genre" : "home");

  window.location.href =
    `/?page=manga&source=${encodeURIComponent(source)}&slug=${encodeURIComponent(slug)}`;
}
/* =========================
   SETTINGS PAGE
========================= */

const SOURCE_LABELS = {
  all: "Semua",
  comicazen: "Comicazen",
  medusa: "Medusa"
};

function getSettingSources() {
  const active =
    normalizeSourceName(
      state.appSettings?.source || "all"
    );

  if (active === "comicazen" || active === "medusa") {
    return [active];
  }

  return ["comicazen", "medusa"];
}

function getVisibleHomeGenres(limit = 30) {
  const map = new Map();

  getSettingSources().forEach((source) => {
    getAvailableGenres(source).forEach((genre) => {
      if (isBlockedGenre(source, genre)) {
        return;
      }

      const clean =
        cleanGenreName(genre);

      const key =
        normalizeGenreName(clean);

      if (key && !map.has(key)) {
        map.set(key, clean);
      }
    });
  });

  const genres =
    Array
      .from(map.values())
      .sort((a, b) =>
        a.localeCompare(b, "en", { sensitivity: "base" })
      );

  return Number(limit || 0) > 0
    ? genres.slice(0, Number(limit))
    : genres;
}

async function loadHomeGenrePreview(activeSource = state.appSettings?.source || "all") {
  const genres =
    getVisibleHomeGenres(30);

  if (!genres.length) {
    state.homeGenre = "";
    state.homeGenreItems = [];
    state.homeGenreTotal = 0;
    return;
  }

  const activeKey =
    normalizeGenreName(state.homeGenre);

  const currentGenre =
    genres.find((genre) =>
      normalizeGenreName(genre) === activeKey
    ) || genres[0];

  state.homeGenre = currentGenre;

  const params = new URLSearchParams({
    source: activeSource || "all",
    q: "",
    mode: "update",
    type: "all",
    genre: currentGenre,
    limit: "12",
    offset: "0"
  });

  try {
    const allStaticItems =
      await fetchStaticHomeItems(activeSource);

    const filteredStaticItems =
      filterStaticHomeItems(allStaticItems, {
        q: "",
        mode: "update",
        type: "all",
        genre: currentGenre
      });

    const visibleStaticItems =
      filterMangaItemsBySettings(filteredStaticItems);

    const sortedStaticItems =
      sortStaticHomeItems(
        visibleStaticItems,
        "update"
      );

    const json =
      paginateStaticItems(
        sortedStaticItems,
        12,
        0
      );

    const rawItems =
      Array.isArray(json.data)
        ? json.data
        : [];

    const items =
      rawItems;

    state.homeGenreItems =
      items.slice(0, 10);

    state.homeGenreTotal =
      Number(json.total || items.length || 0);
  } catch (err) {
    console.warn(
      "Gagal mengambil preview genre:",
      err.message
    );

    state.homeGenreItems = [];
    state.homeGenreTotal = 0;
  }
}

function renderHomeGenrePreviewCard(item) {
  const title =
    escapeHtml(
      item.title ||
      item.alternative ||
      "Untitled"
    );

  const source =
    escapeHtml(item.source || "");

  const sourceRaw =
    escapeJs(item.source || "");

  const slugRaw =
    escapeJs(item.slug || "");

  const thumb =
    escapeHtml(item.thumbnail || "");

  const latest =
    escapeHtml(
      normalizeLatestChapterLabel(item)
    );

  const updated =
    escapeHtml(
      formatMangaDate(
        item.updated_at ||
        item.manga_date ||
        item.date ||
        ""
      )
    );

  const synopsis =
    escapeHtml(
      stripHtml(
        item.synopsis ||
        item.description ||
        ""
      )
    );

  return `
    <article
      class="home-genre-card"
      onclick="openManga('${sourceRaw}', '${slugRaw}')">

      <div class="home-genre-cover">
        ${
          thumb
            ? `<img src="${thumb}" alt="${title}" loading="lazy" />`
            : `<div class="no-cover">No Image</div>`
        }
      </div>

      <div class="home-genre-info">
        <div class="home-genre-meta">
          <span>${source}</span>
          <small>${latest || updated || "Update"}</small>
        </div>

        <h3>${title}</h3>
        <p>${synopsis || "Belum ada sinopsis."}</p>
      </div>
    </article>
  `;
}

function renderHomeGenreSection() {
  const genres =
    getVisibleHomeGenres(30);

  if (!genres.length || !state.homeGenre) {
    return "";
  }

  const activeKey =
    normalizeGenreName(state.homeGenre);

  const activeGenre =
    genres.find((genre) =>
      normalizeGenreName(genre) === activeKey
    ) || genres[0];

  const activeGenreJs =
    escapeHtml(escapeJs(activeGenre));

  const items =
    Array.isArray(state.homeGenreItems)
      ? state.homeGenreItems
      : [];

  return `
    <section
      id="homeGenreSection"
      class="home-genre-section">

      <div class="home-genre-head">
        <div>
          <h2>Genre</h2>
          <p>${escapeHtml(activeGenre)}</p>
        </div>

        <button
          type="button"
          class="home-genre-more"
          onclick="openGenrePage('${activeGenreJs}')">
          Selengkapnya
        </button>
      </div>

      <div class="home-genre-tabs">
        ${genres.map((genre) => {
          const active =
            normalizeGenreName(genre) ===
            normalizeGenreName(activeGenre);

          const safeGenre =
            escapeHtml(genre);

          const jsGenre =
            escapeHtml(escapeJs(genre));

          return `
            <button
              type="button"
              class="home-genre-chip ${active ? "active" : ""}"
              onclick="setHomeGenre('${jsGenre}')">
              ${safeGenre}
            </button>
          `;
        }).join("")}
      </div>

      <div class="home-genre-scroller">
        ${
          items.length
            ? items.map(renderHomeGenrePreviewCard).join("")
            : `<div class="home-genre-empty">Belum ada manga untuk genre ini.</div>`
        }
      </div>
    </section>
  `;
}

async function setHomeGenre(genre) {
  const clean =
    cleanGenreName(genre);

  if (!clean) {
    return;
  }

  state.homeGenre = clean;

  const section =
    document.getElementById("homeGenreSection");

  if (section) {
    section.classList.add("is-loading");
  }

  await loadHomeGenrePreview(
    state.appSettings?.source || "all"
  );

  renderManga(
    state.homeItems || [],
    false,
    {
      keepTrending: true
    }
  );

  renderLoadMore();
  scrollActiveChipIntoView(".home-genre-tabs");
}

function openGenrePage(genre) {
  const clean =
    cleanGenreName(genre);

  if (!clean) {
    return;
  }

  if (!showActionLoading("Memuat genre...")) {
    return;
  }

  saveListRestore("home");

  window.location.href =
    `/?page=genre&genre=${encodeURIComponent(clean)}`;
}

function renderGenrePageTabs(activeGenre) {
  // Page genre harus menampilkan semua genre lengkap A-Z, bukan cuma 60 pertama.
  const genres =
    getVisibleHomeGenres(0);

  const cleanActive =
    cleanGenreName(activeGenre);

  if (
    cleanActive &&
    !genres.some((genre) =>
      normalizeGenreName(genre) === normalizeGenreName(cleanActive)
    )
  ) {
    genres.unshift(cleanActive);
  }

  if (!genres.length) {
    return "";
  }

  const activeKey =
    normalizeGenreName(cleanActive || genres[0]);

  return `
    <div class="genre-page-tabs">
      ${genres.map((genre) => {
        const active =
          normalizeGenreName(genre) === activeKey;

        const safeGenre =
          escapeHtml(genre);

        const jsGenre =
          escapeHtml(escapeJs(genre));

        return `
          <button
            type="button"
            class="genre-page-chip ${active ? "active" : ""}"
            onclick="switchGenrePage('${jsGenre}')">
            ${safeGenre}
          </button>
        `;
      }).join("")}
    </div>
  `;
}

async function switchGenrePage(genre) {
  const clean =
    cleanGenreName(genre);

  if (!clean) {
    return;
  }

  if (
    normalizeGenreName(clean) ===
    normalizeGenreName(state.genrePageGenre)
  ) {
    return;
  }

  window.history?.replaceState?.(
    null,
    "",
    `/?page=genre&genre=${encodeURIComponent(clean)}`
  );

  await loadGenrePage(clean);
}

function renderGenrePageItem(item) {
  const title =
    escapeHtml(
      item.title ||
      item.alternative ||
      "Untitled"
    );

  const source =
    escapeHtml(item.source || "");

  const sourceRaw =
    escapeJs(item.source || "");

  const slugRaw =
    escapeJs(item.slug || "");

  const thumb =
    escapeHtml(item.thumbnail || "");

  const latest =
    escapeHtml(
      normalizeLatestChapterLabel(item)
    );

  const updated =
    escapeHtml(
      formatMangaDate(
        item.updated_at ||
        item.manga_date ||
        item.date ||
        ""
      )
    );

  const synopsis =
    escapeHtml(
      stripHtml(
        item.synopsis ||
        item.description ||
        ""
      )
    );

  return `
    <article
      class="genre-page-item"
      onclick="openManga('${sourceRaw}', '${slugRaw}')">

      <div class="genre-page-cover">
        ${
          thumb
            ? `<img src="${thumb}" alt="${title}" loading="lazy" />`
            : `<div class="no-cover">No Image</div>`
        }
      </div>

      <div class="genre-page-info">
        <div class="genre-page-meta">
          <span>${source}</span>
          <small>${latest || updated || "Update"}</small>
        </div>

        <h2>${title}</h2>
        <p>${synopsis || "Belum ada sinopsis."}</p>
      </div>
    </article>
  `;
}

function renderGenrePageContent(isLoading = false) {
  if (!grid) return;

  const genre =
    state.genrePageGenre || "";

  const source =
    normalizeSourceName(
      state.appSettings?.source || "all"
    );

  const sourceLabel =
    SOURCE_LABELS[source] || SOURCE_LABELS.all;

  const genreJs =
    escapeHtml(escapeJs(genre));

  const items =
    Array.isArray(state.genrePageItems)
      ? state.genrePageItems
      : [];

  grid.innerHTML = `
    <section class="genre-page">
      ${renderGenrePageTabs(genre)}

      <div class="genre-page-head">
        <div>
          <span>${escapeHtml(sourceLabel)}</span>
          <h1>${escapeHtml(genre || "Genre")}</h1>
          <p>Diurutkan dari update terbaru.</p>
        </div>
      </div>

      <div class="genre-page-list">
        ${
          isLoading
            ? `<div class="empty">Memuat genre...</div>`
            : items.length
              ? items.map(renderGenrePageItem).join("")
              : `<div class="empty">Belum ada manga yang cocok dengan genre dan pengaturan saat ini.</div>`
        }
      </div>

      ${
        !isLoading && state.genrePageHasMore
          ? `<div class="load-more-wrap">
              <button
                id="genreLoadMoreBtn"
                class="load-more-btn"
                type="button"
                onclick="loadGenrePage('${genreJs}', true)">
                Lihat Selengkapnya
              </button>
            </div>`
          : ""
      }
    </section>
  `;

  if (!isLoading) {
    if (state.__genreScrollLeft) {
      scrollActiveChipIntoView(".genre-page-tabs", state.__genreScrollLeft);
      state.__genreScrollLeft = 0;
    } else {
      scrollActiveChipIntoView(".genre-page-tabs");
    }
  }
}

async function loadGenrePage(genre, append = false) {
  setNormalMode();

  const clean =
    cleanGenreName(genre);

  const appendScrollY =
    append ? Math.max(0, Number(window.scrollY || 0)) : 0;

  if (!append) {
    prepareGenreRestore(clean);
  }

  if (!clean) {
    if (statusText) {
      statusText.textContent = "Genre";
    }

    if (grid) {
      grid.innerHTML =
        `<div class="empty">Genre tidak ditemukan.</div>`;
    }

    return;
  }

  if (!append) {
    state.genrePageGenre = clean;
    state.genrePageItems = [];
    state.genrePageOffset = 0;
    state.genrePageTotal = 0;
    state.genrePageHasMore = false;
    renderGenrePageContent(true);
  } else {
    const btn =
      document.getElementById("genreLoadMoreBtn");

    if (btn) {
      btn.disabled = true;
      btn.textContent = "Memuat...";
    }
  }

  if (statusText) {
    statusText.textContent =
      append ? "Memuat lagi..." : `Genre ${clean}`;
  }

  await loadGenresFromIndexJson();

  const activeSource =
    state.appSettings?.source || "all";

  const params = new URLSearchParams({
    source: activeSource,
    q: "",
    mode: "update",
    type: "all",
    genre: clean,
    limit: "30",
    offset: String(state.genrePageOffset || 0)
  });

  try {
    const allStaticItems =
      await fetchStaticHomeItems(activeSource);

    const filteredStaticItems =
      filterStaticHomeItems(allStaticItems, {
        q: "",
        mode: "update",
        type: "all",
        genre: clean
      });

    /*
    | Sama seperti Home: filter setting user dilakukan sebelum pagination
    | supaya jumlah dan urutan genre page tidak bolong.
    */
    const visibleStaticItems =
      filterMangaItemsBySettings(filteredStaticItems);

    const sortedStaticItems =
      sortStaticHomeItems(visibleStaticItems, "update");

    const genreLoadLimit =
      !append && Number(state.__genreRestoreTargetOffset || 0) > 30
        ? Number(state.__genreRestoreTargetOffset || 0)
        : 30;

    const json =
      paginateStaticItems(
        sortedStaticItems,
        genreLoadLimit,
        state.genrePageOffset || 0
      );

    const rawItems =
      Array.isArray(json.data)
        ? json.data
        : [];

    const items =
      rawItems;

    state.genrePageItems =
      append
        ? [...state.genrePageItems, ...items]
        : items;

    state.genrePageOffset =
      Number(json.next_offset || 0);

    state.genrePageTotal =
      Number(json.total || state.genrePageItems.length || 0);

    state.genrePageHasMore =
      Boolean(json.has_more);

    renderGenrePageContent(false);

    if (append && appendScrollY) {
      requestAnimationFrame(() => {
        window.scrollTo({ top: appendScrollY, left: 0, behavior: "auto" });
      });
    }

    if (statusText) {
      statusText.textContent =
        `${state.genrePageItems.length} judul · ${clean}`;
    }

    if (!append && state.__genreRestoreScrollY) {
      restoreWindowScroll(state.__genreRestoreScrollY);
      state.__genreRestoreScrollY = 0;
      state.__genreRestoreTargetOffset = 0;
    }
  } catch (err) {
    if (statusText) {
      statusText.textContent =
        "Gagal memuat genre.";
    }

    if (!append && grid) {
      grid.innerHTML =
        `<div class="empty">${escapeHtml(err.message || "Gagal memuat genre.")}</div>`;
    } else {
      toast(err.message || "Gagal memuat genre.");
      renderGenrePageContent(false);
    }
  }
}

function mergeUniqueGenres(oldGenres, newGenres) {
  const map = new Map();

  [
    ...(Array.isArray(oldGenres) ? oldGenres : []),
    ...(Array.isArray(newGenres) ? newGenres : [])
  ].forEach((genre) => {
    const clean =
      cleanGenreName(genre);

    const key =
      normalizeGenreName(clean);

    if (key && !map.has(key)) {
      map.set(key, clean);
    }
  });

  return Array
    .from(map.values())
    .sort((a, b) => a.localeCompare(b));
}

function updateGenresFromHomeItems(items, source = "all") {
  if (!Array.isArray(items) || !items.length) {
    return;
  }

  const nextGenres = {
    comicazen: [...(state.sourceGenres?.comicazen || [])],
    medusa: [...(state.sourceGenres?.medusa || [])]
  };

  if (source === "comicazen" || source === "medusa") {
    const genres =
      extractGenresFromItems(items);

    nextGenres[source] =
      mergeUniqueGenres(
        nextGenres[source],
        genres
      );
  } else {
    const comicazenItems =
      items.filter((item) =>
        normalizeSourceName(item?.source) === "comicazen"
      );

    const medusaItems =
      items.filter((item) =>
        normalizeSourceName(item?.source) === "medusa"
      );

    nextGenres.comicazen =
      mergeUniqueGenres(
        nextGenres.comicazen,
        extractGenresFromItems(comicazenItems)
      );

    nextGenres.medusa =
      mergeUniqueGenres(
        nextGenres.medusa,
        extractGenresFromItems(medusaItems)
      );
  }

  saveGenreCache(nextGenres);
}

async function fetchGenresFromIndexSource(source) {
  const rows = await fetchStaticIndexSource(source);

  return mergeUniqueGenres(
    [],
    extractGenresFromItems(rows)
  );
}

async function loadGenresFromIndexJson(force = false) {
  if (state.genresLoading && !force) {
    return state.sourceGenres;
  }

  const cached =
    loadGenreCache();

  const cacheAge =
    Date.now() - Number(cached.savedAt || 0);

  const cacheValid =
    cacheAge < 1000 * 60 * 60 * 12;

  if (
    !force &&
    cacheValid &&
    (
      cached.comicazen.length ||
      cached.medusa.length
    )
  ) {
    state.sourceGenres = {
      comicazen: cached.comicazen,
      medusa: cached.medusa
    };

    return state.sourceGenres;
  }

  state.genresLoading = true;

  try {
    const [comicazenGenres, medusaGenres] =
      await Promise.all([
        fetchGenresFromIndexSource("comicazen"),
        fetchGenresFromIndexSource("medusa")
      ]);

    const clean = {
      comicazen: comicazenGenres,
      medusa: medusaGenres
    };

    saveGenreCache(clean);

    return clean;
  } catch (err) {
    console.warn(
      "Gagal mengambil genre dari database:",
      err.message
    );

    return state.sourceGenres;
  } finally {
    state.genresLoading = false;
  }
}

function getAvailableGenres(source) {
  return state.sourceGenres?.[source] || [];
}

function isBlockedGenre(source, genre) {
  const list =
    state.appSettings?.blockedGenres?.[source] || [];

  return list
    .map(normalizeGenreName)
    .includes(normalizeGenreName(genre));
}

function toggleBlockedGenre(source, genre) {
  const settings =
    state.appSettings || defaultAppSettings;

  const current =
    settings.blockedGenres?.[source] || [];

  const normalized =
    normalizeGenreName(genre);

  const exists =
    current
      .map(normalizeGenreName)
      .includes(normalized);

  const nextList =
    exists
      ? current.filter(
          (g) =>
            normalizeGenreName(g) !== normalized
        )
      : [...current, genre];

  saveAppSettings({
    ...settings,
    blockedGenres: {
      ...(settings.blockedGenres || {}),
      [source]: nextList
    }
  });

  loadSettingsPage();
}

function setSettingsSource(source) {
  saveAppSettings({
    ...state.appSettings,
    source
  });

  loadSettingsPage();
}

function resetBlockedGenres(source) {
  saveAppSettings({
    ...state.appSettings,
    blockedGenres: {
      ...(state.appSettings.blockedGenres || {}),
      [source]: []
    }
  });

  loadSettingsPage();
}

function renderSourceSetting() {
  const active =
    state.appSettings?.source || "all";

  const options = [
    "all",
    "comicazen",
    "medusa"
  ];

  return `
    <section class="settings-card">
      <div class="settings-card-head">
        <div>
          <h2>Sumber Manga</h2>
          <p>Pilih sumber yang ingin ditampilkan di beranda.</p>
        </div>
      </div>

      <div class="source-choice">
        ${options.map((source) => `
          <button
            class="${active === source ? "active" : ""}"
            onclick="setSettingsSource('${source}')">
            <span>${SOURCE_LABELS[source]}</span>
          </button>
        `).join("")}
      </div>
    </section>
  `;
}

function renderGenreSetting(source) {
  const genres =
    getAvailableGenres(source);

  const blocked =
    state.appSettings
      ?.blockedGenres?.[source] || [];

  if (!genres.length) {
    return `
      <section class="settings-card">
        <div class="settings-card-head">
          <div>
            <h2>Genre ${SOURCE_LABELS[source]}</h2>
            <p>Sedang mengambil data genre dari database...</p>
          </div>
        </div>
      </section>
    `;
  }

  return `
    <section class="settings-card">
      <div class="settings-card-head">
        <div>
          <h2>Genre ${SOURCE_LABELS[source]}</h2>
          <p>Genre yang dicentang tidak akan tampil di beranda.</p>
        </div>

        <button
          class="settings-mini-btn"
          onclick="resetBlockedGenres('${source}')">
          Reset
        </button>
      </div>

      <div class="genre-check-list">
        ${genres.map((genre) => {
          const active =
            isBlockedGenre(
              source,
              genre
            );

          const safeGenre =
            escapeHtml(genre);

          return `
            <button
              class="genre-check ${active ? "active" : ""}"
              onclick="toggleBlockedGenre('${source}', '${safeGenre}')">
              <span class="fake-check">${active ? "✓" : ""}</span>
              <span>${safeGenre}</span>
            </button>
          `;
        }).join("")}
      </div>

      <p class="settings-note">
        Terblokir: ${blocked.length} genre
      </p>
    </section>
  `;
}

async function loadSettingsPage() {
  setNormalMode();

  if (statusText) {
    statusText.textContent = "Pengaturan";
  }

  if (grid) {
    grid.innerHTML = `
      <section class="settings-page">
        <div class="settings-hero">
          <div>
            <h1>Pengaturan</h1>
            <p>Mengambil genre dari database...</p>
          </div>
          ${icon("spinner")}
        </div>
      </section>
    `;
  }

  await loadGenresFromIndexJson();

  const activeSource =
    state.appSettings?.source || "all";

  const showComicazen =
    activeSource === "all" ||
    activeSource === "comicazen";

  const showMedusa =
    activeSource === "all" ||
    activeSource === "medusa";

  if (statusText) {
    statusText.textContent = "Pengaturan";
  }

  if (grid) {
    grid.innerHTML = `
      <section class="settings-page">
        <div class="settings-hero">
          <div>
            <h1>Pengaturan</h1>
            <p>
              Atur sumber manga dan blok genre
              yang tidak ingin tampil.
            </p>
          </div>

          ${icon("settings")}
        </div>

        ${renderSourceSetting()}

        ${showComicazen ? renderGenreSetting("comicazen") : ""}
        ${showMedusa ? renderGenreSetting("medusa") : ""}

        <section class="settings-card">
          <div class="settings-card-head">
            <div>
              <h2>Catatan</h2>
              <p>
                Daftar genre diambil otomatis dari database lewat API genre.
                Kalau genre belum muncul, pastikan tabel manga_genres sudah terisi.
              </p>
            </div>
          </div>
        </section>
      </section>
    `;
  }
}
/* =========================
   DETAIL
========================= */

function renderDetailMeta(label, value) {
  const clean = String(value || "").trim();

  if (!clean || clean === "-") {
    return "";
  }

  return `
    <div class="detail-meta-item">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(clean)}</strong>
    </div>
  `;
}

function getReadStorageKey(source, mangaSlug) {
  return `comicaso_read_${state.userId}_${source}_${mangaSlug}`;
}

function loadReadChapterMap(source, mangaSlug) {
  try {
    const raw = localStorage.getItem(
      getReadStorageKey(source, mangaSlug)
    );

    const list = JSON.parse(raw || "[]");
    const map = {};

    if (Array.isArray(list)) {
      list.forEach((slug) => {
        if (slug) {
          map[String(slug)] = true;
        }
      });
    }

    return map;
  } catch (err) {
    return {};
  }
}

function markChapterAsRead(source, mangaSlug, chapterSlug) {
  try {
    const key = getReadStorageKey(source, mangaSlug);
    const raw = localStorage.getItem(key);
    const list = JSON.parse(raw || "[]");

    const next = Array.isArray(list)
      ? list.map(String)
      : [];

    if (!next.includes(String(chapterSlug))) {
      next.push(String(chapterSlug));
    }

    localStorage.setItem(key, JSON.stringify(next));
  } catch (err) {}
}

function isChapterRead(chapterSlug) {
  return Boolean(
    state.readChapterMap?.[String(chapterSlug)]
  );
}

function renderChapterList(chapters, source, mangaSlug, mangaTitle, thumbnail) {
  if (!chapters.length) {
    return `<div class="empty">Chapter belum terbaca dari JSON.</div>`;
  }

  return chapters.map((ch) => {
    const rawTitle =
      ch.title ||
      ch.name ||
      ch.chapter_title ||
      ch.slug ||
      "Chapter";

    const chTitle =
      escapeHtml(rawTitle);

    const rawSlug =
      ch.slug ||
      ch.chapter_slug ||
      "";

    const chSlug =
      escapeHtml(rawSlug);

    if (!rawSlug) return "";

    const date =
      formatMangaDate(
        ch.date ||
        ch.updated_at ||
        ch.created_at ||
        ""
      );

    const readClass =
      isChapterRead(rawSlug)
        ? " read"
        : "";

    return `
      <button
        class="chapter-item${readClass}"
        data-chapter-title="${escapeHtml(String(rawTitle).toLowerCase())}"
        onclick="openChapter(
          '${escapeJs(source)}',
          '${escapeJs(mangaSlug)}',
          '${escapeJs(rawSlug)}',
          '${escapeJs(rawTitle)}',
          '${escapeJs(mangaTitle)}',
          '${escapeJs(thumbnail)}'
        )">
        <span>${chTitle}</span>
        ${date ? `<small>${escapeHtml(date)}</small>` : ""}
      </button>
    `;
  }).join("");
}

function filterChapterList(value) {
  const q =
    String(value || "")
      .trim()
      .toLowerCase();

  document
    .querySelectorAll(".chapter-item")
    .forEach((btn) => {
      const title =
        btn.dataset.chapterTitle || "";

      btn.style.display =
        !q || title.includes(q)
          ? ""
          : "none";
    });
}

async function loadMangaDetail(source, slug) {
  if (isRestrictedMedusaContent(source)) {
    renderMedusaLoginGate("detail");
    return;
  }

  if (statusText) {
    statusText.textContent = "Memuat detail...";
  }

  if (grid) {
    grid.innerHTML = "";
  }

  try {
    const m =
      await fetchStaticMangaDetail(source, slug);

    state.currentManga = {
      source,
      slug: m.slug || slug,
      title: m.title || m.alternative || "Untitled",
      thumbnail: m.thumbnail || m.thumb || m.cover || "",
      bookmarked: false,
      lastRead: null,
      chapters: Array.isArray(m.chapters) ? m.chapters : []
    };

    state.readChapterMap =
      loadReadChapterMap(source, state.currentManga.slug);

    trackMangaView(state.currentManga).catch(() => {});

    const title =
      escapeHtml(state.currentManga.title);

    const rawMangaSlug =
      state.currentManga.slug;

    const rawTitle =
      state.currentManga.title;

    const rawThumb =
      state.currentManga.thumbnail;

    const thumb =
      escapeHtml(state.currentManga.thumbnail);

    const synopsis =
      escapeHtml(
        stripHtml(
          m.synopsis ||
          m.description ||
          "Belum ada sinopsis."
        )
      );

    const sourceName =
      escapeHtml(source);

    const sourceRaw =
      source;

    const genres =
      getItemGenres(m);

    let chapters =
      sortChapters(
        state.currentManga.chapters
      );

    const firstChapter =
      chapters[0] || null;

    if (statusText) {
      statusText.textContent = "";
    }

    if (grid) {
      grid.innerHTML = `
        <section class="detail detail-modern">
          <div class="detail-hero">
            <div class="detail-cover">
              ${
                thumb
                  ? `<img src="${thumb}" alt="${title}" />`
                  : `<div class="no-cover">No Image</div>`
              }
            </div>

            <div class="detail-info">
              <div class="detail-badge-row">
                <span class="detail-badge">${sourceName}</span>
                ${m.type ? `<span class="detail-badge soft">${escapeHtml(m.type)}</span>` : ""}
                ${m.status ? `<span class="detail-badge soft">${escapeHtml(m.status)}</span>` : ""}
              </div>

              <h2>${title}</h2>

              ${
                m.alternative
                  ? `<p class="detail-alt">${escapeHtml(m.alternative)}</p>`
                  : ""
              }

              ${
                (m.author || m.artist)
                  ? `<div class="detail-meta-grid">
                      ${renderDetailMeta("Author", m.author)}
                      ${renderDetailMeta("Artist", m.artist)}
                    </div>`
                  : ""
              }
            </div>
          </div>

          <div class="detail-actions">
            ${
              firstChapter
                ? `<button
                    id="continueReadBtn"
                    class="action-btn primary-action"
                    onclick="openChapter(
                      '${escapeJs(sourceRaw)}',
                      '${escapeJs(rawMangaSlug)}',
                      '${escapeJs(firstChapter.slug || firstChapter.chapter_slug || "")}',
                      '${escapeJs(firstChapter.title || firstChapter.name || firstChapter.chapter_title || "Mulai Baca")}',
                      '${escapeJs(rawTitle)}',
                      '${escapeJs(rawThumb)}'
                    )">
                    ${icon("play")}
                    <span>Mulai Baca</span>
                  </button>`
                : ""
            }

            <button
              id="detailBookmarkBtn"
              class="action-btn"
              onclick="toggleBookmark()">
              ${icon("bookmark")}
              <span>Simpan Bookmark</span>
            </button>

            <button
              class="action-btn"
              onclick="shareManga()">
              ${icon("share")}
              <span>Bagikan</span>
            </button>
          </div>

          ${renderWebAdSlot(sourceRaw, "webAdDetail")}

          ${
            genres.length
              ? `<div class="genre-list">
                  ${genres.map((g) => `<span>${escapeHtml(g)}</span>`).join("")}
                </div>`
              : ""
          }

          <div class="synopsis">
            <h3>Sinopsis</h3>
            <p>${synopsis}</p>
          </div>

          <div class="chapter-box">
            <div class="chapter-head">
              <div>
                <h3>Chapter</h3>
                <p>${chapters.length} chapter tersedia</p>
              </div>
            </div>

            <div class="chapter-tools">
              <div class="chapter-search">
                <input
                  type="search"
                  placeholder="Cari chapter..."
                  oninput="filterChapterList(this.value)"
                />
              </div>

              <button
                id="chapterSortBtn"
                class="chapter-sort-btn"
                type="button"
                onclick="toggleChapterSort()"
                title="${state.chapterSort === "desc" ? "Urutan Z-A" : "Urutan A-Z"}"
                aria-label="Balik urutan chapter">
                ${state.chapterSort === "desc" ? (icon("up") || "↑") : (icon("down") || "↓")}
              </button>
            </div>

            <div id="chapterList" class="chapter-list">
              ${renderChapterList(
                chapters,
                sourceRaw,
                rawMangaSlug,
                rawTitle,
                rawThumb
              )}
            </div>
          </div>
        </section>
      `;

      renderWebSourceAd(sourceRaw, "webAdDetail").catch(() => {});
    }

    Promise
      .all([
        getBookmarkStatus(source, state.currentManga.slug),
        getLastReadChapter(source, state.currentManga.slug)
      ])
      .then(([bookmarked, lastRead]) => {
        state.currentManga.bookmarked = bookmarked;
        state.currentManga.lastRead = lastRead;

        updateDetailBookmarkButton();
        updateContinueReadButton();
      })
      .catch(() => {});

  } catch (err) {
    if (statusText) {
      statusText.textContent =
        "Gagal memuat detail.";
    }

    if (grid) {
      grid.innerHTML =
        `<div class="empty">${escapeHtml(err.message || "Gagal memuat detail.")}</div>`;
    }

  } finally {
    hideActionLoading();
  }
}

function refreshChapterListAfterHistory() {
  if (!state.currentManga?.chapters) return;

  const list =
    document.getElementById("chapterList");

  if (!list) return;

  state.readChapterMap =
    loadReadChapterMap(
      state.currentManga.source,
      state.currentManga.slug
    );

  const chapters =
    sortChapters(
      state.currentManga.chapters
    );

  list.innerHTML =
    renderChapterList(
      chapters,
      state.currentManga.source,
      state.currentManga.slug,
      state.currentManga.title,
      state.currentManga.thumbnail
    );
}
function updateDetailBookmarkButton() {
  const btn =
    document.getElementById("detailBookmarkBtn");

  if (!btn || !state.currentManga) {
    return;
  }

  if (!isWebAuthenticated()) {
    btn.classList.remove("danger-action");
    btn.innerHTML = `
      ${icon("profile") || icon("bookmark")}
      <span>Login untuk Bookmark</span>
    `;
    return;
  }

  const bookmarked =
    Boolean(state.currentManga.bookmarked);

  btn.classList.toggle(
    "danger-action",
    bookmarked
  );

  btn.innerHTML = `
    ${icon(bookmarked ? "close" : "bookmark")}
    <span>${bookmarked ? "Hapus Bookmark" : "Simpan Bookmark"}</span>
  `;
}

function setDetailBookmarkLoading(loading) {
  const btn =
    document.getElementById("detailBookmarkBtn");

  if (!btn) return;

  btn.disabled = Boolean(loading);
}

function updateContinueReadButton() {
  const btn =
    document.getElementById("continueReadBtn");

  if (!btn || !state.currentManga) {
    return;
  }

  const lastRead =
    state.currentManga.lastRead;

  if (!lastRead?.chapter_slug) {
    return;
  }

  const title =
    lastRead.chapter_title ||
    lastRead.chapter_slug ||
    "Lanjut Baca";

  btn.setAttribute(
    "onclick",
    `openChapter(
      '${escapeJs(state.currentManga.source)}',
      '${escapeJs(state.currentManga.slug)}',
      '${escapeJs(lastRead.chapter_slug)}',
      '${escapeJs(title)}',
      '${escapeJs(state.currentManga.title)}',
      '${escapeJs(state.currentManga.thumbnail)}'
    )`
  );

  btn.innerHTML = `
    ${icon("play")}
    <span>Lanjut ${escapeHtml(title)}</span>
  `;
}

async function saveBookmark() {
  if (!state.currentManga) {
    toast("Data manga belum siap.");
    return;
  }

  if (!isWebAuthenticated()) {
    requireGoogleLogin();
    return;
  }

  await apiPost("/api/bookmark.php", {
    user_id: state.userId,
    source: state.currentManga.source,
    manga_slug: state.currentManga.slug,
    manga_title: state.currentManga.title,
    thumbnail: state.currentManga.thumbnail
  });

  state.currentManga.bookmarked = true;
  updateDetailBookmarkButton();
}

async function deleteBookmark(source, mangaSlug, silent = false) {
  if (!isWebAuthenticated()) {
    requireGoogleLogin();
    return;
  }

  await apiDelete("/api/bookmark.php", {
    user_id: state.userId,
    source,
    manga_slug: mangaSlug
  });
}

async function toggleBookmark() {
  if (!state.currentManga) {
    toast("Data manga belum siap.");
    return;
  }

  if (!isWebAuthenticated()) {
    requireGoogleLogin();
    return;
  }

  const btn =
    document.getElementById("detailBookmarkBtn");

  try {
    if (btn) {
      setDetailBookmarkLoading(true);
    }

    if (state.currentManga.bookmarked) {
      await deleteBookmark(
        state.currentManga.source,
        state.currentManga.slug,
        true
      );

      state.currentManga.bookmarked = false;
      updateDetailBookmarkButton();
    } else {
      await saveBookmark();
    }

    updateDetailBookmarkButton();

  } catch (err) {
    updateDetailBookmarkButton();
    toast(err.message || "Gagal mengubah bookmark.");
  } finally {
    if (btn) {
      btn.disabled = false;
    }
  }
}

function shareManga() {
  if (!state.currentManga) {
    toast("Data manga belum siap.");
    return;
  }

  const url =
    `${location.origin}/?page=manga&source=${encodeURIComponent(state.currentManga.source)}&slug=${encodeURIComponent(state.currentManga.slug)}`;

  if (navigator.share) {
    navigator.share({
      title: state.currentManga.title,
      text: state.currentManga.title,
      url
    }).catch(() => {});
    return;
  }

  navigator.clipboard?.writeText(url);
  toast("Link detail manga disalin.");
}

/* =========================
   CHAPTER NAV
========================= */

function openChapter(
  source,
  mangaSlug,
  chapterSlug,
  chapterTitle = "",
  mangaTitle = "",
  thumbnail = ""
) {
  if (!source || !mangaSlug || !chapterSlug) {
    return;
  }

  if (!showActionLoading("Memuat...")) {
    return;
  }

  const params =
    new URLSearchParams({
      page: "chapter",
      source,
      manga: mangaSlug,
      chapter: chapterSlug
    });

  if (chapterTitle) {
    params.set("chapter_title", chapterTitle);
  }

  if (mangaTitle) {
    params.set("manga_title", mangaTitle);
  }

  if (thumbnail) {
    params.set("thumbnail", thumbnail);
  }

  window.location.href =
    `/?${params.toString()}`;
}

async function loadReaderNav(
  source,
  mangaSlug,
  chapterSlug
) {
  state.readerNav = {
    prev: null,
    next: null
  };

  try {
    const m =
      await fetchStaticMangaDetail(source, mangaSlug);

    const chapters =
      sortChapters(
        Array.isArray(m.chapters)
          ? m.chapters
          : []
      );

    if (!chapters.length) {
      return state.readerNav;
    }

    const index =
      chapters.findIndex((ch) => {
        const slug =
          String(
            ch.slug ||
            ch.chapter_slug ||
            ""
          );

        return slug === chapterSlug;
      });

    if (index === -1) {
      return state.readerNav;
    }

    const makeNavItem = (ch) => {
      if (!ch) return null;

      return {
        slug:
          ch.slug ||
          ch.chapter_slug ||
          "",

        title:
          ch.title ||
          ch.name ||
          ch.chapter_title ||
          ch.slug ||
          "Chapter"
      };
    };

    state.readerNav.prev =
      makeNavItem(chapters[index - 1]);

    state.readerNav.next =
      makeNavItem(chapters[index + 1]);

    return state.readerNav;

  } catch (err) {
    console.warn(
      "Gagal load reader nav:",
      err.message
    );

    return state.readerNav;
  }
}

function updateReaderFooterNav() {
  const footer =
    document.querySelector(".reader-footer");

  if (!footer) return;

  const prevBtn =
    document.getElementById("readerPrevWrap");

  const nextBtn =
    document.getElementById("readerNextWrap");

  if (prevBtn) {
    prevBtn.outerHTML =
      readerNavButton("prev");
  }

  if (nextBtn) {
    nextBtn.outerHTML =
      readerNavButton("next");
  }
}

/* =========================
   CHAPTER COMMENTS
========================= */

function canUseChapterComments() {
  if (typeof isWebAuthenticated === "function") {
    return isWebAuthenticated();
  }

  return Number(state.userId || 0) > 0;
}

function requireCommentLogin() {
  if (typeof requireGoogleLogin === "function") {
    requireGoogleLogin();
    return;
  }

  toast("Buka dari Telegram untuk komentar.");
}

function getCommentUserPayload() {
  const auth =
    state.authUser || {};

  return {
    user_id: state.userId,
    display_name:
      auth.display_name ||
      state.firstName ||
      state.username ||
      "Pembaca",
    username:
      auth.username ||
      state.username ||
      "",
    avatar_url:
      auth.avatar_url ||
      state.photoUrl ||
      ""
  };
}

function chapterCommentTotal(items) {
  return (Array.isArray(items) ? items : [])
    .reduce(
      (total, item) =>
        total + 1 + (Array.isArray(item.replies) ? item.replies.length : 0),
      0
    );
}

function formatCommentDate(value) {
  const date =
    new Date(value || "");

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toLocaleDateString("id-ID", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function renderCommentAvatar(author = {}) {
  const name =
    author.display_name ||
    author.username ||
    "P";

  const initial =
    escapeHtml(String(name).charAt(0).toUpperCase());

  if (!author.avatar_url) {
    return `<span>${initial}</span>`;
  }

  return `
    <img
      src="${escapeHtml(author.avatar_url)}"
      alt="${escapeHtml(name)}"
      loading="lazy"
      onerror="this.style.display='none'; this.nextElementSibling.style.display='grid';"
    />
    <span style="display:none">${initial}</span>
  `;
}

function renderCommentItem(item, isReply = false) {
  const author =
    item.author || {};

  const name =
    author.display_name ||
    author.username ||
    "Pembaca";

  const nameJs =
    escapeHtml(escapeJs(name));

  const replies =
    Array.isArray(item.replies)
      ? item.replies
      : [];

  const liked =
    Boolean(item.liked_by_me);

  const isVvip =
    userHasVvip(author);

  return `
    <article
      class="chapter-comment ${isReply ? "is-reply" : ""} ${isVvip ? "is-vvip" : ""}"
      data-comment-id="${Number(item.id)}">
      <div class="comment-avatar ${isVvip ? "vvip-avatar" : ""}">
        ${renderCommentAvatar(author)}
      </div>

      <div class="comment-body">
        <div class="comment-bubble">
          <div class="comment-top">
            <strong>${escapeHtml(name)}</strong>
            ${renderVvipBadge(author)}
            <span>${escapeHtml(formatCommentDate(item.created_at))}</span>
          </div>

          <p>${escapeHtml(item.body || "")}</p>
        </div>

        <div class="comment-actions">
          <button
            type="button"
            class="${liked ? "active" : ""}"
            onclick="toggleChapterCommentLike(${Number(item.id)}, ${liked ? "true" : "false"})">
            Suka${item.likes_count ? ` ${Number(item.likes_count)}` : ""}
          </button>

          ${
            isReply
              ? ""
              : `<button
                  type="button"
                  onclick="showCommentReplyForm(${Number(item.id)}, '${nameJs}')">
                  Balas
                </button>`
          }

          ${
            item.owned
              ? `<button
                  type="button"
                  onclick="deleteChapterComment(${Number(item.id)})">
                  Hapus
                </button>`
              : ""
          }
        </div>

        <div id="commentReplySlot-${Number(item.id)}"></div>

        ${
          replies.length
            ? `<div class="comment-replies">
                ${replies.map((reply) => renderCommentItem(reply, true)).join("")}
              </div>`
            : ""
        }
      </div>
    </article>
  `;
}

function renderChapterCommentsShell() {
  const title =
    state.currentChapter?.chapterTitle ||
    "Chapter";

  return `
    <section id="chapterComments" class="chapter-comments">
      <div class="chapter-comments-head">
        <div>
          <h2>Komentar</h2>
          <p>${escapeHtml(title)}</p>
        </div>
        <span id="chapterCommentsCount">0</span>
      </div>

      ${
        canUseChapterComments()
          ? `<div class="comment-form">
              <textarea
                id="chapterCommentInput"
                class="allow-select"
                rows="3"
                maxlength="1000"
                placeholder="Tulis komentar chapter ini..."></textarea>

              <button
                id="chapterCommentSubmit"
                type="button"
                onclick="submitChapterComment()">
                Kirim
              </button>
            </div>`
          : `<div class="comment-login">
              <p>Login dulu untuk ikut komentar.</p>
              <button type="button" onclick="requireCommentLogin()">
                Login
              </button>
            </div>`
      }

      <div id="chapterCommentList" class="comment-list">
        <div class="comment-empty">Memuat komentar...</div>
      </div>
    </section>
  `;
}

async function loadChapterComments() {
  const chapter =
    state.currentChapter;

  if (!chapter?.source || !chapter?.mangaSlug || !chapter?.chapterSlug) {
    return;
  }

  const list =
    document.getElementById("chapterCommentList");

  if (!list) {
    return;
  }

  list.innerHTML =
    `<div class="comment-empty">Memuat komentar...</div>`;

  try {
    const params = new URLSearchParams({
      user_id: String(state.userId || ""),
      source: chapter.source,
      manga: chapter.mangaSlug,
      chapter: chapter.chapterSlug
    });

    const json =
      await apiGet(
        `/api/comments.php?${params.toString()}`
      );

    const items =
      Array.isArray(json.data)
        ? json.data
        : [];

    const count =
      chapterCommentTotal(items);

    const countEl =
      document.getElementById("chapterCommentsCount");

    if (countEl) {
      countEl.textContent = String(count);
    }

    list.innerHTML =
      items.length
        ? items.map((item) => renderCommentItem(item)).join("")
        : `<div class="comment-empty">Belum ada komentar di chapter ini.</div>`;

    scrollToTargetComment();
  } catch (err) {
    list.innerHTML =
      `<div class="comment-empty">${escapeHtml(err.message || "Gagal memuat komentar.")}</div>`;
  }
}

function scrollToTargetComment() {
  const commentId =
    Number(getParams().get("comment") || 0);

  if (!commentId) {
    return;
  }

  const target =
    document.querySelector(`[data-comment-id="${commentId}"]`);

  if (!target) {
    return;
  }

  setTimeout(() => {
    window.ReaderEngine?.hideControls?.();
    target.scrollIntoView({
      behavior: "smooth",
      block: "center"
    });
    target.classList.add("comment-highlight");

    setTimeout(() => {
      target.classList.remove("comment-highlight");
    }, 2200);
  }, 250);
}

function bindReaderCommentControls() {
  const comments =
    document.getElementById("chapterComments");

  if (!comments) {
    return;
  }

  const hideReaderControls = () => {
    window.ReaderEngine?.hideControls?.();
  };

  comments.addEventListener("focusin", hideReaderControls);
  comments.addEventListener("pointerdown", hideReaderControls);
  comments.addEventListener("click", hideReaderControls);

  if (window.__readerCommentObserver) {
    window.__readerCommentObserver.disconnect();
  }

  if ("IntersectionObserver" in window) {
    window.__readerCommentObserver =
      new IntersectionObserver((entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          hideReaderControls();
        }
      }, {
        threshold: 0.03,
        rootMargin: "0px 0px -18% 0px"
      });

    window.__readerCommentObserver.observe(comments);
  }
}

async function submitChapterComment(parentId = 0) {
  if (!canUseChapterComments()) {
    requireCommentLogin();
    return;
  }

  const chapter =
    state.currentChapter;

  const input =
    document.getElementById(
      parentId > 0
        ? `chapterReplyInput-${parentId}`
        : "chapterCommentInput"
    );

  const body =
    String(input?.value || "").trim();

  if (!chapter || !body) {
    toast("Komentar masih kosong.");
    return;
  }

  try {
    await apiPost("/api/comments.php", {
      ...getCommentUserPayload(),
      source: chapter.source,
      manga_slug: chapter.mangaSlug,
      chapter_slug: chapter.chapterSlug,
      manga_title: chapter.mangaTitle || "",
      chapter_title: chapter.chapterTitle || "",
      parent_id: parentId || 0,
      body
    });

    if (input) {
      input.value = "";
    }

    await loadChapterComments();
  } catch (err) {
    toast(err.message || "Gagal kirim komentar.");
  }
}

function showCommentReplyForm(commentId, authorName = "Pembaca") {
  if (!canUseChapterComments()) {
    requireCommentLogin();
    return;
  }

  const slot =
    document.getElementById(`commentReplySlot-${commentId}`);

  if (!slot) {
    return;
  }

  slot.innerHTML = `
    <div class="comment-form comment-reply-form">
      <textarea
        id="chapterReplyInput-${Number(commentId)}"
        class="allow-select"
        rows="2"
        maxlength="1000"
        placeholder="Balas ${escapeHtml(authorName)}..."></textarea>

      <button
        type="button"
        onclick="submitChapterComment(${Number(commentId)})">
        Balas
      </button>
    </div>
  `;

  document
    .getElementById(`chapterReplyInput-${commentId}`)
    ?.focus();

  window.ReaderEngine?.hideControls?.();
}

async function toggleChapterCommentLike(commentId, liked) {
  if (!canUseChapterComments()) {
    requireCommentLogin();
    return;
  }

  try {
    await apiPost("/api/comments.php", {
      ...getCommentUserPayload(),
      action: liked ? "unlike" : "like",
      comment_id: commentId
    });

    await loadChapterComments();
  } catch (err) {
    toast(err.message || "Gagal update like.");
  }
}

async function deleteChapterComment(commentId) {
  if (!canUseChapterComments()) {
    requireCommentLogin();
    return;
  }

  try {
    await apiDelete("/api/comments.php", {
      user_id: state.userId,
      comment_id: commentId
    });

    await loadChapterComments();
  } catch (err) {
    toast(err.message || "Gagal hapus komentar.");
  }
}

/* =========================
   CHAPTER / READER
========================= */

async function loadChapterReader(
  source,
  mangaSlug,
  chapterSlug
) {
  if (isRestrictedMedusaContent(source)) {
    renderMedusaLoginGate("reader");
    return;
  }

  if (statusText) {
    statusText.textContent = "Memuat chapter...";
  }

  if (grid) {
    grid.innerHTML = "";
  }

  try {
    const ch =
      await fetchStaticChapter(source, mangaSlug, chapterSlug);
    const params = getParams();

    const chapterTitleRaw =
      ch.title ||
      params.get("chapter_title") ||
      chapterSlug;

    const mangaTitleRaw =
      params.get("manga_title") ||
      mangaSlug;

    const thumbnailRaw =
      params.get("thumbnail") ||
      "";

    const images =
      Array.isArray(ch.images)
        ? ch.images
        : [];

    state.currentChapter = {
      source,
      mangaSlug,
      chapterSlug,
      mangaTitle: mangaTitleRaw,
      chapterTitle: chapterTitleRaw,
      thumbnail: thumbnailRaw,
      images
    };

    markChapterAsRead(source, mangaSlug, chapterSlug);

    if (statusText) {
      statusText.textContent = "";
    }

    if (!images.length) {
      if (grid) {
        grid.innerHTML =
          `<div class="empty">Gambar chapter tidak ditemukan.</div>`;
      }

      hideActionLoading();
      return;
    }

    const isDownloaded =
      window.ReaderEngine?.hasOfflineChapter?.(
        source,
        mangaSlug,
        chapterSlug
      );

    const downloadUnlocked =
      Boolean(state.subscription?.active);

    const downloadTitle =
      !isWebAuthenticated()
        ? "Login Google untuk download"
        : downloadUnlocked
          ? (isDownloaded ? "Sudah disimpan" : "Download")
          : "Download khusus VVIP";

    if (grid) {
      grid.innerHTML = `
        <section class="reader-shell">
          <header class="reader-header">
            <div class="reader-titlebox">
              <strong>${escapeHtml(mangaTitleRaw)}</strong>
              <span>
                ${escapeHtml(chapterTitleRaw)}
                ·
                <b id="readerProgressText">1/${images.length}</b>
              </span>
            </div>
          </header>

          ${renderWebAdSlot(source, "webAdReader")}

          <div class="reader-images-modern">
            ${images.map((src, i) => {
              const safeSrc = escapeHtml(src);

              return `
                <img
                  class="reader-page"
                  src="${safeSrc}"
                  alt="Page ${i + 1}"
                  loading="${i < 4 ? "eager" : "lazy"}"
                  decoding="async"
                />
              `;
            }).join("")}
          </div>

          ${renderChapterCommentsShell()}

          <div
            id="readerSpeedPanel"
            class="reader-speed-panel">

            <button onclick="readerSpeedMinus()">
              ${icon("minus")}
            </button>

            <span id="readerSpeedText">
              ${window.ReaderEngine?.getSpeed?.() || 1}x
            </span>

            <button onclick="readerSpeedPlus()">
              ${icon("plus")}
            </button>
          </div>

          <aside class="reader-side">
            <button
              onclick="ReaderEngine.scrollTop()"
              aria-label="Ke atas">
              ${icon("up")}
            </button>

            <button
              onclick="ReaderEngine.scrollBottom()"
              aria-label="Ke bawah">
              ${icon("down")}
            </button>

            <button
              id="readerDownloadBtn"
              class="${downloadUnlocked ? "" : "is-premium-locked"}"
              onclick="downloadCurrentChapter()"
              aria-label="Download"
              title="${downloadTitle}">
              ${downloadUnlocked ? (isDownloaded ? icon("check") : icon("download")) : icon("lock")}
            </button>
          </aside>

          <footer class="reader-footer">
            <button onclick="goHomeFresh()" aria-label="Home">
              ${icon("home")}
              <span>Home</span>
            </button>

            <button onclick="openManga('${escapeHtml(source)}', '${escapeHtml(mangaSlug)}')" aria-label="Detail">
              ${icon("menu")}
              <span>Detail</span>
            </button>

            <button
              id="readerAutoBtn"
              onclick="toggleReaderAutoScroll()"
              aria-label="Auto Scroll">
              ${icon("play")}
              <span>Auto</span>
            </button>

            <button
              onclick="toggleSpeedPanel()"
              aria-label="Pengaturan">
              ${icon("settings")}
              <span>Setting</span>
            </button>

            <span id="readerPrevWrap">
              ${readerNavButton("prev")}
            </span>

            <span id="readerNextWrap">
              ${readerNavButton("next")}
            </span>
          </footer>
        </section>
      `;

      renderWebSourceAd(source, "webAdReader").catch(() => {});
    }

    setReaderMode();
    hideActionLoading();

    window.addEventListener(
      "scroll",
      updateReaderProgress,
      { passive: true }
    );

    setTimeout(updateReaderProgress, 250);
    bindReaderCommentControls();
    loadChapterComments().catch(() => {});

    loadReaderNav(
      source,
      mangaSlug,
      chapterSlug
    )
      .then(() => updateReaderFooterNav())
      .catch(() => {});

    trackMangaView({
      source,
      slug: mangaSlug,
      title: mangaTitleRaw,
      thumbnail: thumbnailRaw
    }).catch(() => {});

    saveHistory({
      source,
      mangaSlug,
      chapterSlug,
      chapterTitle: chapterTitleRaw
    }).catch(() => {});

  } catch (err) {
    if (statusText) {
      statusText.textContent =
        "Gagal memuat chapter.";
    }

    if (grid) {
      grid.innerHTML =
        `<div class="empty">${escapeHtml(err.message || "Gagal memuat chapter.")}</div>`;
    }

    hideActionLoading();
  }
}

async function saveHistory({
  source,
  mangaSlug,
  chapterSlug,
  chapterTitle
}) {
  if (!isWebAuthenticated()) {
    return;
  }

  try {
    const params = getParams();

    await apiPost("/api/history.php", {
      user_id: state.userId,
      source,
      manga_slug: mangaSlug,
      chapter_slug: chapterSlug,
      manga_title:
        params.get("manga_title") ||
        mangaSlug,
      chapter_title:
        chapterTitle ||
        chapterSlug,
      thumbnail:
        params.get("thumbnail") ||
        ""
    });

  } catch (err) {
    console.warn(
      "Gagal simpan history:",
      err.message
    );
  }
}
/* =========================
   BOOKMARK PAGE
========================= */

function updateBookmarkPageEmptyState() {
  const count =
    document.querySelectorAll(".bookmark-card").length;

  if (statusText) {
    statusText.textContent = `${count} bookmark`;
  }

  if (count === 0 && grid) {
    grid.innerHTML =
      `<div class="empty">Belum ada bookmark.</div>`;
  }
}

async function removeBookmarkFromPage(source, mangaSlug, button = null) {
  try {
    await deleteBookmark(source, mangaSlug, true);

    button
      ?.closest(".bookmark-card")
      ?.remove();

    updateBookmarkPageEmptyState();
    await loadBookmarkPage();
  } catch (err) {
    toast(err.message || "Gagal hapus bookmark.");
  }
}

async function loadBookmarkPage() {
  setNormalMode();

  if (statusText) {
    statusText.textContent = "Memuat bookmark...";
  }

  if (grid) {
    grid.innerHTML = "";
  }

  if (!isWebAuthenticated()) {
    if (statusText) {
      statusText.textContent = "Bookmark";
    }

    if (grid) {
      grid.innerHTML = `
        <section class="profile-card">
          <div class="profile-avatar">G</div>
          <h2>Login Google</h2>
          <p>Login dulu untuk menyimpan dan melihat bookmark.</p>
          <div class="profile-actions">
            <button
              class="action-btn primary-action"
              onclick="loginWithGoogle()">
              ${icon("profile")}
              <span>Login dengan Google</span>
            </button>
          </div>
        </section>
      `;
    }

    return;
  }

  try {
    const json =
      await apiGet(
        `/api/bookmark.php?user_id=${encodeURIComponent(state.userId)}`
      );

    const items =
      Array.isArray(json.data)
        ? json.data
        : [];

    if (statusText) {
      statusText.textContent =
        `${items.length} bookmark`;
    }

    if (!items.length) {
      if (grid) {
        grid.innerHTML =
          `<div class="empty">Belum ada bookmark.</div>`;
      }
      return;
    }

    if (grid) {
      grid.innerHTML = items.map((item) => {
        const title =
          escapeHtml(
            item.manga_title ||
            item.manga_slug ||
            "Untitled"
          );

        const source =
          escapeHtml(item.source || "");

        const slug =
          escapeHtml(item.manga_slug || "");

        const thumb =
          escapeHtml(item.thumbnail || "");

        const updateDate =
          formatMangaDate(
            item.updated_at_manga ||
            item.updated_at ||
            item.manga_date ||
            ""
          );

        return `
          <article
            class="card bookmark-card"
            onclick="openManga('${source}', '${slug}')">

            <div class="cover">
              ${
                thumb
                  ? `<img
                      src="${thumb}"
                      alt="${title}"
                      loading="lazy"
                      onerror="this.style.display='none'; this.nextElementSibling.style.display='grid';"
                    />`
                  : ""
              }

              <div
                class="no-cover"
                style="${thumb ? "display:none" : ""}">
                No Image
              </div>

              <span class="badge">${source}</span>
            </div>

            <div class="card-body">
              <h2>${title}</h2>
              <p>${updateDate ? "Update " + escapeHtml(updateDate) : "Bookmark"}</p>
            </div>

            <button
              type="button"
              class="card-delete-btn"
              onclick="event.stopPropagation(); removeBookmarkFromPage('${source}', '${slug}', this)"
              aria-label="Hapus bookmark">
              ${icon("close")}
            </button>
          </article>
        `;
      }).join("");
    }

  } catch (err) {
    if (statusText) {
      statusText.textContent =
        "Gagal memuat bookmark.";
    }

    if (grid) {
      grid.innerHTML =
        `<div class="empty">${escapeHtml(err.message || "Gagal memuat bookmark.")}</div>`;
    }
  }
}

/* =========================
   HISTORY PAGE
========================= */

function updateHistoryPageEmptyState() {
  const count =
    document.querySelectorAll(".history-item-wrap").length;

  if (statusText) {
    statusText.textContent = `${count} history`;
  }

  if (count === 0 && grid) {
    grid.innerHTML =
      `<div class="empty">Belum ada history baca.</div>`;
  }
}

async function deleteHistoryItem(source, mangaSlug, chapterSlug, button = null) {
  if (!isWebAuthenticated()) {
    requireGoogleLogin();
    return;
  }

  try {
    await apiDelete("/api/history.php", {
      user_id: state.userId,
      source,
      manga_slug: mangaSlug,
      chapter_slug: chapterSlug
    });

    button
      ?.closest(".history-item-wrap")
      ?.remove();

    updateHistoryPageEmptyState();
    await loadHistoryPage();
  } catch (err) {
    toast(err.message || "Gagal hapus history.");
  }
}

async function clearAllHistory() {
  if (!isWebAuthenticated()) {
    requireGoogleLogin();
    return;
  }

  try {
    await apiDelete("/api/history.php", {
      user_id: state.userId,
      clear_all: 1
    });

    if (grid) {
      grid.innerHTML =
        `<div class="empty">Belum ada history baca.</div>`;
    }

    if (statusText) {
      statusText.textContent = "0 history";
    }

    await loadHistoryPage();
  } catch (err) {
    toast(err.message || "Gagal hapus history.");
  }
}

async function loadHistoryPage() {
  setNormalMode();

  if (statusText) {
    statusText.textContent = "Memuat history...";
  }

  if (grid) {
    grid.innerHTML = "";
  }

  if (!isWebAuthenticated()) {
    if (statusText) {
      statusText.textContent = "History";
    }

    if (grid) {
      grid.innerHTML = `
        <section class="profile-card">
          <div class="profile-avatar">G</div>
          <h2>Login Google</h2>
          <p>Login dulu untuk menyimpan dan melihat history baca.</p>
          <div class="profile-actions">
            <button
              class="action-btn primary-action"
              onclick="loginWithGoogle()">
              ${icon("profile")}
              <span>Login dengan Google</span>
            </button>
          </div>
        </section>
      `;
    }

    return;
  }

  try {
    const json =
      await apiGet(
        `/api/history.php?user_id=${encodeURIComponent(state.userId)}`
      );

    const items =
      Array.isArray(json.data)
        ? json.data
        : [];

    if (statusText) {
      statusText.textContent =
        `${items.length} history`;
    }

    if (!items.length) {
      if (grid) {
        grid.innerHTML =
          `<div class="empty">Belum ada history baca.</div>`;
      }
      return;
    }

    if (grid) {
      grid.innerHTML = `
        <section class="history-page">
          <div class="history-page-head">
            <div>
              <h2>History Baca</h2>
              <p>${items.length} chapter terakhir</p>
            </div>

            <button
              type="button"
              class="settings-mini-btn"
              onclick="clearAllHistory()">
              Hapus Semua
            </button>
          </div>

          <div class="history-list">
            ${items.map((item) => {
              const title =
                escapeHtml(
                  item.manga_title ||
                  item.manga_slug ||
                  "Untitled"
                );

              const chapterTitle =
                escapeHtml(
                  item.chapter_title ||
                  item.chapter_slug ||
                  "Chapter"
                );

              const source =
                escapeHtml(item.source || "");

              const mangaSlug =
                escapeHtml(item.manga_slug || "");

              const chapterSlug =
                escapeHtml(item.chapter_slug || "");

              const thumb =
                escapeHtml(item.thumbnail || "");

              const date =
                formatMangaDate(
                  item.updated_at ||
                  item.created_at ||
                  ""
                );

              return `
                <div class="history-item-wrap">
                  <button
                    class="history-item"
                    onclick="openChapter('${source}', '${mangaSlug}', '${chapterSlug}', '${chapterTitle}', '${title}', '${thumb}')">

                    <div class="history-thumb">
                      ${
                        thumb
                          ? `<img src="${thumb}" alt="${title}" loading="lazy" />`
                          : `<span>No Image</span>`
                      }
                    </div>

                    <div class="history-info">
                      <strong>${title}</strong>
                      <span>${chapterTitle}</span>
                      <small>${source}${date ? " · " + escapeHtml(date) : ""}</small>
                    </div>
                  </button>

                  <button
                    type="button"
                    class="history-delete-btn"
                    onclick="deleteHistoryItem('${source}', '${mangaSlug}', '${chapterSlug}', this)"
                    aria-label="Hapus history">
                    ${icon("close")}
                  </button>
                </div>
              `;
            }).join("")}
          </div>
        </section>
      `;
    }

  } catch (err) {
    if (statusText) {
      statusText.textContent =
        "Gagal memuat history.";
    }

    if (grid) {
      grid.innerHTML =
        `<div class="empty">${escapeHtml(err.message || "Gagal memuat history.")}</div>`;
    }
  }
}
/* =========================
   PROFILE / OFFLINE
========================= */

function openOfflineChapter(key) {
  const item =
    window.ReaderEngine?.getOfflineChapter?.(key);

  if (!item) {
    toast("Data offline tidak ditemukan.");
    return;
  }

  if (!showActionLoading("Memuat...")) {
    return;
  }

  state.currentChapter = item;

  state.readerNav = {
    prev: null,
    next: null
  };

  if (statusText) {
    statusText.textContent = "";
  }

  if (grid) {
    grid.innerHTML = `
      <section class="reader-shell">
        <header class="reader-header">
          <div class="reader-titlebox">
            <strong>${escapeHtml(item.mangaTitle)}</strong>
            <span>
              ${escapeHtml(item.chapterTitle)}
              ·
              <b id="readerProgressText">1/${item.images.length}</b>
            </span>
          </div>
        </header>

        <div class="reader-images-modern">
          ${item.images.map((src, i) => `
            <img
              class="reader-page"
              src="${escapeHtml(src)}"
              alt="Page ${i + 1}"
              loading="${i < 4 ? "eager" : "lazy"}"
              decoding="async"
            />
          `).join("")}
        </div>

        <div
          id="readerSpeedPanel"
          class="reader-speed-panel">

          <button onclick="readerSpeedMinus()">
            ${icon("minus")}
          </button>

          <span id="readerSpeedText">
            ${window.ReaderEngine?.getSpeed?.() || 1}x
          </span>

          <button onclick="readerSpeedPlus()">
            ${icon("plus")}
          </button>
        </div>

        <aside class="reader-side">
          <button onclick="ReaderEngine.scrollTop()" aria-label="Ke atas">
            ${icon("up")}
          </button>

          <button onclick="ReaderEngine.scrollBottom()" aria-label="Ke bawah">
            ${icon("down")}
          </button>
        </aside>

        <footer class="reader-footer">
          <button onclick="goHomeFresh()" aria-label="Home">
            ${icon("home")}
            <span>Home</span>
          </button>

          <button onclick="openManga('${escapeHtml(item.source)}', '${escapeHtml(item.mangaSlug)}')" aria-label="Detail">
            ${icon("menu")}
            <span>Detail</span>
          </button>

          <button
            id="readerAutoBtn"
            onclick="toggleReaderAutoScroll()"
            aria-label="Auto Scroll">
            ${icon("play")}
            <span>Auto</span>
          </button>

          <button
            onclick="toggleSpeedPanel()"
            aria-label="Pengaturan">
            ${icon("settings")}
            <span>Setting</span>
          </button>

          <button
            class="reader-disabled"
            disabled>
            ${icon("prev")}
            <span>Prev</span>
          </button>

          <button
            class="reader-disabled"
            disabled>
            ${icon("next")}
            <span>Next</span>
          </button>
        </footer>
      </section>
    `;
  }

  setReaderMode();
  hideActionLoading();

  window.addEventListener(
    "scroll",
    updateReaderProgress,
    { passive: true }
  );

  setTimeout(updateReaderProgress, 250);
}

function deleteOfflineChapter(key) {
  window.ReaderEngine?.deleteOfflineChapter?.(key);
  toast("Download offline dihapus.");
  loadProfilePage();
}

function renderOfflineDownloads() {
  if (!state.subscription?.active) {
    const adminUrl =
      state.botConfig?.admin_contact_url ||
      state.authUser?.bot?.admin_contact_url ||
      "https://t.me/Comicaso_id";

    const message =
      `Saya mau langganan bebas iklan dan download. Email: ${state.authUser?.email || ""}`;

    return `
      <div class="offline-box offline-locked">
        <h3>Download Offline</h3>
        <p>Download khusus member VVIP.</p>
        <button
          type="button"
          class="action-btn primary-action"
          onclick="handleSubscribeRequest('${escapeJs(message)}', '${escapeJs(adminUrl)}')">
          ${icon("lock")}
          <span>Langganan VVIP</span>
        </button>
      </div>
    `;
  }

  const list =
    window.ReaderEngine?.getOfflineList?.() || [];

  if (!list.length) {
    return `
      <div class="offline-box">
        <h3>Download Offline</h3>
        <p>Belum ada manga yang disimpan offline.</p>
      </div>
    `;
  }

  return `
    <div class="offline-box">
      <h3>Download Offline</h3>

      <div class="offline-list">
        ${list.map((item) => `
          <div class="offline-item">
            <div class="offline-thumb">
              ${
                item.thumbnail
                  ? `<img src="${escapeHtml(item.thumbnail)}" alt="${escapeHtml(item.mangaTitle)}" />`
                  : `<span>No Image</span>`
              }
            </div>

            <div class="offline-info">
              <strong>${escapeHtml(item.mangaTitle)}</strong>
              <span>${escapeHtml(item.chapterTitle)}</span>
              <small>
                ${escapeHtml(item.source)}
                ·
                ${item.images.length} halaman
              </small>
            </div>

            <div class="offline-actions">
              <button onclick="openOfflineChapter('${escapeHtml(item.key)}')">
                ${icon("play")}
              </button>

              <button onclick="deleteOfflineChapter('${escapeHtml(item.key)}')">
                ${icon("close")}
              </button>
            </div>
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function notificationText(item) {
  if (item?.body) {
    return item.body;
  }

  if (item?.type === "like") {
    return "menyukai komentar Anda";
  }

  if (item?.type === "mention") {
    return "menyebut Anda di komentar";
  }

  return "membalas komentar Anda";
}

function renderNotificationBadge() {
  const count =
    Math.max(0, Number(state.notificationCount || 0));

  return `
    <i
      class="profile-notification-badge"
      data-notification-badge
      ${count > 0 ? "" : "hidden"}>
      ${count > 99 ? "99+" : count}
    </i>
  `;
}

function renderNotificationItem(item) {
  const actor =
    item.actor || {};

  const actorName =
    actor.display_name ||
    actor.username ||
    "Pembaca";

  const source =
    item.source || "";

  const mangaSlug =
    item.manga_slug || "";

  const chapterSlug =
    item.chapter_slug || "";

  const commentId =
    Number(item.comment_id || 0);

  return `
    <button
      type="button"
      class="notification-item ${item.is_read ? "" : "unread"}"
      onclick="openNotificationItem(${Number(item.id)}, '${escapeHtml(escapeJs(source))}', '${escapeHtml(escapeJs(mangaSlug))}', '${escapeHtml(escapeJs(chapterSlug))}', ${commentId})">
      <div class="notification-avatar">
        ${renderProfileAvatar(actor.avatar_url || "", actorName)}
      </div>

      <div class="notification-info">
        <strong>${escapeHtml(actorName)}</strong>
        <span>${escapeHtml(notificationText(item))}</span>
        <small>
          ${escapeHtml(item.title || "Chapter")}
          ·
          ${escapeHtml(formatCommentDate(item.created_at))}
        </small>
      </div>

      <i class="notification-dot" aria-hidden="true"></i>
    </button>
  `;
}

async function openNotificationItem(
  notificationId,
  source,
  mangaSlug,
  chapterSlug,
  commentId = 0
) {
  if (!source || !mangaSlug || !chapterSlug) {
    toast("Target notifikasi tidak lengkap.");
    return;
  }

  if (!showActionLoading("Membuka chapter...")) {
    return;
  }

  try {
    await markNotificationRead(notificationId);
  } catch (err) {
    console.warn("Gagal update notifikasi:", err.message);
  }

  const params =
    new URLSearchParams({
      page: "chapter",
      source,
      manga: mangaSlug,
      chapter: chapterSlug
    });

  if (commentId) {
    params.set("comment", String(commentId));
  }

  window.location.href =
    `/?${params.toString()}`;
}

async function handleMarkAllNotificationsRead() {
  if (!showActionLoading("Menyimpan...")) {
    return;
  }

  try {
    await markAllNotificationsRead();
    await loadNotificationsPage();
    toast("Notifikasi ditandai dibaca.");
  } catch (err) {
    toast(err.message || "Gagal update notifikasi.");
  } finally {
    hideActionLoading();
  }
}

async function loadNotificationsPage() {
  setNormalMode();

  if (statusText) {
    statusText.textContent = "Notifikasi";
  }

  if (!canUseNotifications()) {
    if (grid) {
      grid.innerHTML = `
        <section class="profile-card notification-page">
          <div class="notification-head">
            <div>
              <h2>Notifikasi</h2>
              <p>Login Google dulu.</p>
            </div>
          </div>

          <button
            type="button"
            class="action-btn primary-action"
            onclick="loginWithGoogle()">
            ${icon("profile")}
            <span>Login dengan Google</span>
          </button>
        </section>
      `;
    }

    return;
  }

  if (grid) {
    grid.innerHTML = `
      <section class="profile-card notification-page">
        <div class="notification-head">
          <div>
            <h2>Notifikasi</h2>
            <p>Balasan dan suka komentar</p>
          </div>

          <button
            type="button"
            onclick="handleMarkAllNotificationsRead()">
            Tandai dibaca
          </button>
        </div>

        <div id="notificationList" class="notification-list">
          <div class="comment-empty">Memuat notifikasi...</div>
        </div>
      </section>
    `;
  }

  const list =
    document.getElementById("notificationList");

  try {
    const json =
      await getNotifications();

    const items =
      Array.isArray(json.data)
        ? json.data
        : [];

    if (list) {
      list.innerHTML =
        items.length
          ? items.map(renderNotificationItem).join("")
          : `<div class="comment-empty">Belum ada notifikasi.</div>`;
    }
  } catch (err) {
    if (list) {
      list.innerHTML =
        `<div class="comment-empty">${escapeHtml(err.message || "Gagal memuat notifikasi.")}</div>`;
    }
  }
}

function renderProfileAvatar(photoUrl, name) {
  const initial =
    escapeHtml(String(name || "U").charAt(0).toUpperCase());

  if (!photoUrl) {
    return `<span>${initial}</span>`;
  }

  return `
    <img
      src="${escapeHtml(photoUrl)}"
      alt="${escapeHtml(name || "Profile")}"
      loading="lazy"
      onerror="this.style.display='none'; this.nextElementSibling.style.display='grid';"
    />
    <span style="display:none">${initial}</span>
  `;
}

function subscriptionLabel(subscription) {
  if (subscription?.active) {
    return `Aktif sampai ${subscription.expires_at || "-"} (${subscription.days_left || 0} hari)`;
  }

  return "Belum aktif";
}

function renderSubscriptionCard({ platform, identity, subscription, bot }) {
  const active =
    Boolean(subscription?.active);

  const label =
    platform === "web"
      ? "Web bebas iklan"
      : "Mini app bebas iklan";

  const adminUrl =
    bot?.admin_contact_url ||
    state.botConfig?.admin_contact_url ||
    "https://t.me/Comicaso_id";

  const message =
    platform === "web"
      ? `Saya mau langganan bebas iklan. Email: ${identity}`
      : `Saya mau langganan bebas iklan. Telegram ID: ${identity}`;

  return `
    <section class="profile-card subscription-card">
      <div class="profile-head">
        <div class="profile-avatar subscription-avatar${active ? " vvip-avatar" : ""}">
          ${active ? icon("check") : icon("bell")}
        </div>
        <div class="profile-main">
          <h2>${escapeHtml(label)}</h2>
          <p>${escapeHtml(subscriptionLabel(subscription))}</p>
          ${
            active
              ? `<small>Status: ${escapeHtml(subscription?.status || "active")}</small>`
              : `<small>Langganan aktif akan menghilangkan iklan.</small>`
          }
        </div>
      </div>

      ${
        active
          ? ""
          : `<div class="profile-actions">
              <button
                class="action-btn primary-action"
                onclick="handleSubscribeRequest('${escapeJs(message)}', '${escapeJs(adminUrl)}')">
                ${icon("share")}
                <span>Langganan Bebas Iklan</span>
              </button>
            </div>`
      }
    </section>
  `;
}

async function handleSubscribeRequest(message, adminUrl) {
  await copyText(message, "Pesan langganan disalin.");
  if (adminUrl) {
    window.open(adminUrl, "_blank", "noopener");
  }
}

function renderProfileContent(user = null) {
  setNormalMode();

  const currentTheme =
    window.ThemeManager?.get?.() ||
    "dark";

  const nextThemeText =
    currentTheme === "dark"
      ? "Light Mode"
      : "Dark Mode";

  const themeIcon =
    currentTheme === "dark"
      ? icon("sun")
      : icon("moon");

  const isLoggedIn =
    Boolean(user?.id);

  const displayName =
    user?.display_name ||
    user?.email ||
    state.firstName ||
    "Web Reader";

  const subtitle =
    isLoggedIn
      ? (user.email || "Login Google aktif")
      : "Login untuk menyimpan bookmark dan history ke akun Google.";

  const smallText =
    isLoggedIn
      ? "Login Google aktif"
      : `Guest ID: ${state.webGuestUserId || state.userId}`;

  const avatarUrl =
    isLoggedIn
      ? (user.avatar_url || "")
      : "";

  const subscription =
    user?.subscription ||
    state.subscription ||
    null;

  const bot =
    user?.bot ||
    state.botConfig ||
    {};

  if (statusText) {
    statusText.textContent = "Profile";
  }

  if (grid) {
    grid.innerHTML = `
      <section class="profile-card profile-social">
        <div class="profile-head">
          <div class="profile-avatar">
            ${renderProfileAvatar(avatarUrl, displayName)}
          </div>

          <div class="profile-main">
            <h2>${escapeHtml(displayName)}</h2>
            <p>${escapeHtml(subtitle)}</p>

            ${
              isLoggedIn && user.email
                ? `<button
                    type="button"
                    class="profile-copy-row"
                    onclick="copyText('${escapeJs(user.email)}', 'Email disalin.')">
                    <span>Email</span>
                    <strong>${escapeHtml(user.email)}</strong>
                    ${icon("check")}
                  </button>`
                : `<small>${escapeHtml(smallText)}</small>`
            }
          </div>
        </div>

        <div class="profile-actions">
          ${
            isLoggedIn
              ? `<button
                  class="action-btn"
                  onclick="goPage('/?page=notifications', 'Memuat...')">
                  ${icon("bell")}
                  <span>Notifikasi</span>
                  ${renderNotificationBadge()}
                </button>`
              : ""
          }

          ${
            isLoggedIn
              ? `<button
                  class="action-btn"
                  onclick="handleLogoutGoogle()">
                  ${icon("close")}
                  <span>Logout</span>
                </button>`
              : `<button
                  class="action-btn primary-action"
                  onclick="loginWithGoogle()">
                  ${icon("profile")}
                  <span>Login dengan Google</span>
                </button>`
          }

          <button
            class="action-btn ${isLoggedIn ? "primary-action" : ""}"
            onclick="toggleAppTheme()">
            ${themeIcon}
            <span>${nextThemeText}</span>
          </button>
        </div>
      </section>

      ${
        isLoggedIn
          ? renderSubscriptionCard({
              platform: "web",
              identity: user.email || "",
              subscription,
              bot
            })
          : ""
      }

      ${isLoggedIn ? renderOfflineDownloads() : ""}
    `;
  }
}

async function loadProfilePage() {
  renderProfileContent(state.authUser);

  try {
    if (await hydrateAuthUser()) {
      renderProfileContent(state.authUser);
      return;
    }

    renderProfileContent(null);
  } catch (err) {
    toast(err.message || "Gagal memuat profile.");
  }
}

async function handleLogoutGoogle() {
  try {
    await logoutGoogle();
    toast("Logout berhasil.");
    loadProfilePage();
  } catch (err) {
    toast(err.message || "Gagal logout.");
  }
}

function toggleAppTheme() {
  const next =
    window.ThemeManager?.toggle?.() ||
    "dark";

  toast(
    next === "light"
      ? "Light Mode aktif."
      : "Dark Mode aktif."
  );

  loadProfilePage();
}
