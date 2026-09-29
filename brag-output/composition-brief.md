# Hyperframes Composition Brief: FlashcardQuiz

## Objective
Create a short launch-style brag video for **FlashcardQuiz** — an AI flashcard
and quiz generator that turns lecture material into atomic cards, misconception
MCQs, SM-2 reviews and one-click Anki export, free with no account.

## Output
- Composition directory: `brag-output/composition/`
- Rendered video: `brag-output/brag.mp4`
- Format: landscape — 1920x1080
- Duration: 20 seconds

## Source Material
- Project root: `F:\Downloads\flashcardquiz-master (copy)\flashcardquiz-master (copy)`
- Primary files read: `static/index.html`, `static/tokens.css`, `static/app.html`,
  `static/manifest.json`, `README.md`
- Product name: FlashcardQuiz (landing wordmark: `FlashcardQuiz`, full title
  `Flashcard & Quiz Generator`)
- Tagline / strongest claim: *"Turn dense lectures into flashcards that
  actually stick."* and *"Wrong MCQ options come from real student mistakes."*
- Key UI or visual moment to recreate: the **Generate tab** of `/app` (tab strip
  `Generate / Review / Quiz`, Source material textarea, tags field, teal
  Generate button) and the **hero card stack** (three stacked flashcards with
  `Basic` / `Cloze` / `Answer` chips).
- Copy that must appear verbatim:
  - `Flashcards that actually stick.`
  - `Atomic cards — one fact each.`
  - `Wrong answers come from real student mistakes.`
  - `SM-2 spaced repetition`
  - `One-click Anki export`
  - `Free, forever.`
  - `No account needed.`
  - Card faces: `DNA replication: Enzyme that unwinds the double helix`,
    `The mitochondria produces ATP.`, `DNA Helicase`

## Creative Direction
- Tone preset: `app-store`
- Creative direction: *"calm mint-and-sage product film where the flashcards
  themselves are the main character"*
- Interpretation: feature-card rhythm, smooth slide/wipe reveals, clean reveals
  with no mess; the copy carries the personality, motion stays confident and
  typography holds long enough to read at every step.
- Angle: the whole video runs *inside* the tool. We never cut to a marketing
  page — we paste the lecture, watch the deck build, hit the misconception
  quiz, grade a card, export to Anki, and land on the three platforms. The
  payoff line is the claim only this app makes: the wrong answers are real
  student mistakes.
- Hook: the Generate tab mid-work — lecture text streaming into the Source
  material textarea, `Biology,Lecture_01` tag snapping in, headline
  *"Flashcards that actually stick."* slamming over it, ending on the cursor
  pressing **Generate**.
- Outro / punchline: three platform cards (Browser / Windows desktop / Android
  APK) slide in under *"Free, forever."* → chip *"No account needed."* → logo
  lands on the final strong beat.
- Avoid:
  - Generic SaaS language ("streamline your workflow", "supercharge learning")
  - Abstract filler visuals, color washes, unrelated motion graphics
  - Redesigning the product's UI — use the project's real layout, copy and
    palette only
  - Any API key, token, host URL or personal data on screen

## Visual Identity
- Background: `#BEE9DA` (light mint), stepping to `#A8D9C4`; panels `#F4FBF7`,
  elevated `#FFFFFF`
- Text: `#312116` (primary), `#4A3C32` (secondary), `#685D55` (muted)
- Accent: `#7CD5C8` (teal), `#3DB8A8` (deep), sage `#9BAE8B`
- Semantic: correct `#3D9A6E`, wrong `#C45B5B`, amber `#B8863B`
- Brand gradient: `linear-gradient(135deg, #7CD5C8 0%, #A8D9C4 50%, #9BAE8B 100%)`
- Display font: **DM Sans 700** (Google Fonts)
- Body font: **DM Sans 400/500/600**; mono accents: **JetBrains Mono 500**
- Visual references from the project:
  - Hero's three stacked flashcards with `Basic` / `Cloze` / `Answer` chips
  - Teal→sage gradient text treatment on emphasis words
  - Tab strip `Generate · Review · Quiz` with a teal active pill
  - Feature/get cards with 12px/18px radii and `0 4px 14px rgba(49,33,22,0.09)`
    shadow
  - Logo + wordmark from `static/icons/logo.png` (may be referenced, not required)

## Storyboard
The storyboard in `brag-output/brag-plan.md` is the creative contract.

Scene summary:
1. Paste the lecture — 3.7s — Generate tab, notes stream in, tag chip pops,
   cursor clicks Generate; headline *"Flashcards that actually stick."*
2. The deck builds — 4.8s — four real card faces land one by one into a stack
   with a "Generating cards…" progress bar; label *"Atomic cards — one fact
   each."*
