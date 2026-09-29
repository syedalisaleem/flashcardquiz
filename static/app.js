"use strict";

/* ---------------------------------------------------------------- state */

const S = {
  decks: [],
  deckId: null,
  deck: null,
  view: "dashboard",
  tab: "generate",
  job: null,
  filter: "",
  search: "",
  review: [],
  reviewIdx: 0,
  quizIdx: 0,
  quizAnswered: {},
  quizScore: { answered: 0, correct: 0 },
  model: null,
  modalOpen: false,
  focus: false,
  pomo: { total: 25 * 60, left: 25 * 60, running: false, timer: null },
  goalSec: 0,
  streak: 0,
};

const $ = (id) => document.getElementById(id);

/* ---------------------------------------------------------------- platform */

function detectPlatform() {
  const p = new URLSearchParams(location.search).get("platform");
  if (p === "android" || p === "desktop") return p;
  return /FlashcardQuiz/i.test(navigator.userAgent) ? "android" : "web";
}

const PLATFORM = detectPlatform();
document.body.dataset.platform = PLATFORM;

const SAMPLE = `Cell Biology — Lecture 01

The mitochondrion is the powerhouse of the cell, responsible for producing ATP via cellular respiration.

DNA replication: Helicase unwinds the double helix by breaking hydrogen bonds between base pairs. DNA Polymerase synthesizes the new complementary strand in the 5' to 3' direction. RNA Primase lays down short RNA primers to initiate synthesis. DNA Ligase seals nicks in the sugar-phosphate backbone.

Protein synthesis happens at the ribosome, where mRNA codons are translated into amino acid chains.

The Golgi apparatus modifies, sorts, and packages proteins for secretion.

The nucleus stores genetic material as chromatin and coordinates cell activities.`;

/* ---------------------------------------------------------------- helpers */

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers:
      options.body && !(options.body instanceof FormData)
        ? { "Content-Type": "application/json" }
        : {},
    ...options,
  });
  if (!res.ok) {
    let detail = res.statusText;
    try { detail = (await res.json()).detail || detail; } catch (_) {}
    throw new Error(detail);
  }
  return res.json();
}

function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function clozeHtml(text) {
  return esc(text).replace(/\{\{c\d+::(.*?)\}\}/g, '<mark>$1</mark>');
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function toast(msg, kind = "") {
  const box = document.createElement("div");
  box.className = "toast " + kind;
  box.textContent = msg;
  $("toasts").appendChild(box);
  setTimeout(() => {
    box.classList.add("out");
    setTimeout(() => box.remove(), 280);
  }, 3000);
}

function fmtSize(bytes) {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / 1048576).toFixed(1) + " MB";
}

function dueCount(deck) {
  if (!deck || !deck.cards) return 0;
  return deck.cards.filter((c) => c.due <= today()).length;
}

function totalDue() {
  return S.decks.reduce((sum, d) => sum + (d.due || 0), 0);
}

/* ---------------------------------------------------------------- streak + goal */

function touchStreak() {
  const t = today();
  let data = { date: t, count: 1 };
  try { data = JSON.parse(localStorage.getItem("fq_streak") || "null") || data; } catch (_) {}
  if (data.date === t) {
    S.streak = data.count;
    return;
  }
  const prev = new Date(data.date);
  const now = new Date(t);
  const dayMs = 86400000;
  const gap = Math.round((now - prev) / dayMs);
  data = { date: t, count: gap === 1 ? (data.count || 0) + 1 : 1 };
  try { localStorage.setItem("fq_streak", JSON.stringify(data)); } catch (_) {}
  S.streak = data.count;
}

function loadStreak() {
  try {
    const data = JSON.parse(localStorage.getItem("fq_streak") || "null");
    if (!data) { S.streak = 0; return; }
    const gap = Math.round((new Date(today()) - new Date(data.date)) / 86400000);
    S.streak = gap <= 1 ? data.count : 0;
  } catch (_) { S.streak = 0; }
  const el = $("streakCount");
  if (el) el.textContent = S.streak;
}

function goalKey() { return "fq_goal_" + today(); }

function loadGoal() {
  try { S.goalSec = parseInt(localStorage.getItem(goalKey()) || "0", 10) || 0; } catch (_) { S.goalSec = 0; }
}

function addGoalSec(sec) {
  S.goalSec += sec;
  try { localStorage.setItem(goalKey(), String(S.goalSec)); } catch (_) {}
  renderGoal();
}

/* ---------------------------------------------------------------- modal */

function openModal(title, bodyHtml) {
  const root = document.createElement("div");
  root.className = "modal-overlay";
  root.innerHTML = `<div class="modal"><h2>${esc(title)}</h2>${bodyHtml}</div>`;
  $("modalRoot").appendChild(root);
  S.modalOpen = true;
  const close = () => {
    if (!root.isConnected) return;
    S.modalOpen = false;
    root.remove();
  };
  root.addEventListener("click", (e) => { if (e.target === root) close(); });
  return { root, close, $: (sel) => root.querySelector(sel) };
}

function confirmModal(title, message, confirmLabel = "Confirm") {
  return new Promise((resolve) => {
    const m = openModal(
      title,
      `<p class="muted" style="margin:0">${esc(message)}</p>
       <div class="modal-actions">
         <button data-cancel class="btn-ghost">Cancel</button>
         <button class="btn-primary" data-confirm>${esc(confirmLabel)}</button>
       </div>`
    );
    m.$("[data-cancel]").onclick = () => { m.close(); resolve(false); };
    m.$("[data-confirm]").onclick = () => { m.close(); resolve(true); };
  });
}

/* ---------------------------------------------------------------- prefs */

const PALETTES = {
  teal:  { accent: "#7CD5C8" },
  sage:  { accent: "#9BAE8B" },
  deep:  { accent: "#3DB8A8" },
  warm:  { accent: "#C4A882" },
  rose:  { accent: "#C47A8A" },
};

function loadPrefs() {
  try { return Object.assign({ theme: "light", accent: "teal", size: "normal" }, JSON.parse(localStorage.getItem("fq_prefs") || "{}")); }
  catch (_) { return { theme: "light", accent: "teal", size: "normal" }; }
}

function savePrefs(prefs) {
  try { localStorage.setItem("fq_prefs", JSON.stringify(prefs)); } catch (_) {}
}

