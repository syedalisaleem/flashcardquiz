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
    /* inside a native shell: route download buttons to the app UI */
    document.querySelectorAll("#downloadBtn, #ctaDownloadBtn, .dl-btn").forEach((a) => {
      if (a.href && a.href.includes("/api/download/")) a.href = "/app";
    });
    document.querySelectorAll("#dlLabel, #ctaDlLabel").forEach((s) => {
      if (s) s.textContent = "Open the app";
    });
    document.querySelectorAll(".nav .brand").forEach((a) => { a.href = "/app"; });
    return;
  }
  if (IS_MOBILE) {
    /* phones get the APK first */
    const swap = (btn, labelId, label) => {
      if (!btn) return;
      btn.href = "/api/download/android";
      const l = document.getElementById(labelId);
      if (l) l.textContent = label;
    };
    swap(document.getElementById("downloadBtn"), "dlLabel", "Get the Android app");
    swap(document.getElementById("ctaDownloadBtn"), "ctaDlLabel", "Get the Android app");
    const card = document.getElementById("getAndroidCard");
    if (card) card.classList.add("featured");
  }
})();

/* theme toggle (shares localStorage with the app) */
function fqPrefs() {
  try { return JSON.parse(localStorage.getItem("fq_prefs") || "{}") || {}; } catch (_) { return {}; }
}
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  const t = document.getElementById("themeToggle");
  if (t) t.textContent = theme === "dark" ? "\u263D" : "\u263E";
}
(function initTheme() {
  const p = fqPrefs();
  const dark = p.theme === "dark" || (p.theme !== "light" && window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
  applyTheme(dark ? "dark" : "light");
  const t = document.getElementById("themeToggle");
  if (t) t.addEventListener("click", () => {
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    const prefs = fqPrefs();
    prefs.theme = next;
    try { localStorage.setItem("fq_prefs", JSON.stringify(prefs)); } catch (_) {}
    applyTheme(next);
  });
})();

/* scroll reveal */
const reveals = document.querySelectorAll(".reveal");
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
    { threshold: 0.12 }
  );
  reveals.forEach((el) => io.observe(el));
} else {
  reveals.forEach((el) => el.classList.add("visible"));
}

/* download buttons: plain navigation so the browser streams the file
   natively (no blob buffering — reliable even for the 110 MB exe over a
   tunnel), with a confirmation toast */
function toastLite(msg, kind) {
  const box = document.createElement("div");
  box.className = "toast-lite " + (kind || "");
  box.textContent = msg;
  document.body.appendChild(box);
  setTimeout(() => {
    box.classList.add("out");
    setTimeout(() => box.remove(), 320);
  }, 3600);
}

document.querySelectorAll("#downloadBtn, #ctaDownloadBtn, .dl-btn").forEach((btn) => {
  if (!btn.href || !btn.href.includes("/api/download/")) return;
  btn.addEventListener("click", () => {
    toastLite("Download started — check your downloads.", "ok");
  });
});
