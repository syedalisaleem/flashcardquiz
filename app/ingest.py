"""Multi-modal ingestion: PDF text extraction with optional OCR for scanned
pages, plus direct OCR for image uploads (photos / handwritten notes)."""

from __future__ import annotations

import io
import threading

from pypdf import PdfReader

_ocr_lock = threading.Lock()
_ocr_engine = None  # type: Optional[object]

OCR_DEP_MSG = (
    "This PDF has no extractable text (scanned). OCR is not installed — "
    "run: pip install rapidocr_onnxruntime pypdfium2"
)

MAX_PAGES = 5000   # a whole book fits under this
OCR_MAX_PAGES = 300  # cap OCR work per job; beyond that, note the limit


def ocr_available() -> bool:
    try:
        import rapidocr_onnxruntime  # noqa: F401

        return True
    except ImportError:
        return False


def _get_ocr():
    global _ocr_engine
    if _ocr_engine is None:
        from rapidocr_onnxruntime import RapidOCR

        _ocr_engine = RapidOCR()
    return _ocr_engine


def _ocr_image(pil_image) -> str:
    """Run RapidOCR on a PIL image, returning joined text lines (best-first)."""
    import numpy as np

    with _ocr_lock:
        result, _ = _get_ocr()(np.array(pil_image.convert("RGB")))
    if not result:
        return ""
    lines = [t.strip() for _, t, conf in result if conf >= 0.5 and t and t.strip()]
    return "\n".join(lines)


def _render_pages(data: bytes, indices: list[int], scale: float = 2.0):
    """Yield PIL images for the given 0-based page indices of a PDF."""
    import pypdfium2 as pdfium

    pdf = pdfium.PdfDocument(data)
    try:
        for i in indices:
            yield pdf[i].render(scale=scale).to_pil()
    finally:
        pdf.close()


def extract_pdf_text(data: bytes, max_pages: int = MAX_PAGES, ocr: bool = True) -> str:
    """Extract text from a PDF (whole books included).

    Pages with no extractable text are treated as scans: when `ocr` is True
    (and the OCR engine is installed) they are rendered to images and read
    with OCR, keeping per-page markers for source grounding. OCR work is
    capped at OCR_MAX_PAGES pages per call to keep runtime sane. A page that
    fails to OCR is skipped rather than aborting the whole file.
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
    if ocr and ocr_pages and ocr_available():
        ocr_did_run = True
        for i, pil in enumerate(_render_pages(data, ocr_pages)):
            try:
                text = _ocr_image(pil)
            except Exception:
                continue  # unreadable page; don't abort the whole upload
            if text:
                chunks.append(f"[Page {ocr_pages[i] + 1} (OCR)]\n{text}")
        if len(scan_pages) > OCR_MAX_PAGES:
            chunks.append(f"[Note: {len(scan_pages) - OCR_MAX_PAGES} more scanned pages skipped (OCR cap).]")

    if not chunks:
        if ocr and not ocr_available():
            raise ValueError(OCR_DEP_MSG)
        if ocr and ocr_did_run:
            raise ValueError("OCR ran but found no readable text in this PDF.")
        raise ValueError(
            "No text found — this PDF looks scanned. Tick \u201cOCR scanned pages\u201d to read it."
        )

    return "\n\n".join(chunks)


def extract_image_text(data: bytes) -> str:
    """OCR a photo/scan (PNG, JPEG, WEBP, BMP, TIFF) directly."""
    from PIL import Image

    if not ocr_available():
        raise ValueError(
            "Image OCR is not installed — run: pip install rapidocr_onnxruntime"
        )
    img = Image.open(io.BytesIO(data))
    return _ocr_image(img)