function mediaDark() {
  return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

function applyPrefs() {
  const prefs = loadPrefs();
  const theme = prefs.theme === "auto" ? (mediaDark() ? "dark" : "light") : prefs.theme;
  document.documentElement.dataset.theme = theme;
  const p = PALETTES[prefs.accent] || PALETTES.teal;
  document.documentElement.style.setProperty("--accent", p.accent);
  for (const cls of ["fs-compact", "fs-large"]) document.documentElement.classList.remove(cls);
  if (prefs.size !== "normal") document.documentElement.classList.add(`fs-${prefs.size}`);
  return prefs;
}

function openPrefs() {
  const prefs = loadPrefs();
  const tpl = $("prefsTpl");
  const m = openModal("Settings", tpl ? tpl.innerHTML : "");
  const row = (sel, key, onChange) => {
    const els = m.root.querySelectorAll(sel);
    els.forEach((b) => b.classList.toggle("on", b.dataset[key] === String(prefs[key])));
    els.forEach((b) => b.addEventListener("click", () => {
      prefs[key] = b.dataset[key];
      savePrefs(prefs);
      els.forEach((x) => x.classList.toggle("on", x === b));
      onChange && onChange();
    }));
  };
  row('[data-theme]', "theme", applyPrefs);
  row('[data-accent]', "accent", applyPrefs);
  row('[data-size]', "size", applyPrefs);
}

/* ---------------------------------------------------------------- init */

async function init() {
  applyPrefs();
  const mq = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)");
  if (mq && mq.addEventListener) mq.addEventListener("change", () => { if (loadPrefs().theme === "auto") applyPrefs(); });
  loadStreak();
  loadGoal();
  try {
    S.model = await api("/api/settings");
    renderModelInfo();
    await loadModels();
  } catch (_) {}
  try {
    await refreshDecks();
    if (S.decks.length) {
      S.deckId = S.decks[0].id;
      resetStudyState();
      await loadDeck();
    }
  } catch (_) {}
  renderAll();
  renderDashboard();
  switchView("dashboard");
  bindEvents();
  initDragDrop();
}

async function refreshDecks() {
  S.decks = await api("/api/decks");
}

async function loadDeck() {
  S.deck = await api(`/api/decks/${S.deckId}`);
  renderAll();
  renderDashboard();
}

function resetStudyState() {
  S.quizAnswered = {};
  S.quizIdx = 0;
  S.quizScore = { answered: 0, correct: 0 };
  S.review = [];
  S.reviewIdx = 0;
}

async function selectDeck(id) {
  S.deckId = id;
  resetStudyState();
  await loadDeck();
  if (S.view === "library") renderCards();
  if (S.view === "analytics") renderStats();
}

/* ---------------------------------------------------------------- views */

function switchView(view) {
  S.view = view;
  document.querySelectorAll(".sidenav-item").forEach((b) => {
    b.classList.toggle("active", b.dataset.view === view);
  });
  document.querySelectorAll(".view").forEach((v) => {
    v.classList.toggle("hidden", v.id !== "view-" + view);
  });
  closeSidenav();
  if (view === "dashboard") renderDashboard();
  if (view === "library") renderCards();
  if (view === "analytics") renderStats();
  if (view === "study") {
    if (S.tab === "review") renderReview();
    if (S.tab === "quiz") renderQuiz();
    if (S.tab === "generate") loadModels();
  }
}

function switchTab(tab) {
  S.tab = tab;
  document.querySelectorAll("#tabs button").forEach((b) => {
    b.classList.toggle("active", b.dataset.tab === tab);
  });
  document.querySelectorAll(".tab").forEach((t) => {
    t.classList.toggle("hidden", t.id !== "tab-" + tab);
  });
  if (tab === "review") renderReview();
  if (tab === "quiz") renderQuiz();
  if (tab === "generate") loadModels();
}

/* ---------------------------------------------------------------- focus mode */

function enterFocus() {
  S.focus = true;
  document.body.classList.add("focus-mode");
  if (!$("focusExit")) {
    const btn = document.createElement("button");
    btn.id = "focusExit";
    btn.className = "focus-exit";
    btn.title = "Exit focus mode";
    btn.setAttribute("aria-label", "Exit focus mode");
    btn.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>';
    btn.onclick = exitFocus;
    document.body.appendChild(btn);
  }
}

function exitFocus() {
  S.focus = false;
  document.body.classList.remove("focus-mode");
  const btn = $("focusExit");
  if (btn) btn.remove();
}

/* ---------------------------------------------------------------- render */

function renderAll() {
  renderSidebar();
  renderHeader();
  renderCards();
  if (S.view === "study" && S.tab === "review") renderReview();
  if (S.view === "study" && S.tab === "quiz") renderQuiz();
  renderStats();
  renderNavBadges();
}

function renderNavBadges() {
  const due = totalDue();
  const nb = $("navDueBadge");
  if (nb) {
    nb.textContent = due || "";
    nb.classList.toggle("hidden", !due);
  }
  const cards = S.deck ? S.deck.cards.length : 0;
  const cb = $("navCardsBadge");
  if (cb) {
    cb.textContent = cards || "";
    cb.classList.toggle("hidden", !cards);
  }
}

function renderSidebar() {
  const list = $("deckList");
  if (!list) return;
  const q = S.search.toLowerCase();
  list.innerHTML = "";
  const decks = S.decks.filter((d) => !q || d.name.toLowerCase().includes(q));
  if (!decks.length) {
    list.innerHTML = '<li class="upcoming-empty" style="padding:10px 6px">' + (q ? "No decks match your search." : "No decks yet — create one.") + "</li>";
    return;
  }
  for (const d of decks) {
    const li = document.createElement("li");
    li.className = "deck-item" + (d.id === S.deckId ? " active" : "");
    li.innerHTML = `
      <span class="deck-item-name">${esc(d.name)}</span>
      <span class="deck-item-count" title="${d.cards} cards">${d.cards}${d.due ? " · " + d.due + " due" : ""}</span>`;
    li.onclick = () => selectDeck(d.id);
    list.appendChild(li);
  }
}

