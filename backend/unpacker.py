"""
JS de-obfuscation utilities.

Includes a Python port of the Dean Edwards p,a,c,k,e,d unpacker used by
most embed providers (cloudnestra, streamcdn, swish.to, etc.) and a few
helpers for grabbing common encoded payloads.
"""

from __future__ import annotations

import re
from typing import Optional

PACKED_RE = re.compile(
    r"""eval\(function\(p,a,c,k,e,(?:r|d)\)\{.*?\}\(\s*['"](.*?)['"]\s*,\s*(\d+|\[\])\s*,\s*(\d+)\s*,\s*['"](.*?)['"]\.split\(['"]\|['"]\)""",
    re.DOTALL,
)


def _base(n: int) -> str:
    """Convert int to dean-edwards base-N representation used by the packer."""
    if n == 0:
        return "0"
    digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    result = ""
    while n > 0:
        result = digits[n % 62] + result
        n //= 62
    return result


def unpack_packed(source: str) -> Optional[str]:
    """Unpack a p,a,c,k,e,d packed JavaScript string. Returns unpacked JS or None."""
    m = PACKED_RE.search(source)
    if not m:
        return None
    payload, _a_raw, c_raw, k_raw = m.group(1), m.group(2), m.group(3), m.group(4)
    try:
        c = int(c_raw)
    except ValueError:
        return None
    words = k_raw.split("|")
    if len(words) < c:
        c = len(words)

    def _replace(match: re.Match) -> str:
        word = match.group(0)
        # Convert dean-edwards base back to int
        n = 0
        for ch in word:
            if ch.isdigit():
                n = n * 62 + int(ch)
            elif ch.islower():
                n = n * 62 + (ord(ch) - ord("a") + 10)
            else:
                n = n * 62 + (ord(ch) - ord("A") + 36)
        if n < c and words[n]:
            return words[n]
        return word

    try:
        return re.sub(r"\b\w+\b", _replace, payload).replace("\\'", "'").replace('\\"', '"').replace("\\/", "/")
    except Exception:
        return None


def unpack_all(source: str) -> str:
    """Apply unpacker iteratively (sometimes nested) and return the
    concatenation of the original source plus any unpacked payloads."""
    pieces = [source]
    cur = source
    for _ in range(3):
        u = unpack_packed(cur)
        if not u:
            break
        pieces.append(u)
        cur = u
    return "\n".join(pieces)
