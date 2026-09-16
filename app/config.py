"""Application configuration via environment variables."""
import os
import sys
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


def _env_files() -> list[Path]:
    r"""Dev: reads `.env` next to the code. Frozen (.exe): reads the
    user-writable `%LOCALAPPDATA%\FlashcardQuiz\.env` first, then falls back
    to the cwd `.env` (older installs) for anything missing."""
    if getattr(sys, "frozen", False):
        base = Path(os.environ.get("LOCALAPPDATA") or Path.home()) / "FlashcardQuiz"
        return [base / ".env", Path(".env")]
    return [Path(__file__).resolve().parent.parent / ".env"]


class Settings(BaseSettings):
    # OpenAI-compatible API. Works with OpenAI, OpenRouter, and local servers:
    # Ollama (http://127.0.0.1:11434/v1), LM Studio (http://127.0.0.1:1234/v1).
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    # "gpt-4o-mini", "google/gemma-4-26b-a4b-it:free", "gemma3:4b" (Ollama), ...
    llm_model: str = "gpt-4o-mini"
    llm_temperature: float = 0.4

    # No API key? Set mock=true to return deterministic sample cards
    # (useful for testing the full pipeline without spending tokens).
    mock_llm: bool = False

    # OCR.space cloud OCR (free tier: 25,000 req/month)
    # Get a key at https://ocr.space/ocrapi/freekey
    ocrspace_api_key: str = ""

    model_config = SettingsConfigDict(
        env_file=[str(p) for p in _env_files()], env_file_encoding="utf-8"
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