function renderHeader() {
  const title = $("deckTitle");
  if (title) title.textContent = S.deck ? S.deck.name : "\u2014";
  const cards = S.deck ? S.deck.cards.length : 0;
  const due = dueCount(S.deck);
  const mcqs = S.deck ? S.deck.mcqs.length : 0;
  const cardsBadge = $("cardsBadge");
  if (cardsBadge) cardsBadge.textContent = cards || "";
  const dueBadge = $("dueBadge");
  if (dueBadge) dueBadge.textContent = due || "";
  const quizBadge = $("quizBadge");
  if (quizBadge) quizBadge.textContent = mcqs || "";
  const exportBtn = $("exportBtn");
  if (exportBtn) exportBtn.disabled = cards === 0;
  const libName = $("libDeckName");
  if (libName) libName.textContent = S.deck ? S.deck.name : "\u2014";
  const anaName = $("anaDeckName");
  if (anaName) anaName.textContent = S.deck ? S.deck.name : "\u2014";
}

function renderModelInfo() {
  if (!S.model) return;
  const el = $("modelInfo");
  if (el) el.innerHTML = `<b>${esc(S.model.model)}</b>${S.model.mock ? '<span class="mock-tag">MOCK</span>' : ""}`;
}

async function loadModels() {
  const sel = $("modelSelect");
  if (!sel) return;
  try {
    const models = await api("/api/models");
    const cur = JSON.stringify({ base_url: S.model.base_url, model: S.model.model });
    sel.innerHTML = "";
    for (const m of models) {
      const opt = document.createElement("option");
      opt.value = JSON.stringify({ base_url: m.base_url, model: m.model });
      opt.textContent = m.label + (m.base_url.includes("127.0.0.1") ? " (local)" : "");
      if (opt.value === cur) opt.selected = true;
      sel.appendChild(opt);
    }
    sel.disabled = false;
  } catch (_) { sel.disabled = true; }
}

async function onModelChange() {
  const sel = $("modelSelect");
  if (!sel.value) return;
  const pick = JSON.parse(sel.value);
  try {
    S.model = await api("/api/settings", { method: "PATCH", body: JSON.stringify(pick) });
    renderModelInfo();
    toast(`Model switched to ${pick.model}`, "ok");
  } catch (err) {
    toast("Could not switch model: " + err.message, "err");
    await loadModels();
  }
}

/* ---------------------------------------------------------------- dashboard */

function renderDashboard() {
  const h = new Date().getHours();
  const part = h < 5 ? "night" : h < 12 ? "morning" : h < 18 ? "afternoon" : "evening";
  const g = $("dashGreeting");
  if (g) g.textContent = `Good ${part}`;
  const t = $("dashTitle");
  const sub = $("dashSub");
  const due = totalDue();
  if (due > 0) {
    if (t) t.textContent = `${due} card${due === 1 ? "" : "s"} due today`;
    if (sub) sub.textContent = "A short review keeps the streak alive.";
  } else if (S.decks.length) {
    if (t) t.textContent = "All caught up!";
    if (sub) sub.textContent = "Generate new cards or quiz yourself on a deck.";
  } else {
    if (t) t.textContent = "Welcome — let's begin";
    if (sub) sub.textContent = "Create your first deck to start studying.";
  }

  renderDashDecks();
  renderGoal();
  renderUpcoming();
  renderMiniStats();
}

function renderDashDecks() {
  const box = $("dashDecks");
  if (!box) return;
  const q = S.search.toLowerCase();
  let decks = S.decks.slice().sort((a, b) => (b.due || 0) - (a.due || 0));
  if (q) decks = decks.filter((d) => d.name.toLowerCase().includes(q));
  if (!decks.length) {
    box.innerHTML = `
      <div class="dash-empty-illust">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="14" height="16" rx="2"/><path d="M7 9h6M7 13h6"/><path d="M17 3h2a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-2"/></svg>
        <span>${q ? "No decks match your search." : "No decks yet — hit New deck to get started."}</span>
      </div>`;
    return;
  }
  box.innerHTML = "";
  decks.slice(0, 5).forEach((d, i) => {
    const el = document.createElement("div");
    el.className = "dash-deck animate-in";
    el.style.animationDelay = `${i * 40}ms`;
    el.innerHTML = `
      <div class="dash-deck-icon">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
      </div>
      <div class="dash-deck-info">
        <div class="dash-deck-name">${esc(d.name)}</div>
        <div class="dash-deck-meta">${d.cards} cards · ${d.mcqs || 0} questions</div>
      </div>
      <span class="dash-deck-due ${d.due ? "" : "zero"}">${d.due ? d.due + " due" : "clear"}</span>`;
    el.onclick = () => {
      selectDeck(d.id).then(() => {
        switchView("study");
        switchTab(d.due ? "review" : "generate");
      });
    };
    box.appendChild(el);
  });
}

function renderGoal() {
  const GOAL = 60 * 60;
  const mins = Math.floor(S.goalSec / 60);
  const ring = $("goalRingFill");
  const num = $("goalDone");
  const cap = $("goalCaption");
  if (num) num.textContent = mins;
  if (ring) {
    const pct = Math.min(1, S.goalSec / GOAL);
    ring.style.strokeDashoffset = String(314 - 314 * pct);
    ring.style.stroke = pct >= 1 ? "var(--green)" : "var(--accent)";
  }
  if (cap) {
    const left = Math.max(0, 60 - mins);
    cap.textContent = S.goalSec === 0
      ? "Start a review session to track today's goal."
      : left > 0 ? `${left} min left to hit today's goal.` : "Goal reached — great work!";
  }
}

function renderUpcoming() {
  const list = $("upcomingList");
  if (!list) return;
  const withDue = S.decks.filter((d) => d.due > 0).sort((a, b) => b.due - a.due);
  if (!withDue.length) {
    list.innerHTML = '<li class="upcoming-empty">Nothing scheduled — you\'re all caught up.</li>';
    return;
  }
  list.innerHTML = withDue.slice(0, 5).map((d) => `
    <li class="upcoming-item">
      <span class="upcoming-dot"></span>
      <strong>${esc(d.name)}</strong>
      <span class="when">${d.due} due</span>
    </li>`).join("");
}

function renderMiniStats() {
  const set = (id, v) => { const el = $(id); if (el) el.textContent = v; };
  let cards = 0, due = 0, mastered = 0;
  for (const d of S.decks) {
    cards += d.cards || 0;
    due += d.due || 0;
  }
  if (S.deck) {
    mastered = S.deck.cards.filter((c) => (c.reps || 0) >= 3).length;
  }
  set("miniTotalCards", cards);
  set("miniDueToday", due);
  set("miniMastered", mastered);
}

