"""Multi-modal ingestion: PDF text extraction with RapidOCR (local) as the
primary engine and ocr.space as cloud fallback. Supports images too."""

from __future__ import annotations

import io
import threading

from pypdf import PdfReader

_ocr_lock = threading.Lock()
_ocr_engine = None

MAX_PAGES = 5000
OCR_MAX_PAGES = 300

# A page whose native extraction yields fewer words than this *and* fewer
# compact characters is treated as a scan (page numbers, stray headers) and
# routed through OCR instead of being trusted as real content.
_JUNK_MIN_WORDS = 4
_JUNK_MIN_CHARS = 20


def ocrspace_available() -> bool:
    from .config import get_settings
    return bool(get_settings().ocrspace_api_key)


def rapidocr_available() -> bool:
    try:
        import rapidocr_onnxruntime  # noqa: F401
        return True
    except ImportError:
        return False


def any_ocr_available() -> bool:
    return bool(_want_rapidocr() and rapidocr_available()) or bool(
        _want_ocrspace() and ocrspace_available()
    )


def _provider() -> str:
    from .config import get_settings

    try:
        return (get_settings().ocr_provider or "auto").strip().lower()
    except Exception:
        return "auto"


def _want_rapidocr() -> bool:
    return _provider() in ("auto", "rapidocr")


def _want_ocrspace() -> bool:
    return _provider() in ("auto", "ocrspace")


def _get_rapidocr():
    global _ocr_engine
    if _ocr_engine is None:
        from rapidocr_onnxruntime import RapidOCR
        _ocr_engine = RapidOCR()
    return _ocr_engine


def _preprocess(pil_image):
    """Grayscale + autocontrast + upscale so thin strokes survive OCR.

    Scanned pages are often low-contrast gray on gray; RapidOCR in particular
    drops glyphs it cannot segment. Up scaling is capped at 3x to bound memory.
    """
    from PIL import Image, ImageOps

    img = pil_image.convert("RGB")
    w, h = img.size
    longest = max(w, h, 1)
    if longest < 1200:
        scale = min(1200.0 / longest, 3.0)
        if scale > 1.05:
            img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    gray = ImageOps.autocontrast(img.convert("L"))
    return gray.convert("RGB")


def _dedupe_text(text: str) -> str:
    """Strip OCR noise: collapse repeated lines (doubled rendering) and cap how
    often a line may recur (running headers/footers repeated on every page).

    Never returns an empty string for non-empty input — deduping must not
    destroy the only text we managed to read.
    """
    if not text:
        return text
    out: list[str] = []
    seen: dict[str, int] = {}
    prev = None
    for raw in text.splitlines():
        stripped = raw.strip()
        if not stripped:
            out.append(raw)
            continue
        if stripped == prev:
            continue
        key = stripped.lower()
        count = seen.get(key, 0) + 1
        seen[key] = count
        if count > 3:
            continue
        out.append(raw)
        prev = stripped
    result = "\n".join(out).strip()
    return result if result else text.strip()


# ---------------------------------------------------------------- ocr.space

def _ocrspace_image(pil_image) -> str:
    """OCR a PIL image via ocr.space cloud API."""
    import httpx
    from .config import get_settings

    buf = io.BytesIO()
    pil_image.convert("RGB").save(buf, format="PNG")
    b64 = __import__("base64").b64encode(buf.getvalue()).decode()

    resp = httpx.post(
        "https://api.ocr.space/parse/image",
        data={
            "apikey": get_settings().ocrspace_api_key,
            "base64Image": f"data:image/png;base64,{b64}",
            "language": "eng",
            "isOverlayRequired": "false",
            "OCREngine": "2",
        },
        timeout=30.0,
    )
    resp.raise_for_status()
    result = resp.json()

    if result.get("IsErroredOnProcessing"):
        msgs = result.get("ErrorMessage", ["OCR failed"])
        raise RuntimeError(f"ocr.space error: {msgs}")

    texts = []
    for p in result.get("ParsedResults", []):
        txt = p.get("ParsedText", "")
        if txt:
            texts.append(txt)
    return "\n".join(texts).strip()


def _ocrspace_pdf_page(data: bytes, page_idx: int) -> str:
    """OCR a single PDF page via ocr.space."""
    import httpx
    from .config import get_settings

    b64 = __import__("base64").b64encode(data).decode()

    resp = httpx.post(
        "https://api.ocr.space/parse/image",
        data={
            "apikey": get_settings().ocrspace_api_key,
            "base64Image": f"data:application/pdf;base64,{b64}",
            "language": "eng",
            "isOverlayRequired": "false",
            "OCREngine": "2",
            "pageIndex": str(page_idx),
        },
        timeout=60.0,
    )
    resp.raise_for_status()
    result = resp.json()

    if result.get("IsErroredOnProcessing"):
        msgs = result.get("ErrorMessage", ["OCR failed"])
        raise RuntimeError(f"ocr.space error: {msgs}")

    texts = []
    for p in result.get("ParsedResults", []):
        txt = p.get("ParsedText", "")
        if txt:
            texts.append(txt)
    return "\n".join(texts).strip()


# ---------------------------------------------------------------- rapidocr (local fallback)

def _rapidocr_image(pil_image) -> str:
    import numpy as np
    with _ocr_lock:
        result, _ = _get_rapidocr()(np.array(pil_image.convert("RGB")))
    if not result:
        return ""
    lines = [t.strip() for _, t, conf in result if conf >= 0.5 and t and t.strip()]
    return "\n".join(lines)


