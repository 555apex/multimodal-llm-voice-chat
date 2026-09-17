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

_CLOCK = re.compile(r"(?<![A-Za-z0-9:])(\d{1,2}):(\d{2})(?::(\d{2}))?(?![0-9:])")
_DATE = re.compile(r"(?<![0-9])(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?![0-9])")
_BUSINESS_WORDS = ("城市对", "跨市路线", "联系倾向", "国省干线", "通行能力", "交通运行", "城市间联系")
_SPOKEN_TIME = re.compile(r"(?:[〇零一二三四五六七八九十]+年[〇零一二三四五六七八九十]+月[〇零一二三四五六七八九十]+日|\d{4}年\d{1,2}月\d{1,2}日|[零一二三四五六七八九十]+点(?:整|[零一二三四五六七八九十]+分(?:[零一二三四五六七八九十]+秒)?)(?:到(?:次日|翌日)?[零一二三四五六七八九十]+点(?:整|[零一二三四五六七八九十]+分(?:[零一二三四五六七八九十]+秒)?))?)")


def _chinese_number(value: int) -> str:
    digits = "零一二三四五六七八九"
    if value < 10:
        return digits[value]
    tens, ones = divmod(value, 10)
    return ("" if tens == 1 else digits[tens]) + "十" + (digits[ones] if ones else "")


def _spoken_clock(match: re.Match) -> str:
    hour, minute = int(match[1]), int(match[2])
    second = int(match[3]) if match[3] is not None else None
    if hour > 23 or minute > 59 or (second is not None and second > 59):
        return match[0]
    spoken = _chinese_number(hour) + "点"
    spoken += _chinese_number(minute) + "分" if minute else ("整" if second is None else "零分")
    if second is not None:
        spoken += _chinese_number(second) + "秒"
    return spoken


def _spoken_date(match: re.Match) -> str:
    from datetime import date
    year, month, day = (int(match[i]) for i in (1, 2, 3))
    try:
        date(year, month, day)
    except ValueError:
        return match[0]
    return "".join("〇一二三四五六七八九"[int(d)] for d in match[1]) + "年" + _chinese_number(month) + "月" + _chinese_number(day) + "日"


def _normalize_times(text: str) -> str:
    # Convert a complete interval first; never interpret a road code or arbitrary dash as time.
    clock = r"\d{1,2}:\d{2}(?::\d{2})?"
    interval = re.compile(r"(?<![A-Za-z0-9:])(" + clock + r")\s*(?:[-–—~～至到])\s*(次日|翌日)?\s*(" + clock + r")(?![0-9:])")
    def replace_interval(match):
        left, right = _CLOCK.fullmatch(match[1]), _CLOCK.fullmatch(match[3])
        spoken_left, spoken_right = _spoken_clock(left), _spoken_clock(right)
        if spoken_left == match[1] or spoken_right == match[3]:
            return match[0]
        return spoken_left + "到" + (match[2] or "") + spoken_right
    text = interval.sub(replace_interval, text)
    text = _DATE.sub(_spoken_date, text)
    return _CLOCK.sub(_spoken_clock, text)


@dataclass(frozen=True)
class NormalizedSpeechText:
    text: str
    replacements: int


def normalize_speech_text(source: str) -> NormalizedSpeechText:
    """Return readable plain text and a count of removed/replaced artifacts."""
    original = source or ""
    text = unicodedata.normalize("NFKC", unescape(original))
    converted = _normalize_times(text)
    def spoken_city_pair(match):
        number = match[1]
        if number.isdigit() and int(number) < 100:
            number = _chinese_number(int(number))
        return number + "组城市间联系"
    converted = re.sub(r"(?<![0-9])([0-9]{1,2}|[零一二三四五六七八九十]+)个城市对", spoken_city_pair, converted)
    time_replacements = int(converted != text)
    text = converted
    replacements = text.count("\ufffd") + time_replacements
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
        protected.extend((match.start(), match.end()) for match in _SPOKEN_TIME.finditer(remaining))
        for word in _BUSINESS_WORDS:
            protected.extend((match.start(), match.end()) for match in re.finditer(re.escape(word), remaining))
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