/* ---------------------------------------------------------------- generate */

function setGenBusy(busy) {
  const b = $("generateBtn");
  b.disabled = busy;
  b.classList.toggle("busy", busy);
  if (busy) {
    if (!b.dataset.idle) b.dataset.idle = b.textContent || "Generate";
    b.innerHTML = "Generating&hellip;";
  } else {
    b.innerHTML = b.dataset.idle
      ? b.dataset.idle
      : '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="margin-right:6px"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg> Generate';
  }
}

async function startGenerate() {
  if (!S.deckId) return toast("Create a deck first.", "err");
  const source = $("sourceText").value.trim();
  if (!source) return toast("Add source material first (paste notes or upload a file).", "err");
  const numCards = Math.min(60, Math.max(1, parseInt($("numCards").value, 10) || 10));
  const numMcqs = Math.min(30, Math.max(1, parseInt($("numMcqs").value, 10) || 3));
  const tags = $("tags").value.split(",").map((t) => t.trim()).filter(Boolean);
  setGenBusy(true);
  setProgress(true, "Starting generation...");
  touchStreak();
  try {
    const res = await api(`/api/decks/${S.deckId}/generate`, {
      method: "POST",
      body: JSON.stringify({
        source_text: source, flashcards: true, mcqs: true,
        num_cards: numCards, num_mcqs: numMcqs, tags,
      }),
    });
    S.job = res.job_id;
    toast("Generation started.", "ok");
    pollJob();
  } catch (err) {
    setProgress(false);
    toast("Error: " + err.message, "err");
    setGenBusy(false);
  }
}

function setProgress(visible, text) {
  $("genProgress").classList.toggle("hidden", !visible);
  if (text !== undefined) $("genStatus").textContent = text;
}

async function pollJob() {
  if (!S.job) return;
  let job;
  try {
    job = await api(`/api/jobs/${S.job}`);
  } catch (_) {
    setProgress(false); S.job = null; setGenBusy(false); return;
  }
  const total = job.target_cards + job.target_mcqs;
  const done = job.cards + job.mcqs;
  const fill = $("genFill");
  fill.classList.toggle("indeterminate", job.status === "running" && done === 0);
  fill.style.width = total ? Math.round((done / total) * 100) + "%" : "0%";

  if (job.cards || job.mcqs) {
    try {
      S.deck = await api(`/api/decks/${S.deckId}`);
      renderSidebar(); renderHeader(); renderNavBadges();
      if (S.view === "library") renderCards();
      renderDashboard();
    } catch (_) {}
  }

  if (job.status === "running") {
    setProgress(true, `Generating... ${job.cards}/${job.target_cards} cards, ${job.mcqs}/${job.target_mcqs} questions`);
    setTimeout(pollJob, 1600);
    return;
  }
  if (job.status === "paused") {
    const when = job.reset_ms ? new Date(job.reset_ms).toLocaleTimeString() : "soon";
    const mins = Math.max(1, Math.round(job.wait_s / 60));
    setProgress(true, `Rate limit hit \u2014 resuming at ${when} (~${mins} min).`);
    setTimeout(pollJob, 15000);
    return;
  }
  setProgress(false);
  if (job.status === "error") {
    toast("Generation failed: " + (job.error || "unknown error"), "err");
  } else {
    toast(`Done: ${job.cards} cards, ${job.mcqs} questions added.`, "ok");
  }
  S.job = null;
  setGenBusy(false);
  await refreshDecks();
  await loadDeck();
}

/* ---------------------------------------------------------------- cards */

function renderCards() {
  const grid = $("cardGrid");
  if (!grid) return;
  const q = (S.filter || "").toLowerCase();
  const cards = (S.deck ? S.deck.cards : []).filter((c) => {
    if (!q) return true;
    return ((c.front || c.text) + " " + (c.back || "")).toLowerCase().includes(q);
  });
  const count = $("cardCount");
  if (count) count.textContent = `${cards.length} of ${S.deck ? S.deck.cards.length : 0}`;
  const empty = $("cardsEmpty");
  if (empty) empty.classList.toggle("hidden", cards.length > 0);
  grid.innerHTML = "";
  cards.forEach((card, i) => {
    const el = cardEl(card);
    el.style.animationDelay = `${Math.min(i * 30, 300)}ms`;
    el.classList.add("animate-in");
    grid.appendChild(el);
  });
}