# ---------------------------------------------------------------- unified OCR entry points

def _run_chain(pil_image) -> str:
    """RapidOCR (local, free) first, ocr.space (cloud) as fallback.

    Returns deduped text; raises ValueError when nothing usable was read.
    """
    img = _preprocess(pil_image)
    errors: list[str] = []

    if _want_rapidocr() and rapidocr_available():
        try:
            text = _dedupe_text(_rapidocr_image(img))
            if text.strip():
                return text
            errors.append("rapidocr found no text")
        except Exception as exc:  # noqa: BLE001 — engine failures fall through
            errors.append(f"rapidocr: {exc}")

    if _want_ocrspace() and ocrspace_available():
        try:
            text = _dedupe_text(_ocrspace_image(img))
            if text.strip():
                return text
            errors.append("ocr.space found no text")
        except Exception as exc:  # noqa: BLE001
            errors.append(f"ocr.space: {exc}")

    detail = "; ".join(errors)
    raise ValueError(
        "OCR is not available or found no readable text."
        + (f" ({detail})" if detail else "")
        + " Set OCRSPACE_API_KEY in .env or install rapidocr_onnxruntime."
    )


def ocr_image(pil_image) -> str:
    """OCR a PIL image via the configured engine chain."""
    return _run_chain(pil_image)


def ocr_pdf_page(data: bytes, page_idx: int) -> str:
    """OCR a single page of a PDF: render locally and run RapidOCR first,
    falling back to ocr.space when the local engine is unavailable."""
    if _want_rapidocr() and rapidocr_available():
        import pypdfium2 as pdfium

        text = ""
        pdf = pdfium.PdfDocument(data)
        try:
            pil = pdf[page_idx].render(scale=2.0).to_pil()
            text = _dedupe_text(_rapidocr_image(_preprocess(pil)))
        except Exception:  # noqa: BLE001 — fall through to the cloud
            text = ""
        finally:
            pdf.close()
        if text.strip():
            return text

    if _want_ocrspace() and ocrspace_available():
        text = _dedupe_text(_ocrspace_pdf_page(data, page_idx))
        if text.strip():
            return text

    raise ValueError(
        "OCR is not available or found no readable text. "
        "Set OCRSPACE_API_KEY in .env or install rapidocr_onnxruntime."
    )


def _is_junk_text(text: str) -> bool:
    """True when native extraction produced only noise (page numbers, stray
    headers) rather than a real paragraph — i.e. the page is effectively a scan."""
    if not text.strip():
        return True
    if len(text.split()) >= _JUNK_MIN_WORDS:
        return False
    return len("".join(text.split())) < _JUNK_MIN_CHARS


# ---------------------------------------------------------------- PDF extraction

def extract_pdf_text(data: bytes, max_pages: int = MAX_PAGES, ocr: bool = True) -> str:
    """Extract text from a PDF (whole books included).

    Pages whose native extraction is empty — or is only noise such as a page
    number or running header — are treated as scans: when `ocr` is True they
    are OCR'd (RapidOCR local first, ocr.space fallback).
    OCR work is capped at OCR_MAX_PAGES per call.
    """
    reader = PdfReader(io.BytesIO(data))
    pages: dict[int, str] = {}
    native: dict[int, str] = {}
    scan_pages: list[int] = []

    for i, page in enumerate(reader.pages[:max_pages]):
        text = (page.extract_text() or "").strip()
        if text and not _is_junk_text(text):
            pages[i] = f"[Page {i + 1}]\n{text}"
        else:
            scan_pages.append(i)
            if text:
                native[i] = text

    ocr_pages = scan_pages[:OCR_MAX_PAGES]
    ocr_did_run = False

    if ocr and ocr_pages and any_ocr_available():
        ocr_did_run = True
        for page_idx in ocr_pages:
            text = ""
            try:
                text = ocr_pdf_page(data, page_idx).strip()
            except Exception:
                text = ""
            if text:
                pages[page_idx] = f"[Page {page_idx + 1} (OCR)]\n{text}"
            elif native.get(page_idx):
                # OCR found nothing; keep whatever native extraction produced.
                pages[page_idx] = f"[Page {page_idx + 1}]\n{native[page_idx]}"

    if not pages:
        if ocr and not any_ocr_available():
            raise ValueError(
                "This PDF has no extractable text (scanned). "
                "Set OCRSPACE_API_KEY in .env or run: pip install rapidocr_onnxruntime pypdfium2"
            )
        if ocr and ocr_did_run:
            raise ValueError("OCR ran but found no readable text in this PDF.")
        raise ValueError(
            "No text found — this PDF looks scanned. Tick \"OCR scanned pages\" to read it."
        )

    out = "\n\n".join(pages[k] for k in sorted(pages))
    if ocr and ocr_did_run and len(scan_pages) > OCR_MAX_PAGES:
        out += f"\n\n[Note: {len(scan_pages) - OCR_MAX_PAGES} more scanned pages skipped (OCR cap).]"
    return out


# ---------------------------------------------------------------- image extraction

def extract_image_text(data: bytes) -> str:
    """OCR a photo/scan (PNG, JPEG, WEBP, BMP, TIFF) directly."""
    from PIL import Image

    if not any_ocr_available():
        raise ValueError(
            "Image OCR is not available. Set OCRSPACE_API_KEY in .env or install rapidocr_onnxruntime."
        )
    img = Image.open(io.BytesIO(data))
    return ocr_image(img)