3. Real student mistakes — 4.8s — Quiz tab, four options arrive, three wrong
   ones show their "why it's wrong" note, correct answer goes green; headline
   *"Wrong answers come from real student mistakes."*
4. Review and export — 3.6s — card flips, SM-2 grade buttons `1 2 3 4` appear,
   Export to Anki pressed → `.apkg` chip; labels *"SM-2 spaced repetition"* and
   *"One-click Anki export"*
5. Free, forever — 3.1s — three platform cards slide in, *"Free, forever."* +
   chip *"No account needed."*, logo lands.

## Audio
- Audio role: warm bed — bright, calm, confident; a study tool, not a hype reel
- Audio arc: enters under the hook at ~0.55, eases up across scenes 3–4 (the
  card build and the green correct answer), resolves on the logo hit, tail fade
  over the final ~1.0s
- Music: `assets/music/happy-beats-business-moves-vol-11-by-ende-dot-app.mp3`
- Music treatment: starts at 0.0s, volume ~0.35, gentle rise to ~0.45 by
  Scene 3, fade-out over the last second so the final SFX rings clean; never
  above 0.5
- Music cue guidance: bundled preset at
  `C:\Users\callm\.agents\skills\brag\assets\music\cues\happy-beats-business-moves-vol-11-by-ende-dot-app.music-cues.json`
  (114.84 BPM). Strong cues in window: 1.60, 3.70, 5.80, 6.34, 8.96, 9.50,
  12.65, 17.91, 22.65. Lock only 1–3 major moments: **3.70s** (transition into
  the card build), **9.50s** (correct answer goes green), **17.91s** (platform
  cards / logo). Beat grid is 0.52s spacing for sequential arrivals. Ignore any
  cue that hurts readability or scene pacing.
- Audio-reactive treatment: subtle — background warmth and the hero card
  stack's soft glow may breathe with music RMS/bass. No waveform, equalizer,
  note or particle visuals; no strobing or text scaling.
- Audio-coupled moments:
  - Scene 1 — subtle key ticks while lecture text streams in; short click on
    the Generate press
  - Scene 2 — one soft card-place per card, four arrivals on consecutive beats
  - Scene 3 — one light option-snap per distractor; single clean confirmation
    when the correct answer turns green
  - Scene 4 — card-flip swish; light switch on Export
  - Scene 5 — three light card slides; one logo hit on 17.91s
- SFX selection guidance: app-store energy — consistent light layer at
  0.65–0.75 volume. Card-like reveals use `casino/card-place-*` /
  `casino/card-slide-*`; feature-card pop-ins use `interface/drop_*`;
  simulated interaction uses `interface/click_*` / `ui/mouseclick1`; typing uses
  `keyboard/keypress-*.wav` randomized; one restrained announcement cue
  (`impact/impactBell_heavy_000`) is allowed on the outro logo and nowhere else.
- SFX analysis guidance: `C:\Users\callm\.agents\skills\brag\assets\sfx\sfx-analysis.md`
  — prefer low/medium high-frequency-risk files for repeated or polished
  moments; do not stack dense high-HF sequences.
- Exact SFX choice: Hyperframes should choose filenames, timestamps, density
  and volume based on the implemented animation.
- Audio files: copy the chosen music and any Hyperframes-selected SFX into
  `brag-output/composition/assets/`

## Hyperframes Instructions
Load the composition-building Hyperframes domain skills — `hyperframes-core`
(composition contract + `data-*` timing), `hyperframes-animation` (motion),
`hyperframes-creative` (design spec, beats, audio-reactive),
`hyperframes-keyframes` (seek-safe keyframes), and `hyperframes-cli`
(lint/check/render). /brag is its own workflow: do not enter the `hyperframes`
entry-point intent interview and do not route into its generic promo /
launch-video workflow. Prefer native Hyperframes conventions over anything in
`/brag`.

Requirements:
- Show at least one real UI, copy, or visual element from the source project.
- Keep all text readable in the final render.
- Keep the video within 15-25 seconds.
- Include the planned music/SFX layer unless audio was explicitly disabled or
  documented as intentionally silent.
- Treat `/brag` audio notes as guidance, not a fixed cue sheet. Choose SFX
  after the visual animation exists.
- Treat music cue metadata as optional timing hints. Hyperframes decides exact
  animation timing and should ignore cues that hurt readability, scene pacing,
  or the product story.
- Use only 1–3 strong cue locks in this 20s video.
- Use SFX to support motion and interaction; restraint when the edit is already
  busy.
- Honor the planned music treatment (fade-out over the last ~1.0s, subtle
  audio-reactive glow).
- Use local assets for audio and any runtime/media dependencies.
- Run `hyperframes check` before render — it is brag's single gate.