function cardEl(card) {
  const wrap = document.createElement("div");
  wrap.className = "qcard";
  const isCloze = card.type === "Cloze";
  const front = isCloze ? clozeHtml(card.text) : esc(card.front);
  const back = esc(card.back || (isCloze ? "Cloze card \u2014 hidden terms are revealed when imported into Anki." : ""));
  wrap.innerHTML = `
    <div class="qcard-inner">
      <div class="qcard-face front">
        <div class="qcard-type">${esc(card.type)}</div>
        <div class="qcard-f">${front || '<span style="opacity:.4">(empty)</span>'}</div>
        <div class="qcard-tags">
          ${(card.tags || []).slice(0, 3).map((t) => `<span class="qcard-tag">#${esc(t)}</span>`).join("")}
        </div>
        <div class="qcard-srs"><span>${card.source ? esc(card.source) : ""}</span><span>${card.due <= today() ? "due" : ""}</span></div>
      </div>
      <div class="qcard-face back">
        <div class="qcard-type">Answer</div>
        <div class="qcard-f">${back || '<span style="opacity:.4">No answer</span>'}</div>
        <div class="qcard-srs"><span>${card.source ? esc(card.source) : ""}</span></div>
      </div>
    </div>
    <button class="qcard-x" title="Delete card" aria-label="Delete card">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
    </button>`;
  wrap.querySelector(".qcard-inner").onclick = (e) => {
    if (e.target.closest(".qcard-x")) return;
    wrap.classList.toggle("flipped");
  };
  wrap.querySelector(".qcard-face.front").ondblclick = () => editCardModal(card);
  wrap.querySelector(".qcard-x").onclick = (e) => { e.stopPropagation(); deleteCard(card); };
  return wrap;
}

function editCardModal(card) {
  const isCloze = card.type === "Cloze";
  const m = openModal(
    "Edit card",
    `<label class="muted" style="display:block;margin-bottom:4px">Front / Cloze text</label>
     <textarea id="editFront" rows="3">${esc(isCloze ? card.text : card.front)}</textarea>
     ${isCloze ? "" : '<label class="muted" style="display:block;margin-top:8px;margin-bottom:4px">Back</label>'}
     ${isCloze ? "" : `<textarea id="editBack" rows="2">${esc(card.back)}</textarea>`}
     <div class="modal-actions">
       <button data-cancel class="btn-ghost">Cancel</button>
       <button class="btn-primary" data-save>Save</button>
     </div>`
  );
  m.$("[data-save]").onclick = async () => {
    const body = isCloze
      ? { text: m.$("#editFront").value }
      : { front: m.$("#editFront").value, back: m.$("#editBack").value };
    try {
      await api(`/api/decks/${S.deckId}/cards/${card.id}`, { method: "PATCH", body: JSON.stringify(body) });
      m.close(); await loadDeck(); toast("Card updated.", "ok");
    } catch (err) { toast("Error: " + err.message, "err"); }
  };
  m.$("[data-cancel]").onclick = () => m.close();
}

async function deleteCard(card) {
  const ok = await confirmModal("Delete card", `"${(card.front || card.text).slice(0, 60)}..." will be permanently removed.`, "Delete");
  if (!ok) return;
  await api(`/api/decks/${S.deckId}/cards/${card.id}`, { method: "DELETE" });
  await loadDeck(); toast("Card deleted.", "ok");
}

/* ---------------------------------------------------------------- review */

function pomoRender() {
  const mins = Math.floor(S.pomo.left / 60);
  const secs = S.pomo.left % 60;
  return `${mins}:${String(secs).padStart(2, "0")}`;
}

function pomoToggle() {
  S.pomo.running = !S.pomo.running;
  if (S.pomo.running) {
    S.pomo.timer = setInterval(() => {
      S.pomo.left--;
      if (S.pomo.left <= 0) {
        clearInterval(S.pomo.timer);
        S.pomo.running = false;
        S.pomo.left = S.pomo.total;
        toast("Pomodoro break! Stretch for a minute.", "ok");
      }
      const chip = $("pomoTime");
      if (chip) chip.textContent = pomoRender();
    }, 1000);
  } else if (S.pomo.timer) {
    clearInterval(S.pomo.timer);
  }
  const chip = $("pomoChip");
  if (chip) chip.classList.toggle("running", S.pomo.running);
  const btn = $("pomoBtn");
  if (btn) btn.textContent = S.pomo.running ? "Pause" : "Start";
}

async function renderReview() {
  const area = $("reviewArea");
  if (!area) return;
  const dueN = dueCount(S.deck);
  const dueEl = $("reviewDueCount");
  if (dueEl) dueEl.textContent = dueN;

  if (!S.review.length) {
    const due = S.deck ? S.deck.cards.filter((c) => c.due <= today()) : [];
    if (!due.length) {
      area.innerHTML = `
        <div class="review-done">
          <div class="done-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><path d="M22 4 12 14.01l-3-3"/></svg>
          </div>
          <div class="empty-title">All caught up</div>
          <p class="muted">No cards due today. Generate more or come back tomorrow.</p>
        </div>`;
      return;
    }
    S.review = due; S.reviewIdx = 0;
  }

  const total = S.review.length;
  const idx = Math.min(S.reviewIdx, total - 1);
  const card = S.review[idx];
  const isCloze = card.type === "Cloze";
  const front = isCloze ? clozeHtml(card.text) : esc(card.front);
  const back = esc(card.back || "");

  const pills = Array.from({ length: Math.min(total, 30) }, (_, i) =>
    `<span class="card-pill ${i < idx ? "done" : i === idx ? "current" : ""}"></span>`
  ).join("");

  area.innerHTML = `
    <div class="card-progress-pills">${pills}</div>
    <div class="review-card-wrap">
      <div class="review-card" id="reviewFlip">
        <div class="review-face front">
          <span class="review-label">Question</span>
          <div class="review-text">${front}</div>
          <span class="review-hint">Click card to reveal answer</span>
          ${card.source ? `<span class="review-source">${esc(card.source)}</span>` : ""}
        </div>
        <div class="review-face back">
          <span class="review-label">Answer</span>
          <div class="review-text">${back}</div>
          ${card.source ? `<span class="review-source">${esc(card.source)}</span>` : ""}
        </div>
      </div>
    </div>
    <div class="toolbar" style="justify-content:center;margin-bottom:14px">
      <span class="pomodoro-chip" id="pomoChip">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M9 2h6"/></svg>
        <span id="pomoTime">${pomoRender()}</span>
        <button class="pomo-btn" id="pomoBtn" type="button">${S.pomo.running ? "Pause" : "Start"}</button>
      </span>
      <span class="muted sm">Card ${idx + 1} of ${total} due &middot; press 1&ndash;4</span>
    </div>
    <div class="rating-row" id="qualityBtns">
      <button class="rating-btn again" data-q="1">Again<span class="ivl">&lt;10m</span></button>
      <button class="rating-btn hard" data-q="3">Hard<span class="ivl">2d</span></button>
      <button class="rating-btn good" data-q="4">Good<span class="ivl">4d</span></button>
      <button class="rating-btn easy" data-q="5">Easy<span class="ivl">7d</span></button>
    </div>
    <div id="reviewResult" class="quiz-explain" style="display:none"></div>`;

  $("reviewFlip").onclick = () => $("reviewFlip").classList.toggle("flipped");
  $("pomoBtn").onclick = (e) => { e.stopPropagation(); pomoToggle(); };
  area.querySelector("#qualityBtns").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-q]");
    if (btn) submitReview(card, parseInt(btn.dataset.q, 10));
  });
}

async function submitReview(card, quality) {
  if (!S.review.includes(card)) return;
  const labels = { 1: "Again", 3: "Hard", 4: "Good", 5: "Easy" };
  try {
    const res = await api("/api/review", {
      method: "POST",
      body: JSON.stringify({ deck_id: S.deckId, card_id: card.id, quality }),
    });
    touchStreak();
    addGoalSec(60);
    S.review = S.review.filter((c) => c !== card);
    const result = $("reviewResult");
    if (result) {
      result.style.display = "";
      result.innerHTML = `<strong>${labels[quality]}</strong> — next review in ${res.interval} day${res.interval === 1 ? "" : "s"} (ease ${res.ease.toFixed(2)})`;
    }
    if (S.review.length) {
      setTimeout(() => { S.reviewIdx = 0; renderReview(); }, 700);
    } else {
      setTimeout(() => {
        S.review = []; S.reviewIdx = 0;
        renderReview();
        toast("Review session complete!", "ok");
        refreshDecks().then(() => { renderSidebar(); renderNavBadges(); renderDashboard(); });
        loadDeck();
      }, 900);
    }
  } catch (err) { toast("Error: " + err.message, "err"); }
}

/* ---------------------------------------------------------------- quiz */

function renderQuiz() {
  const area = $("quizArea");
  if (!area) return;
  const mcqs = S.deck ? S.deck.mcqs : [];
  const scoreEl = $("quizSessionScore");
  if (scoreEl) {
    scoreEl.textContent = S.quizScore.answered
      ? `${S.quizScore.correct}/${S.quizScore.answered} correct (${Math.round((S.quizScore.correct / S.quizScore.answered) * 100)}%)`
      : "";
  }
  if (!mcqs.length) {
    area.innerHTML = `
      <div class="quiz-start">
        <svg class="quiz-start-art" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>
        <h3>No questions yet</h3>
        <p>Generate MCQs on the Generate tab, then test yourself here.</p>
      </div>`;
    return;
  }
  S.quizIdx = Math.min(S.quizIdx, mcqs.length - 1);
  const m = mcqs[S.quizIdx];
  const chosen = S.quizAnswered[m.id];
  const answered = chosen !== undefined;
  const pct = Math.round(((S.quizIdx + 1) / mcqs.length) * 100);

  let html = `
    <div class="quiz-progress-row">
      <span class="muted sm">Q${S.quizIdx + 1} / ${mcqs.length}</span>
      <div class="quiz-progress-bar"><div class="quiz-progress-fill" style="width:${pct}%"></div></div>
      ${m.answered ? `<span class="quiz-score-pill">${Math.round((m.correct / m.answered) * 100)}% this Q</span>` : ""}
    </div>
    <div class="quiz-q">${esc(m.question)}</div>
    <div class="quiz-opts">`;
  const keys = ["1", "2", "3", "4"];
  m.options.forEach((opt, i) => {
    let cls = "quiz-opt";
    if (answered) {
      if (i === m.correct_index) cls += " correct";
      else if (i === chosen) cls += " wrong";
      else cls += " locked";
    }
    html += `<button class="${cls}" data-choice="${i}"><span class="opt-key">${keys[i]}</span>${esc(opt)}</button>`;
  });
  const expl = answered
    ? (chosen === m.correct_index ? m.explanation : (m.distractor_explanations[String(chosen)] || m.explanation))
    : "";
  html += `</div>
      ${answered ? `<div class="quiz-explain">${esc(expl)}</div>` : ""}
      <div class="toolbar" style="margin:16px 0 0">
        <button id="quizPrev" class="btn-ghost btn-sm">&larr; Prev</button>
        <button id="quizNext" class="btn-ghost btn-sm">Next &rarr;</button>
        <span class="spacer"></span>
        <button id="quizSkip" class="btn-ghost btn-sm">${answered ? "Next" : "Skip"}</button>
      </div>
    <div class="quiz-list" style="margin-top:20px">
      <div class="quiz-list-title">All questions</div>
      ${mcqs.map((q, i) => {
        const acc = q.answered ? Math.round((q.correct / q.answered) * 100) + "%" : "\u2014";
        const accCls = !q.answered ? "acc-none" : q.correct / q.answered >= 0.6 ? "acc-good" : "acc-bad";
        return `<div class="quiz-entry ${i === S.quizIdx ? "active" : ""}" data-i="${i}">
          <span style="font-family:var(--font-mono);font-weight:600">${i + 1}</span>
          <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(q.question)}</span>
          <span class="acc ${accCls}">${acc}</span>
          <button class="del" data-del="${q.id}">Del</button>
        </div>`;
      }).join("")}
    </div>`;

  area.innerHTML = html;
  area.querySelectorAll(".quiz-opt").forEach((btn) => {
    btn.onclick = () => answerQuiz(m, parseInt(btn.dataset.choice, 10));
  });
  area.querySelector("#quizPrev").onclick = () => { S.quizIdx = (S.quizIdx - 1 + mcqs.length) % mcqs.length; renderQuiz(); };
  area.querySelector("#quizNext").onclick = () => { S.quizIdx = (S.quizIdx + 1) % mcqs.length; renderQuiz(); };
  area.querySelector("#quizSkip").onclick = () => { S.quizIdx = (S.quizIdx + 1) % mcqs.length; renderQuiz(); };
  area.querySelectorAll(".quiz-entry").forEach((entry) => {
    entry.onclick = (e) => {
      if (e.target.classList.contains("del")) return;
      S.quizIdx = parseInt(entry.dataset.i, 10); renderQuiz();
    };
  });
  area.querySelectorAll(".quiz-entry .del").forEach((btn) => {
    btn.onclick = async () => {
      const ok = await confirmModal("Delete question", "This question will be removed.", "Delete");
      if (!ok) return;
      await api(`/api/decks/${S.deckId}/mcqs/${btn.dataset.del}`, { method: "DELETE" });
      await loadDeck(); toast("Question deleted.", "ok");
    };
  });
}

async function answerQuiz(m, choice) {
  if (S.quizAnswered[m.id] !== undefined) return;
  try {
    const res = await api(`/api/decks/${S.deckId}/quiz/answer`, {
      method: "POST",
      body: JSON.stringify({ mcq_id: m.id, choice }),
    });
    S.quizAnswered[m.id] = choice;
    S.quizScore.answered++;
    if (res.correct) S.quizScore.correct++;
    m.answered = (m.answered || 0) + 1;
    m.correct = (m.correct || 0) + (res.correct ? 1 : 0);
    touchStreak();
    addGoalSec(45);
    renderQuiz();
    toast(res.correct ? "Correct!" : "Not quite.", res.correct ? "ok" : "err");
  } catch (err) { toast("Error: " + err.message, "err"); }
}

/* ---------------------------------------------------------------- stats */

function renderStats() {
  const area = $("statsArea");
  if (!area) return;
  if (!S.deck) { area.innerHTML = '<p class="muted">Create or select a deck to see stats.</p>'; return; }

  const cards = S.deck.cards;
  const mcqs = S.deck.mcqs;
  const totalCards = cards.length;
  const dueToday = dueCount(S.deck);
  const mastered = cards.filter((c) => (c.reps || 0) >= 3).length;
  const young = cards.filter((c) => (c.reps || 0) < 3 && c.due > today()).length;
  const learning = cards.filter((c) => (c.reps || 0) > 0 && c.due <= today()).length;
  const totalMcqs = mcqs.length;
  const avgAccuracy = totalMcqs > 0
    ? Math.round(mcqs.reduce((sum, m) => sum + (m.answered ? (m.correct / m.answered) : 0), 0) / totalMcqs * 100)
    : 0;
  const masteryRate = totalCards > 0 ? Math.round(mastered / totalCards * 100) : 0;
  const dueRate = totalCards > 0 ? Math.round(dueToday / totalCards * 100) : 0;

  const tile = (val, lbl, delay = 0) => `
    <div class="stat-tile animate-in" style="animation-delay:${delay}ms">
      <span class="val">${val}</span>
      <span class="lbl">${lbl}</span>
    </div>`;

  area.innerHTML = `
    ${tile(totalCards, "Total cards", 0)}
    ${tile(mastered, "Mastered", 50)}
    ${tile(dueToday, "Due today", 100)}
    ${tile(avgAccuracy + "%", "Quiz accuracy", 150)}
    ${tile(totalMcqs, "Questions", 200)}
    ${tile(masteryRate + "%", "Mastery rate", 250)}
    <div class="srs-bars" style="grid-column:1/-1">
      <div class="srs-bar-row">
        <span class="srs-bar-label">Learning</span>
        <div class="srs-bar-track"><div class="srs-bar-fill" style="width:${totalCards ? Math.round((learning / totalCards) * 100) : 0}%"></div></div>
        <span class="srs-bar-count">${learning}</span>
      </div>
      <div class="srs-bar-row">
        <span class="srs-bar-label">Due</span>
        <div class="srs-bar-track"><div class="srs-bar-fill" style="width:${dueRate}%"></div></div>
        <span class="srs-bar-count">${dueToday}</span>
      </div>
      <div class="srs-bar-row">
        <span class="srs-bar-label">Young</span>
        <div class="srs-bar-track"><div class="srs-bar-fill" style="width:${totalCards ? Math.round((young / totalCards) * 100) : 0}%"></div></div>
        <span class="srs-bar-count">${young}</span>
      </div>
      <div class="srs-bar-row">
        <span class="srs-bar-label">Mastered</span>
        <div class="srs-bar-track"><div class="srs-bar-fill" style="width:${masteryRate}%"></div></div>
        <span class="srs-bar-count">${mastered}</span>
      </div>
    </div>`;
}

/* ---------------------------------------------------------------- decks */

function createDeck() {
  const m = openModal(
    "New deck",
    `<input type="text" id="deckName" placeholder="e.g. Lecture 02 \u2014 Genetics" style="width:100%" />
     <div class="modal-actions">
       <button data-cancel class="btn-ghost">Cancel</button>
       <button class="btn-primary" data-create>Create</button>
     </div>`
  );
  const createBtn = m.$("[data-create]");
  const doCreate = async () => {
    const name = m.$("#deckName").value.trim();
    if (!name) { m.$("#deckName").focus(); return; }
    if (createBtn.disabled) return;
    createBtn.disabled = true;
    createBtn.innerHTML = '<span class="spin-inline"></span>Creating&hellip;';
    try {
      const deck = await api("/api/decks", { method: "POST", body: JSON.stringify({ name }) });
      m.close(); await refreshDecks(); await selectDeck(deck.id);
      switchView("study");
      switchTab("generate");
      toast(`Deck "${name}" created.`, "ok");
    } catch (err) {
      toast("Error: " + err.message, "err");
      createBtn.disabled = false;
      createBtn.textContent = "Create";
    }
  };
  m.$("[data-create]").onclick = doCreate;
  m.$("[data-cancel]").onclick = () => m.close();
  m.$("#deckName").onkeydown = (e) => { if (e.key === "Enter") doCreate(); };
  m.$("#deckName").focus();
}

async function deleteDeck() {
  if (!S.deck) return;
  const ok = await confirmModal("Delete deck", `Deck "${S.deck.name}" and all its cards and questions will be permanently deleted.`, "Delete");
  if (!ok) return;
  await api(`/api/decks/${S.deckId}`, { method: "DELETE" });
  await refreshDecks();
  S.deckId = S.decks.length ? S.decks[0].id : null;
  if (S.deckId) await selectDeck(S.deckId);
  else { S.deck = null; resetStudyState(); renderAll(); renderDashboard(); }
  toast("Deck deleted.", "ok");
}

/* ---------------------------------------------------------------- export */

async function exportDeck() {
  if (!S.deck || !S.deck.cards.length) return;
  try {
    const res = await fetch(`/api/decks/${S.deckId}/export`);
    if (!res.ok) throw new Error((await res.json().catch(() => ({}))).detail || "export failed");
    const blob = await res.blob();
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = (S.deck.name || "deck").replace(/[^a-zA-Z0-9 _-]/g, "").replace(/\s+/g, "_") + ".apkg";
    a.click();
    URL.revokeObjectURL(a.href);
    toast("Deck exported \u2014 import the .apkg in Anki.", "ok");
  } catch (err) { toast("Error: " + err.message, "err"); }
}

/* ---------------------------------------------------------------- drag & drop */

function initDragDrop() {
  const zone = $("dropZone");
  const fileInput = $("fileInput");
  if (!zone || !fileInput) return;

  zone.addEventListener("click", (e) => {
    if (e.target.tagName !== "INPUT" && e.target.tagName !== "LABEL") fileInput.click();
  });

  zone.addEventListener("dragover", (e) => { e.preventDefault(); zone.classList.add("drag"); });
  zone.addEventListener("dragleave", () => zone.classList.remove("drag"));
  zone.addEventListener("drop", (e) => {
    e.preventDefault();
    zone.classList.remove("drag");
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  });

  fileInput.addEventListener("change", (e) => {
    const file = e.target.files[0];
    if (file) handleFile(file);
    e.target.value = "";
  });

  const removeBtn = $("fileRemove");
  if (removeBtn) removeBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    $("filePreview").classList.add("hidden");
    $("sourceText").value = "";
    $("fileStatus").textContent = "";
    $("fileStatus").className = "status";
  });
}

function handleFile(file) {
  const preview = $("filePreview");
  $("fileName").textContent = file.name;
  $("fileSize").textContent = fmtSize(file.size);
  preview.classList.remove("hidden");

  const fd = new FormData();
  fd.append("file", file);
  fd.append("use_ocr", String($("ocrToggle").checked));
  $("fileStatus").innerHTML = '<span class="spin-inline"></span>Reading file&hellip;';
  $("fileStatus").className = "status";

  api("/api/upload", { method: "POST", body: fd })
    .then((res) => {
      $("sourceText").value = res.text;
      const ocrNote = res.ocr ? " (OCR)" : "";
      $("fileStatus").textContent = `Loaded ${file.name}${ocrNote} (${res.text.length.toLocaleString()} chars).`;
      $("fileStatus").className = "status ok";
      toast("Source material loaded." + ocrNote, "ok");
    })
    .catch((err) => {
      $("fileStatus").textContent = "Error: " + err.message;
      $("fileStatus").className = "status err";
    });
}

/* ---------------------------------------------------------------- sidenav */

function closeSidenav() {
  const nav = $("sidenav");
  const backdrop = $("sidebarBackdrop");
  if (nav) nav.classList.remove("open");
  if (backdrop) backdrop.classList.remove("on");
}

function toggleSidenav() {
  const nav = $("sidenav");
  const backdrop = $("sidebarBackdrop");
  if (!nav) return;
  const open = !nav.classList.contains("open");
  nav.classList.toggle("open", open);
  if (backdrop) backdrop.classList.toggle("on", open);
}

/* ---------------------------------------------------------------- events */

function bindEvents() {
  if (PLATFORM !== "web") {
    const chip = $("platformChip");
    if (chip) {
      chip.textContent = PLATFORM === "android" ? "Android" : "Desktop";
      chip.hidden = false;
    }
  }

  const menuBtn = $("menuBtn");
  if (menuBtn) menuBtn.onclick = toggleSidenav;
  const backdrop = $("sidebarBackdrop");
  if (backdrop) backdrop.onclick = closeSidenav;

  document.querySelectorAll(".sidenav-item").forEach((b) => {
    b.onclick = () => switchView(b.dataset.view);
  });

  document.querySelectorAll("#tabs button").forEach((b) => {
    b.onclick = () => switchTab(b.dataset.tab);
  });

  const genBtn = $("generateBtn");
  if (genBtn) genBtn.onclick = startGenerate;
  const src = $("sourceText");
  if (src) src.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") { e.preventDefault(); startGenerate(); }
  });
  const modelSel = $("modelSelect");
  if (modelSel) modelSel.onchange = onModelChange;
  const exportBtn = $("exportBtn");
  if (exportBtn) exportBtn.onclick = exportDeck;
  const prefs = $("prefsBtn");
  if (prefs) prefs.onclick = openPrefs;
  const themeBtn = $("themeToggleApp");
  if (themeBtn) themeBtn.onclick = () => {
    const cur = loadPrefs();
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    cur.theme = next;
    savePrefs(cur);
    applyPrefs();
  };
  const newDeck = $("newDeckBtn");
  if (newDeck) newDeck.onclick = createDeck;
  const delDeck = $("deleteDeckBtn");
  if (delDeck) delDeck.onclick = deleteDeck;
  const sample = $("loadSample");
  if (sample) sample.onclick = () => { $("sourceText").value = SAMPLE; toast("Sample notes loaded.", "ok"); };
  const filter = $("cardFilter");
  if (filter) filter.oninput = (e) => { S.filter = e.target.value; renderCards(); };

  const search = $("globalSearch");
  if (search) search.oninput = (e) => {
    S.search = e.target.value.trim();
    renderSidebar();
    if (S.view === "dashboard") renderDashDecks();
  };
  if (search) search.onkeydown = (e) => {
    if (e.key === "Enter" && S.search) switchView("study");
  };

  const dashResume = $("dashResumeBtn");
  if (dashResume) dashResume.onclick = () => {
    switchView("study");
    switchTab("review");
    enterFocus();
  };
  const dashNew = $("dashNewDeckBtn");
  if (dashNew) dashNew.onclick = createDeck;
  const dashAll = $("dashViewAllDecks");
  if (dashAll) dashAll.onclick = () => switchView("study");

  document.querySelectorAll(".dash-action").forEach((btn) => {
    btn.onclick = () => {
      const v = btn.dataset.goto;
      switchView(v);
      if (btn.dataset.tab) switchTab(btn.dataset.tab);
    };
  });

  document.addEventListener("keydown", (e) => {
    if (S.modalOpen || e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.target.matches("input, textarea, select")) return;
    if (e.key === "Escape" && S.focus) { exitFocus(); return; }
    const num = parseInt(e.key, 10);
    if (!num || num < 1 || num > 4) return;
    if (S.view === "study" && S.tab === "review" && S.review.length) {
      submitReview(S.review[S.reviewIdx], [1, 3, 4, 5][num - 1]);
    } else if (S.view === "study" && S.tab === "quiz" && S.deck && S.deck.mcqs.length) {
      const m = S.deck.mcqs[S.quizIdx];
      if (m && num - 1 < m.options.length) answerQuiz(m, num - 1);
    }
  });

  setInterval(() => {
    if (S.view === "study" && S.tab === "review" && !document.hidden && !S.modalOpen) {
      addGoalSec(5);
    }
  }, 5000);
}

/* boot: the overlay in app.html stays up until init() settles (or 12 s,
   whichever comes first) so a cold-start wake never shows a blank app. */
const bootEl = document.getElementById("bootScreen");
function hideBoot() {
  if (!bootEl) return;
  bootEl.classList.add("out");
  setTimeout(() => bootEl.remove(), 320);
}
init().then(hideBoot, hideBoot);
setTimeout(hideBoot, 12000);
