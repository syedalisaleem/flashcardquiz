"use strict";

/* ---------------------------------------------------------------- state */

const S = {
  decks: [],
  deckId: null,
  deck: null,
  tab: "generate",
  job: null,
  filter: "",
  review: [],
  reviewIdx: 0,
  quizIdx: 0,
  quizAnswered: {},
  quizScore: { answered: 0, correct: 0 },
  model: null,
  modalOpen: false,
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

/* ---------------------------------------------------------------- modal */

function openModal(title, bodyHtml) {
  const root = document.createElement("div");
  root.className = "modal-backdrop";
  root.innerHTML = `<div class="modal"><h3>${esc(title)}</h3>${bodyHtml}</div>`;
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
       <div class="actions">
         <button data-cancel class="btn-ghost">Cancel</button>
         <button class="confirm" data-confirm>${esc(confirmLabel)}</button>
       </div>`
    );
    m.$("[data-cancel]").onclick = () => { m.close(); resolve(false); };
    m.$("[data-confirm]").onclick = () => { m.close(); resolve(true); };
  });
}

/* ---------------------------------------------------------------- prefs */

const PALETTES = {
  blue: { accent: "#2563eb" },
  violet: { accent: "#7c3aed" },
  emerald: { accent: "#059669" },
  rose: { accent: "#e11d48" },
  amber: { accent: "#d97706" },
};

function loadPrefs() {
  try { return Object.assign({ theme: "light", accent: "blue", size: "normal" }, JSON.parse(localStorage.getItem("fq_prefs") || "{}")); }
  catch (_) { return { theme: "light", accent: "blue", size: "normal" }; }
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
  const p = PALETTES[prefs.accent] || PALETTES.blue;
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
    els.forEach((b) => b.classList.toggle("active", b.dataset[key] === String(prefs[key])));
    els.forEach((b) => b.addEventListener("click", () => {
      prefs[key] = b.dataset[key];
      savePrefs(prefs);
      els.forEach((x) => x.classList.toggle("active", x === b));
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
  try {
    S.model = await api("/api/settings");
    renderModelInfo();
    await loadModels();
  } catch (_) {}
  await refreshDecks();
  if (S.decks.length) {
    S.deckId = S.decks[0].id;
    resetStudyState();
    await loadDeck();
  }
  renderSidebar();
  bindEvents();
  initDragDrop();
}

async function refreshDecks() {
  S.decks = await api("/api/decks");
}

async function loadDeck() {
  S.deck = await api(`/api/decks/${S.deckId}`);
  renderAll();
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
}

/* ---------------------------------------------------------------- render */

function renderAll() {
  renderSidebar();
  renderHeader();
  renderCards();
  renderReview();
  renderQuiz();
  renderStats();
}

function skeletonCards(count = 6) {
  return Array.from({ length: count }, () => '<div class="skeleton skeleton-card"></div>').join("");
}

function skeletonStats(count = 6) {
  return Array.from({ length: count }, () => '<div class="skeleton skeleton-stat"></div>').join("");
}

function renderSidebar() {
  const list = $("deckList");
  list.innerHTML = "";
  for (const d of S.decks) {
    const li = document.createElement("li");
    li.className = "deck-item" + (d.id === S.deckId ? " active" : "");
    li.innerHTML = `
      <span class="name">${esc(d.name)}</span>
      <span class="meta">${d.cards}${d.due ? ' <span class="due-num">' + d.due + ' due</span>' : ""}</span>`;
    li.onclick = () => selectDeck(d.id);
    list.appendChild(li);
  }
}

function renderHeader() {
  $("deckTitle").textContent = S.deck ? S.deck.name : "\u2014";
  const cards = S.deck ? S.deck.cards.length : 0;
  const due = S.deck ? S.deck.cards.filter((c) => c.due <= today()).length : 0;
  const mcqs = S.deck ? S.deck.mcqs.length : 0;
  $("cardsBadge").textContent = cards || "";
  $("dueBadge").textContent = due || "";
  $("quizBadge").textContent = mcqs || "";
  $("exportBtn").disabled = cards === 0;
}

function renderModelInfo() {
  if (!S.model) return;
  $("modelInfo").innerHTML =
    `<b>${esc(S.model.model)}</b>${S.model.mock ? '<span class="mock-tag">MOCK</span>' : ""}`;
}

async function loadModels() {
  const sel = $("modelSelect");
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

function switchTab(tab) {
  S.tab = tab;
  document.querySelectorAll("#tabs button").forEach((b) => {
    b.classList.toggle("active", b.dataset.tab === tab);
  });
  document.querySelectorAll(".tab").forEach((t) => {
    t.classList.toggle("hidden", t.id !== "tab-" + tab);
  });
  if (tab === "cards") renderCards();
  if (tab === "review") renderReview();
  if (tab === "quiz") renderQuiz();
  if (tab === "stats") renderStats();
  if (tab === "generate") loadModels();
}

/* ---------------------------------------------------------------- generate */

function setGenBusy(busy) {
  const b = $("generateBtn");
  b.disabled = busy;
  b.classList.toggle("busy", busy);
  if (busy) {
    if (!b.dataset.idle) b.dataset.idle = b.textContent || "Generate";
    b.textContent = "Generating\u2026";
  } else {
    b.textContent = b.dataset.idle || "Generate";
  }
}

async function startGenerate() {
  const source = $("sourceText").value.trim();
  if (!source) return toast("Add source material first (paste notes or upload a file).", "err");
  const numCards = Math.min(60, Math.max(1, parseInt($("numCards").value, 10) || 10));
  const numMcqs = Math.min(30, Math.max(1, parseInt($("numMcqs").value, 10) || 3));
  const tags = $("tags").value.split(",").map((t) => t.trim()).filter(Boolean);
  setGenBusy(true);
  setProgress(true, "Starting generation...");
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
      renderSidebar(); renderHeader();
      if (S.tab === "cards") renderCards();
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
  await loadDeck();
}

/* ---------------------------------------------------------------- cards */

function renderCards() {
  const grid = $("cardGrid");
  const q = S.filter.toLowerCase();
  const cards = (S.deck ? S.deck.cards : []).filter((c) => {
    if (!q) return true;
    return ((c.front || c.text) + " " + (c.back || "")).toLowerCase().includes(q);
  });
  $("cardCount").textContent = `${cards.length} of ${S.deck ? S.deck.cards.length : 0}`;
  $("cardsEmpty").classList.toggle("hidden", cards.length > 0);
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
  wrap.className = "flip-card";
  const isCloze = card.type === "Cloze";
  const front = isCloze ? clozeHtml(card.text) : esc(card.front);
  const back = esc(card.back || (isCloze ? "Cloze card \u2014 hidden terms are revealed when imported into Anki." : ""));
  wrap.innerHTML = `
    <div class="flip-inner">
      <div class="face front">
        <div class="face-text">${front || '<span style="opacity:.4">(empty)</span>'}</div>
        <div class="face-meta">
          <span class="chip">${card.type}</span>
          ${(card.tags || []).slice(0, 3).map((t) => `<span class="chip tags">#${esc(t)}</span>`).join("")}
          ${card.source ? `<span style="margin-left:auto">${esc(card.source)}</span>` : ""}
        </div>
      </div>
      <div class="face back">
        <div class="face-text">${back || '<span style="opacity:.4">No answer</span>'}</div>
        <div class="face-meta">
          <span class="chip" style="background:var(--green-soft);color:var(--green)">Answer</span>
          ${card.source ? `<span style="margin-left:auto">${esc(card.source)}</span>` : ""}
        </div>
      </div>
    </div>
    <div class="card-actions">
      <button data-act="edit">Edit</button>
      <button data-act="del">Delete</button>
    </div>`;
  wrap.querySelector(".flip-inner").onclick = () => wrap.classList.toggle("flipped");
  wrap.querySelector('[data-act="edit"]').onclick = () => editCardModal(card);
  wrap.querySelector('[data-act="del"]').onclick = () => deleteCard(card);
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
     <div class="actions">
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

async function renderReview() {
  const area = $("reviewArea");
  $("reviewDueCount").textContent = S.deck ? S.deck.cards.filter((c) => c.due <= today()).length : 0;

  if (!S.review.length) {
    const due = S.deck ? S.deck.cards.filter((c) => c.due <= today()) : [];
    if (!due.length) {
      area.innerHTML = `
        <div class="empty">
          <div class="empty-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg></div>
          <div class="empty-title">All caught up</div>
          <p>No cards due today. Generate more or come back tomorrow.</p>
        </div>`;
      return;
    }
    S.review = due; S.reviewIdx = 0;
  }

  const card = S.review[S.reviewIdx];
  const isCloze = card.type === "Cloze";
  const front = isCloze ? clozeHtml(card.text) : esc(card.front);
  const back = esc(card.back || "");
  area.innerHTML = `
    <div class="review-stage">
      <div class="flip-card big review-flip">
        <div class="flip-inner">
          <div class="face front">
            <div class="face-text" style="justify-content:center;text-align:center;font-size:16px">${front}</div>
            <div class="face-meta"><span class="chip">${card.type}</span><span style="margin-left:auto;opacity:.6">Click to reveal</span></div>
          </div>
          <div class="face back">
            <div class="face-text" style="justify-content:center;text-align:center;font-size:15px">${back}</div>
            <div class="face-meta"><span class="chip" style="background:var(--green-soft);color:var(--green)">Answer</span></div>
          </div>
        </div>
      </div>
      <div class="review-actions" id="qualityBtns">
        <button class="q1" data-q="1"><span class="key-hint">1</span> Again</button>
        <button class="q3" data-q="3"><span class="key-hint">2</span> Hard</button>
        <button class="q4" data-q="4"><span class="key-hint">3</span> Good</button>
        <button class="q5" data-q="5"><span class="key-hint">4</span> Easy</button>
      </div>
      <div class="review-progress">${S.reviewIdx + 1} of ${S.review.length} due \u00b7 press 1\u20134</div>
      <div id="reviewResult" class="review-result"></div>
    </div>`;
  area.querySelector(".flip-inner").onclick = () =>
    area.querySelector(".flip-card").classList.toggle("flipped");
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
    S.review = S.review.filter((c) => c !== card);
    const result = $("reviewResult");
    if (result) {
      result.innerHTML = `${labels[quality]} \u2014 next review in <b>${res.interval}</b> day${res.interval === 1 ? "" : "s"} (ease ${res.ease.toFixed(2)})`;
      result.className = "review-result ok";
    }
    if (S.review.length) {
      setTimeout(() => { S.reviewIdx = 0; renderReview(); }, 700);
    } else {
      setTimeout(() => { S.review = []; S.reviewIdx = 0; renderReview(); toast("Review session complete!", "ok"); loadDeck(); }, 900);
    }
  } catch (err) { toast("Error: " + err.message, "err"); }
}

/* ---------------------------------------------------------------- quiz */

function renderQuiz() {
  const area = $("quizArea");
  const mcqs = S.deck ? S.deck.mcqs : [];
  $("quizSessionScore").textContent = S.quizScore.answered
    ? `${S.quizScore.correct}/${S.quizScore.answered} correct (${Math.round((S.quizScore.correct / S.quizScore.answered) * 100)}%)`
    : "";
  if (!mcqs.length) {
    area.innerHTML = `
      <div class="empty">
        <div class="empty-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg></div>
        <div class="empty-title">No questions yet</div>
        <p>Generate MCQs on the Generate tab, then test yourself here.</p>
      </div>`;
    return;
  }
  S.quizIdx = Math.min(S.quizIdx, mcqs.length - 1);
  const m = mcqs[S.quizIdx];
  const chosen = S.quizAnswered[m.id];
  const answered = chosen !== undefined;

  let html = `
    <div class="quiz-box">
      <div class="quiz-head">
        <span>Q${S.quizIdx + 1} of ${mcqs.length}</span>
        <span>${m.answered ? Math.round((m.correct / m.answered) * 100) + "%" : ""}</span>
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
    html += `<button class="${cls}" data-choice="${i}"><span class="key">${keys[i]}</span>${esc(opt)}</button>`;
  });
  const expl = answered
    ? (chosen === m.correct_index ? m.explanation : (m.distractor_explanations[String(chosen)] || m.explanation))
    : "";
  html += `</div>
      <div class="quiz-expl">${answered ? esc(expl) : ""}</div>
      <div class="quiz-nav">
        <div class="nav-btns">
          <button id="quizPrev" class="btn-ghost btn-sm">&larr; Prev</button>
          <button id="quizNext" class="btn-ghost btn-sm">Next &rarr;</button>
        </div>
        <button id="quizSkip" class="btn-ghost btn-sm">${answered ? "Next" : "Skip"}</button>
      </div>
    </div>
    <div class="quiz-list">
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
    renderQuiz();
    toast(res.correct ? "Correct!" : "Not quite.", res.correct ? "ok" : "err");
  } catch (err) { toast("Error: " + err.message, "err"); }
}

/* ---------------------------------------------------------------- stats */

function renderStats() {
  const area = $("statsArea");
  if (!S.deck) { area.innerHTML = '<p class="muted">Select a deck to see stats.</p>'; return; }

  const cards = S.deck.cards;
  const mcqs = S.deck.mcqs;
  const totalCards = cards.length;
  const dueToday = cards.filter((c) => c.due <= today()).length;
  const mastered = cards.filter((c) => (c.repetitions || 0) >= 3).length;
  const totalMcqs = mcqs.length;
  const avgAccuracy = totalMcqs > 0
    ? Math.round(mcqs.reduce((sum, m) => sum + (m.answered ? (m.correct / m.answered) : 0), 0) / totalMcqs * 100)
    : 0;
  const masteryRate = totalCards > 0 ? Math.round(mastered / totalCards * 100) : 0;
  const dueRate = totalCards > 0 ? Math.round(dueToday / totalCards * 100) : 0;

  area.innerHTML = `
    <div class="stat-card animate-in" style="animation-delay:0ms">
      <div class="stat-value">${totalCards}</div>
      <div class="stat-label">Total cards</div>
      <div class="stat-bar"><div class="stat-bar-fill" style="width:100%;background:var(--accent)"></div></div>
    </div>
    <div class="stat-card accent animate-in" style="animation-delay:50ms">
      <div class="stat-value">${mastered}</div>
      <div class="stat-label">Mastered (3+ reps)</div>
      <div class="stat-bar"><div class="stat-bar-fill" style="width:${masteryRate}%;background:var(--green)"></div></div>
    </div>
    <div class="stat-card amber animate-in" style="animation-delay:100ms">
      <div class="stat-value">${dueToday}</div>
      <div class="stat-label">Due today</div>
      <div class="stat-bar"><div class="stat-bar-fill" style="width:${dueRate}%;background:var(--amber)"></div></div>
    </div>
    <div class="stat-card green animate-in" style="animation-delay:150ms">
      <div class="stat-value">${avgAccuracy}%</div>
      <div class="stat-label">Quiz accuracy</div>
      <div class="stat-bar"><div class="stat-bar-fill" style="width:${avgAccuracy}%;background:var(--green)"></div></div>
    </div>
    <div class="stat-card animate-in" style="animation-delay:200ms">
      <div class="stat-value">${totalMcqs}</div>
      <div class="stat-label">Total questions</div>
    </div>
    <div class="stat-card accent animate-in" style="animation-delay:250ms">
      <div class="stat-value">${masteryRate}%</div>
      <div class="stat-label">Mastery rate</div>
      <div class="stat-bar"><div class="stat-bar-fill" style="width:${masteryRate}%;background:var(--accent)"></div></div>
    </div>`;
}

/* ---------------------------------------------------------------- decks */

function createDeck() {
  const m = openModal(
    "New deck",
    `<input type="text" id="deckName" placeholder="e.g. Lecture 02 \u2014 Genetics" style="width:100%" />
     <div class="actions">
       <button data-cancel class="btn-ghost">Cancel</button>
       <button class="btn-primary" data-create>Create</button>
     </div>`
  );
  const doCreate = async () => {
    const name = m.$("#deckName").value.trim();
    if (!name) { m.$("#deckName").focus(); return; }
    try {
      const deck = await api("/api/decks", { method: "POST", body: JSON.stringify({ name }) });
      m.close(); await refreshDecks(); await selectDeck(deck.id);
      toast(`Deck "${name}" created.`, "ok");
    } catch (err) { toast("Error: " + err.message, "err"); }
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
  else { S.deck = null; resetStudyState(); renderAll(); }
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
    if (e.target.tagName !== "INPUT") fileInput.click();
  });

  zone.addEventListener("dragover", (e) => { e.preventDefault(); zone.classList.add("dragover"); });
  zone.addEventListener("dragleave", () => zone.classList.remove("dragover"));
  zone.addEventListener("drop", (e) => {
    e.preventDefault();
    zone.classList.remove("dragover");
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  });

  fileInput.addEventListener("change", (e) => {
    const file = e.target.files[0];
    if (file) handleFile(file);
    e.target.value = "";
  });

  const removeBtn = $("fileRemove");
  if (removeBtn) removeBtn.addEventListener("click", () => {
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
  $("fileStatus").textContent = "Reading file...";
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

/* ---------------------------------------------------------------- events */

function bindEvents() {
  if (PLATFORM !== "web") {
    const chip = $("platformChip");
    chip.textContent = PLATFORM === "android" ? "Android" : "Desktop";
    chip.hidden = false;
  }
  const sidebar = $("sidebar");
  const backdrop = $("sidebarBackdrop");
  const setDrawer = (open) => {
    sidebar.classList.toggle("open", open);
    backdrop.classList.toggle("show", open);
  };
  $("menuBtn").onclick = () => setDrawer(!sidebar.classList.contains("open"));
  backdrop.onclick = () => setDrawer(false);

  document.querySelectorAll("#tabs button").forEach((b) => {
    b.onclick = () => switchTab(b.dataset.tab);
  });
  $("generateBtn").onclick = startGenerate;
  $("sourceText").addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") { e.preventDefault(); startGenerate(); }
  });
  $("modelSelect").onchange = onModelChange;
  $("exportBtn").onclick = exportDeck;
  $("prefsBtn").onclick = openPrefs;
  $("newDeckBtn").onclick = createDeck;
  $("deleteDeckBtn").onclick = deleteDeck;
  $("loadSample").onclick = () => { $("sourceText").value = SAMPLE; toast("Sample notes loaded.", "ok"); };
  $("cardFilter").oninput = (e) => { S.filter = e.target.value; renderCards(); };

  document.addEventListener("keydown", (e) => {
    if (S.modalOpen || e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.target.matches("input, textarea, select")) return;
    const num = parseInt(e.key, 10);
    if (!num || num < 1 || num > 4) return;
    if (S.tab === "review" && S.review.length) {
      submitReview(S.review[S.reviewIdx], [1, 3, 4, 5][num - 1]);
    } else if (S.tab === "quiz" && S.deck && S.deck.mcqs.length) {
      const m = S.deck.mcqs[S.quizIdx];
      if (m && num - 1 < m.options.length) answerQuiz(m, num - 1);
    }
  });
}

init();
