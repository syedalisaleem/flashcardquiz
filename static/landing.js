"use strict";

/* ---------------------------------------------------------------- platform */

function detectPlatform() {
  const p = new URLSearchParams(location.search).get("platform");
  if (p === "android" || p === "desktop") return p;
  return /FlashcardQuiz/i.test(navigator.userAgent) ? "android" : "web";
}

const PLATFORM = detectPlatform();
const IS_MOBILE =
  (window.matchMedia && window.matchMedia("(pointer: coarse)").matches) ||
  window.innerWidth <= 760;

document.body.dataset.platform = PLATFORM;
if (PLATFORM === "web" && IS_MOBILE) document.body.classList.add("web-mobile");

(function platformUi() {
  if (PLATFORM !== "web") {
    document.querySelectorAll("#downloadBtn, #ctaDownloadBtn, .dl-btn").forEach((a) => {
      if (a.href && a.href.includes("/api/download/")) a.href = "/app";
    });
    document.querySelectorAll(".nav .brand").forEach((a) => { a.href = "/app"; });
    return;
  }
  if (IS_MOBILE) {
    const swap = (btn) => {
      if (!btn) return;
      btn.href = "/api/download/android";
    };
    swap(document.getElementById("downloadBtn"));
    const card = document.getElementById("getAndroidCard");
    if (card) card.classList.add("get-card-featured");
  }
})();

/* theme toggle */
function fqPrefs() {
  try { return JSON.parse(localStorage.getItem("fq_prefs") || "{}") || {}; } catch (_) { return {}; }
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
}

(function initTheme() {
  const p = fqPrefs();
  const dark = p.theme === "dark" || (p.theme !== "light" && window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
  applyTheme(dark ? "dark" : "light");

  const btn = document.getElementById("themeToggle");
  if (btn) btn.addEventListener("click", () => {
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    const prefs = fqPrefs();
    prefs.theme = next;
    try { localStorage.setItem("fq_prefs", JSON.stringify(prefs)); } catch (_) {}
    applyTheme(next);
  });
})();

/* scroll reveal */
const reveals = document.querySelectorAll(".reveal, .feature-card, .step, .get-card, .section-header");
if ("IntersectionObserver" in window) {
  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add("visible");
          io.unobserve(e.target);
        }
      });
    },
    { threshold: 0.1, rootMargin: "0px 0px -40px 0px" }
  );
  reveals.forEach((el) => {
    el.classList.add("reveal");
    io.observe(el);
  });
} else {
  reveals.forEach((el) => el.classList.add("visible"));
}

/* download toast */
function toastLite(msg, kind) {
  const box = document.createElement("div");
  box.className = "toast-lite " + (kind || "");
  box.textContent = msg;
  document.body.appendChild(box);
  setTimeout(() => {
    box.classList.add("out");
    setTimeout(() => box.remove(), 280);
  }, 3200);
}

document.querySelectorAll(".dl-btn").forEach((btn) => {
  if (!btn.href || !btn.href.includes("/api/download/")) return;
  btn.addEventListener("click", () => {
    toastLite("Download started — check your downloads.", "ok");
  });
});
