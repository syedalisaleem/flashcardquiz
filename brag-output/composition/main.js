(function () {
  window.__timelines = window.__timelines || {};
  var tl = window.__timelines["main"];
  if (!tl || typeof tl.to !== 'function') {
    tl = gsap.timeline({ paused: true, defaults: { ease: 'power3.out' } });
    window.__timelines["main"] = tl;
  }

  /* ---------- ambient backdrop (finite, on the timeline) ---------- */
  tl.to('#ring1', { rotation: 170, duration: 20, ease: 'none' }, 0);
  tl.to('#ring2', { rotation: -140, duration: 20, ease: 'none' }, 0);

  /* ---------- audio-reactive glow (per-frame band sampling) ---------- */
  var AD = window.__AUDIO_DATA || { fps: 20, totalFrames: 0, frames: [] };
  var glowA = document.getElementById('glowA');
  var glowB = document.getElementById('glowB');
  function sampleAudio(i) {
    var f = AD.frames[i];
    if (!f) { return; }
    var b = f.bands || [];
    var bass = b[0] || 0;
    var mid = b[4] || 0;
    if (glowA) {
      glowA.style.transform = 'scale(' + (1 + bass * 0.14).toFixed(4) + ')';
      glowA.style.opacity = (0.56 + bass * 0.3).toFixed(3);
    }
    if (glowB) {
      glowB.style.opacity = (0.30 + mid * 0.26).toFixed(3);
    }
  }
  var fps = AD.fps || 20;
  var maxFrames = Math.min(AD.totalFrames || 0, Math.floor(20 * fps));
  for (var fi = 0; fi < maxFrames; fi++) {
    tl.call(sampleAudio, [fi], fi / fps);
  }

  /* =========================================================
     SCENE 1 â€” paste the lecture (0.00 â†’ 4.15)
     ========================================================= */
  tl.fromTo('#s1-rule', { scaleX: 0 }, { scaleX: 1, duration: 0.7, ease: 'power2.out' }, 0.16);
  tl.fromTo('#s1-l4', { y: 16, opacity: 0 }, { y: 0, opacity: 1, duration: 0.34, ease: 'power2.out' }, 0.28);
  tl.fromTo('#s1-l5', { y: 16, opacity: 0 }, { y: 0, opacity: 1, duration: 0.34, ease: 'power2.out' }, 0.56);
  tl.fromTo('#s1-l6', { y: 16, opacity: 0 }, { y: 0, opacity: 1, duration: 0.34, ease: 'power2.out' }, 0.84);
  tl.to('#s1-caret', { opacity: 0, duration: 0.5, yoyo: true, repeat: 5, ease: 'none' }, 1.10);

  tl.to('#s1-app', { y: -8, duration: 1.8, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 0.40);
  tl.to('#s1-right', { y: -6, duration: 1.7, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 0.60);

  tl.fromTo('#s1-cursor', { x: 0, y: 0 }, { x: 452, y: 206, duration: 0.95, ease: 'power2.inOut' }, 2.10);
  tl.fromTo('#s1-cursor', { scale: 1 }, { scale: 0.85, duration: 0.09, ease: 'power1.out' }, 3.28);
  tl.to('#s1-cursor', { scale: 1, duration: 0.18, ease: 'back.out(3)' }, 3.37);
  tl.fromTo('#s1-btn', { scale: 1 }, { scale: 0.955, duration: 0.09, ease: 'power1.out' }, 3.28);
  tl.to('#s1-btn', { scale: 1, duration: 0.2, ease: 'back.out(3)' }, 3.37);

  tl.to('#s1-inner', { x: -70, opacity: 0, duration: 0.35, ease: 'power2.in' }, 3.70);

  /* =========================================================
     SCENE 2 â€” the deck builds (3.70 â†’ 8.60)
     ========================================================= */
  tl.fromTo('#s2-inner', { x: 80, opacity: 0 }, { x: 0, opacity: 1, duration: 0.55 }, 3.85);
  tl.fromTo('#s2-rule', { scaleX: 0 }, { scaleX: 1, duration: 0.65, ease: 'power2.out' }, 4.16);
  tl.fromTo('#s2-left', { x: -26, opacity: 0 }, { x: 0, opacity: 1, duration: 0.5 }, 4.10);

  tl.fromTo('#s2-fill', { scaleX: 0 }, { scaleX: 1, duration: 3.0, ease: 'power1.inOut' }, 4.60);

  var counter = { v: 0 };
  var countEl = document.getElementById('s2-count');
  tl.to(counter, {
    v: 24, duration: 3.0, ease: 'power1.inOut',
    onUpdate: function () {
      if (countEl) { countEl.textContent = Math.round(counter.v) + ' / 24'; }
    }
  }, 4.60);

  tl.fromTo('#c1', { y: 96, opacity: 0 }, { y: 0, opacity: 1, duration: 0.55, ease: 'power3.out' }, 4.75);
  tl.fromTo('#c2', { x: 70, y: 40, opacity: 0 }, { x: 0, y: 0, opacity: 1, duration: 0.55, ease: 'back.out(1.3)' }, 5.28);
  tl.fromTo('#c3', { y: 120, opacity: 0 }, { y: 0, opacity: 1, duration: 0.5, ease: 'power4.out' }, 5.80);
  tl.fromTo('#c4', { x: -70, y: 40, opacity: 0 }, { x: 0, y: 0, opacity: 1, duration: 0.55, ease: 'back.out(1.2)' }, 6.34);

  tl.to('#s2-stack', { y: -14, duration: 1.3, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 5.90);

  tl.to('#s2-inner', { x: -70, opacity: 0, duration: 0.35, ease: 'power2.in' }, 8.15);

  /* =========================================================
     SCENE 3 â€” real student mistakes (8.15 â†’ 13.15)
     ========================================================= */
  tl.fromTo('#s3-inner', { x: 80, opacity: 0 }, { x: 0, opacity: 1, duration: 0.55 }, 8.30);
  tl.fromTo('#s3-left', { x: -26, opacity: 0 }, { x: 0, opacity: 1, duration: 0.5 }, 8.48);
  tl.fromTo('#s3-rule', { scaleX: 0 }, { scaleX: 1, duration: 0.6, ease: 'power2.out' }, 8.56);

  tl.fromTo('#s3-panel', { y: 54, opacity: 0, scale: 0.985 }, { y: 0, opacity: 1, scale: 1, duration: 0.6 }, 8.42);
  tl.fromTo('#s3-chip', { y: 14, opacity: 0 }, { y: 0, opacity: 1, duration: 0.4 }, 8.62);
  tl.fromTo('#s3-score', { y: 14, opacity: 0 }, { y: 0, opacity: 1, duration: 0.4 }, 8.66);
  tl.fromTo('#s3-q', { y: 18, opacity: 0 }, { y: 0, opacity: 1, duration: 0.45 }, 8.70);

  tl.fromTo('#s3-o1', { y: 44, opacity: 0 }, { y: 0, opacity: 1, duration: 0.45 }, 8.80);
  tl.fromTo('#s3-o2', { y: 44, opacity: 0 }, { y: 0, opacity: 1, duration: 0.45 }, 8.98);
  tl.fromTo('#s3-o3', { y: 44, opacity: 0 }, { y: 0, opacity: 1, duration: 0.45 }, 9.16);
  tl.fromTo('#s3-o4', { y: 44, opacity: 0 }, { y: 0, opacity: 1, duration: 0.45 }, 9.34);

  tl.fromTo('#s3-o2',
    { backgroundColor: '#F4FBF7', borderColor: 'rgba(49,33,22,0.11)' },
    { backgroundColor: 'rgba(61,154,110,0.16)', borderColor: '#3D9A6E', duration: 0.34, ease: 'power2.out' }, 9.50);
  tl.fromTo('#s3-badge2',
    { backgroundColor: 'rgba(49,33,22,0.11)', color: '#312116' },
    { backgroundColor: '#3D9A6E', color: '#FFFFFF', duration: 0.34, ease: 'power2.out' }, 9.50);
  tl.fromTo('#s3-exp2', { opacity: 0, y: 8 }, { opacity: 1, y: 0, duration: 0.4 }, 9.54);
  tl.fromTo('#s3-check', { scale: 0, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.42, ease: 'back.out(2.4)' }, 9.58);

  tl.to('#s3-panel', { y: -10, duration: 1.3, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 9.95);

  tl.to('#s3-inner', { x: -70, opacity: 0, duration: 0.35, ease: 'power2.in' }, 12.70);

  /* =========================================================
     SCENE 4 â€” review + export (12.70 â†’ 16.65)
     ========================================================= */
  tl.fromTo('#s4-inner', { x: 80, opacity: 0 }, { x: 0, opacity: 1, duration: 0.55 }, 12.85);
  tl.fromTo('#s4b1', { x: -26, opacity: 0 }, { x: 0, opacity: 1, duration: 0.5 }, 13.00);
  tl.fromTo('#s4-rule', { scaleX: 0 }, { scaleX: 1, duration: 0.6, ease: 'power2.out' }, 13.08);
  tl.fromTo('#s4b2', { x: -26, opacity: 0 }, { x: 0, opacity: 1, duration: 0.5 }, 14.75);

  tl.fromTo('#s4-stage', { y: 56, opacity: 0 }, { y: 0, opacity: 1, duration: 0.55 }, 13.34);
  tl.fromTo('#s4-flip', { rotationY: 0 }, { rotationY: 180, duration: 0.62, ease: 'power2.inOut' }, 13.95);

  tl.fromTo('.grade',
    { y: 36, opacity: 0 },
    { y: 0, opacity: 1, duration: 0.42, stagger: 0.09, ease: 'back.out(1.5)' }, 14.25);
  tl.fromTo('#s4-intervals', { y: 16, opacity: 0 }, { y: 0, opacity: 1, duration: 0.42 }, 14.40);

  tl.fromTo('#s4-export', { y: 24, opacity: 0 }, { y: 0, opacity: 1, duration: 0.4, ease: 'back.out(1.6)' }, 15.12);
  tl.fromTo('#s4-export', { scale: 1 }, { scale: 0.95, duration: 0.1, ease: 'power1.out' }, 15.56);
  tl.to('#s4-export', { scale: 1, duration: 0.22, ease: 'back.out(3)' }, 15.66);
  tl.fromTo('#s4-chip', { scale: 0.5, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.4, ease: 'back.out(2.4)' }, 15.80);

  tl.to('#s4-inner', { x: -70, opacity: 0, duration: 0.35, ease: 'power2.in' }, 16.20);

  /* =========================================================
     SCENE 5 â€” free, forever (16.20 â†’ 20.00)
     ========================================================= */
  tl.fromTo('#s5-inner', { x: 70, opacity: 0 }, { x: 0, opacity: 1, duration: 0.55 }, 16.35);
  tl.fromTo('#s5-head', { x: -26, opacity: 0 }, { x: 0, opacity: 1, duration: 0.5 }, 16.50);
  tl.fromTo('#s5-rule', { scaleX: 0 }, { scaleX: 1, duration: 0.6, ease: 'power2.out' }, 16.58);

  tl.fromTo('.plat',
    { y: 78, opacity: 0 },
    { y: 0, opacity: 1, duration: 0.5, stagger: 0.14, ease: 'power3.out' }, 16.70);

  tl.fromTo('#m1', { y: 54, opacity: 0, rotation: -16 }, { y: 0, opacity: 1, rotation: -3, duration: 0.55, ease: 'back.out(1.3)' }, 17.30);
  tl.fromTo('#m2', { y: 54, opacity: 0, rotation: 18 }, { y: 0, opacity: 1, rotation: 5, duration: 0.55, ease: 'back.out(1.3)' }, 17.45);

  tl.fromTo('#s5-head .hl', { y: 60, opacity: 0, scale: 0.94 },
    { y: 0, opacity: 1, scale: 1, duration: 0.5, ease: 'back.out(1.4)' }, 17.55);
  tl.fromTo('#s5-chip', { scale: 0.5, opacity: 0 },
    { scale: 1, opacity: 1, duration: 0.45, ease: 'back.out(2.4)' }, 17.91);
  tl.fromTo('#s5-lock', { y: 34, opacity: 0 }, { y: 0, opacity: 1, duration: 0.5 }, 18.60);

  tl.seek(0);
})();
