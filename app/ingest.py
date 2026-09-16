"""Multi-modal ingestion: PDF text extraction with cloud OCR (ocr.space)
as primary engine and RapidOCR as local fallback. Supports images too."""

from __future__ import annotations

import io
import threading

from pypdf import PdfReader

_ocr_lock = threading.Lock()
_ocr_engine = None

MAX_PAGES = 5000
OCR_MAX_PAGES = 300


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
    return ocrspace_available() or rapidocr_available()


def _get_rapidocr():
    global _ocr_engine
    if _ocr_engine is None:
        from rapidocr_onnxruntime import RapidOCR
        _ocr_engine = RapidOCR()
    return _ocr_engine


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

def ocr_image(pil_image) -> str:
    """OCR a PIL image, trying ocr.space first then RapidOCR."""
    if ocrspace_available():
        try:
            text = _ocrspace_image(pil_image)
            if text:
                return text
        except Exception:
            pass  # fall through to local
    if rapidocr_available():
        return _rapidocr_image(pil_image)
    raise ValueError(
        "OCR is not available. Set OCRSPACE_API_KEY in .env or install rapidocr_onnxruntime."
    )


def ocr_pdf_page(data: bytes, page_idx: int) -> str:
    """OCR a single page of a PDF, trying ocr.space first then RapidOCR rendering."""
    if ocrspace_available():
        try:
            text = _ocrspace_pdf_page(data, page_idx)
            if text:
                return text
        except Exception:
            pass  # fall through to local
    if rapidocr_available():
        import pypdfium2 as pdfium
        pdf = pdfium.PdfDocument(data)
        try:
            pil = pdf[page_idx].render(scale=2.0).to_pil()
            return _rapidocr_image(pil)
        finally:
            pdf.close()
    raise ValueError(
        "OCR is not available. Set OCRSPACE_API_KEY in .env or install rapidocr_onnxruntime."
    )


# ---------------------------------------------------------------- PDF extraction

def extract_pdf_text(data: bytes, max_pages: int = MAX_PAGES, ocr: bool = True) -> str:
    """Extract text from a PDF (whole books included).

    Pages with no extractable text are treated as scans: when `ocr` is True
    they are OCR'd (ocr.space cloud first, RapidOCR local fallback).
    OCR work is capped at OCR_MAX_PAGES per call.
    """
    reader = PdfReader(io.BytesIO(data))
    chunks: list[str] = []
    scan_pages: list[int] = []

    for i, page in enumerate(reader.pages[:max_pages]):
        text = page.extract_text() or ""
        if text.strip():
            chunks.append(f"[Page {i + 1}]\n{text.strip()}")
        else:
            scan_pages.append(i)

    ocr_pages = scan_pages[:OCR_MAX_PAGES]
    ocr_did_run = False

    if ocr and ocr_pages and any_ocr_available():
        ocr_did_run = True
        for i, page_idx in enumerate(ocr_pages):
            try:
                text = ocr_pdf_page(data, page_idx)
            except Exception:
                continue
            if text:
                chunks.append(f"[Page {page_idx + 1} (OCR)]\n{text}")

        if len(scan_pages) > OCR_MAX_PAGES:
            chunks.append(f"[Note: {len(scan_pages) - OCR_MAX_PAGES} more scanned pages skipped (OCR cap).]")

    if not chunks:
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

    return "\n\n".join(chunks)


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
