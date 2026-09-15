"""Deterministic cleanup and semantic chunking for text sent to TTS models."""
from __future__ import annotations

from dataclasses import dataclass
from html import unescape
import re
import unicodedata


_FENCED_CODE = re.compile(r"```[\s\S]*?```")
_INLINE_CODE = re.compile(r"`([^`]+)`")
# Strong emphasis (**x** / __x__) is stripped before weak emphasis (*x* / _x_).
# The earlier single-pass form used a (?<!\w) lookbehind, which fails after CJK
# characters because \w matches them, so "执行**应急预案**" kept literal stars.
_MARKDOWN_STRONG = re.compile(r"\*\*(.+?)\*\*|__(.+?)__", re.DOTALL)
_MARKDOWN_EMPHASIS = re.compile(r"\*([^*\n]+?)\*|(?<![A-Za-z0-9_])_([^_\n]+?)_(?![A-Za-z0-9_])")
_MARKDOWN_HEADING = re.compile(r"(?m)^[ \t]{0,3}#{1,6}[ \t]*")
_MARKDOWN_IMAGE = re.compile(r"!\[([^\]]*)\]\([^)]*\)")
_MARKDOWN_LINK = re.compile(r"\[([^\]]+)\]\([^)]*\)")
_URL = re.compile(r"(?:https?://|www\.)\S+", re.IGNORECASE)
_HTML_TAG = re.compile(r"<[^>]+>")
_LIST_PREFIX = re.compile(r"(?m)^\s*(?:[-*•]+|\d+[.)、])\s*")
_HORIZONTAL_RULE = re.compile(r"(?m)^[ \t]*(?:\*{3,}|-{3,}|_{3,})[ \t]*$")
_BLOCKQUOTE = re.compile(r"(?m)^[ \t]{0,3}>[ \t]?")
_SPACE = re.compile(r"[ \t\f\v]+")
_BLANK_LINES = re.compile(r"\s*\n\s*")
_REPEATED_PUNCTUATION = re.compile(r"([，。！？；：、,.!?;:])\1+")
_SENTENCE = re.compile(r"[^。！？；：\n]+[。！？；：]?|\n+")
_SOFT_BOUNDARY = re.compile(r"[，、,]\s*")
_PROTECTED_TOKEN = re.compile(
    r"(?i)(?:\b[A-Z]{1,4}\d{1,5}(?:[+-]\d+(?:\.\d+)?)?\b"
    r"|\b\d{1,4}(?:[-/.年]\d{1,2}){1,2}(?:日)?\b"
    r"|\b\d{1,2}:\d{2}(?::\d{2})?\b"
    r"|(?<!\w)[+-]?\d+(?:\.\d+)?\s*(?:%|mm|cm|km|m|公里|米|毫米|厘米|小时|分钟|秒)(?!\w))"
)


@dataclass(frozen=True)
class NormalizedSpeechText:
    text: str
    replacements: int


def normalize_speech_text(source: str) -> NormalizedSpeechText:
    """Return readable plain text and a count of removed/replaced artifacts."""
    original = source or ""
    text = unicodedata.normalize("NFKC", unescape(original))
    replacements = text.count("\ufffd")
    text = text.replace("\ufffd", "")

    substitutions = (
        (_FENCED_CODE, "。"),
        (_MARKDOWN_IMAGE, r"\1"),
        (_MARKDOWN_LINK, r"\1"),
        (_INLINE_CODE, r"\1"),
        (_MARKDOWN_STRONG, lambda match: match.group(1) or match.group(2)),
        (_MARKDOWN_EMPHASIS, lambda match: match.group(1) or match.group(2)),
        (_MARKDOWN_HEADING, ""),
        (_HORIZONTAL_RULE, "。"),
        (_BLOCKQUOTE, ""),
        (_URL, "链接"),
        (_HTML_TAG, " "),
        (_LIST_PREFIX, ""),
    )
    for pattern, replacement in substitutions:
        text, count = pattern.subn(replacement, text)
        replacements += count

    cleaned: list[str] = []
    for character in text:
        category = unicodedata.category(character)
        if category.startswith("C") and character not in "\n\t":
            replacements += 1
            continue
        if character in "{}[]|^~":
            cleaned.append(" ")
            replacements += 1
        elif character == "&":
            cleaned.append("和")
            replacements += 1
        else:
            cleaned.append(character)
    text = "".join(cleaned)
    # Safety net: any markdown punctuation still present is never speakable.
    text, residual = re.subn(r"[*_`~]", "", text)
    replacements += residual
    text = text.translate(str.maketrans({",": "，", ";": "；", ":": "：", "!": "！", "?": "？"}))
    text = re.sub(r"(?<=\d)：(?=\d)", ":", text)
    text = _SPACE.sub(" ", text)
    text = _BLANK_LINES.sub("。", text)
    text = _REPEATED_PUNCTUATION.sub(r"\1", text)
    text = re.sub(r"\s*([，。！？；：、,.!?;:])\s*", r"\1", text)
    text = re.sub(r"\s*和\s*", "和", text)
    text = text.strip(" ，；、")
    return NormalizedSpeechText(text=text, replacements=replacements)


def semantic_segments(text: str, maximum: int = 100, minimum: int = 24) -> list[str]:
    """Split on natural pauses without cutting identifiers, values, dates or units."""
    if maximum < 16:
        raise ValueError("maximum must be at least 16")
    normalized = normalize_speech_text(text).text
    if not normalized:
        return []

    sentences = [part.strip() for part in _SENTENCE.findall(normalized) if part.strip()]
    result: list[str] = []
    current = ""
    for sentence in sentences:
        for piece in _split_long(sentence, maximum, minimum):
            if current and len(current) + len(piece) > maximum:
                result.append(current)
                current = piece
            else:
                current += piece
    if current:
        result.append(current)
    return result


def _split_long(text: str, maximum: int, minimum: int) -> list[str]:
    pieces: list[str] = []
    remaining = text
    while len(remaining) > maximum:
        protected = [(match.start(), match.end()) for match in _PROTECTED_TOKEN.finditer(remaining)]
        candidates = [
            match.end() for match in _SOFT_BOUNDARY.finditer(remaining, 0, maximum + 1)
            if match.end() >= minimum and not _inside(match.end(), protected)
        ]
        cut = candidates[-1] if candidates else _safe_cut(maximum, protected)
        if cut <= 0:
            cut = maximum
        pieces.append(remaining[:cut].strip())
        remaining = remaining[cut:].strip()
    if remaining:
        pieces.append(remaining)
    return pieces


def _inside(position: int, spans: list[tuple[int, int]]) -> bool:
    return any(start < position < end for start, end in spans)


def _safe_cut(preferred: int, spans: list[tuple[int, int]]) -> int:
    for start, end in spans:
        if start < preferred < end:
            return start if start >= 16 else end
    return preferred
