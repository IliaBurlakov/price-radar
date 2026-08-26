#!/usr/bin/env python3
"""Read-only probe for the public Wildberries shared-basket contract."""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


SHARED_BASKET_API = (
    "https://wbx-api-gateway.wildberries.ru/share-basket/api/v1/basket/{}"
)
SHARE_ID_PATTERN = re.compile(r"[a-z0-9]{10}")
REQUEST_TIMEOUT_SECONDS = 20


class ProbeError(Exception):
    """Expected validation, transport, or response-contract failure."""


def extract_share_id(shared_basket_url: str) -> str:
    parsed = urllib.parse.urlsplit(shared_basket_url)
    if parsed.scheme != "https":
        raise ProbeError("URL scheme must be https")
    if parsed.netloc != "www.wildberries.ru":
        raise ProbeError("URL host must be www.wildberries.ru")
    if parsed.path != "/basket":
        raise ProbeError("URL path must be /basket")
    if parsed.fragment:
        raise ProbeError("URL fragment is not allowed")

    query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
    share_ids = query.get("shareId", [])
    if len(share_ids) != 1 or not share_ids[0]:
        raise ProbeError("URL must contain exactly one non-empty shareId")

    share_id = share_ids[0]
    if SHARE_ID_PATTERN.fullmatch(share_id) is None:
        raise ProbeError("shareId must contain exactly 10 lowercase letters or digits")
    return share_id


def build_api_url(share_id: str) -> str:
    return SHARED_BASKET_API.format(urllib.parse.quote(share_id, safe=""))


def fetch(api_url: str) -> tuple[int, str, bytes]:
    request = urllib.request.Request(
        api_url,
        headers={
            "Accept": "application/json",
            "User-Agent": "PriceRadar-Shared-Basket-Probe/1.0",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(
            request,
            timeout=REQUEST_TIMEOUT_SECONDS,
        ) as response:
            return (
                response.status,
                response.headers.get("Content-Type", ""),
                response.read(),
            )
    except urllib.error.HTTPError as error:
        return (
            error.code,
            error.headers.get("Content-Type", ""),
            error.read(),
        )
    except urllib.error.URLError as error:
        raise ProbeError(f"request failed: {error.reason}") from error


def decode_json(body: bytes) -> object:
    try:
        return json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ProbeError("API response is not valid UTF-8 JSON") from error


def read_items(payload: object) -> list[dict[str, int]]:
    if not isinstance(payload, dict):
        raise ProbeError("API response root must be a JSON object")
    items = payload.get("items")
    if not isinstance(items, list):
        raise ProbeError("successful API response must contain an items array")

    result: list[dict[str, int]] = []
    for index, item in enumerate(items, start=1):
        if not isinstance(item, dict):
            raise ProbeError(f"item {index} must be a JSON object")
        fields = {name: item.get(name) for name in ("nmId", "chrtId", "quantity")}
        if any(isinstance(value, bool) or not isinstance(value, int)
               for value in fields.values()):
            raise ProbeError(
                f"item {index} must contain integer nmId, chrtId, and quantity"
            )
        result.append(fields)
    return result


def print_result(status: int, content_type: str, body: bytes) -> int:
    print(f"HTTP status: {status}")
    payload = decode_json(body)

    if status != 200:
        print("Items: 0")
        if isinstance(payload, dict) and payload.get("error_message"):
            print(f"API error: {payload['error_message']}")
        else:
            print(f"Response: {json.dumps(payload, ensure_ascii=False)}")
        return 2

    if "application/json" not in content_type.lower():
        raise ProbeError(f"unexpected Content-Type: {content_type or '<missing>'}")
    items = read_items(payload)
    print(f"Items: {len(items)}")
    for index, item in enumerate(items, start=1):
        print(
            f"{index}. nmId={item['nmId']}, "
            f"chrtId={item['chrtId']}, quantity={item['quantity']}"
        )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Probe the read-only Wildberries shared-basket endpoint."
    )
    parser.add_argument(
        "shared_basket_url",
        help="https://www.wildberries.ru/basket?shareId=...",
    )
    args = parser.parse_args()

    try:
        share_id = extract_share_id(args.shared_basket_url)
        status, content_type, body = fetch(build_api_url(share_id))
        return print_result(status, content_type, body)
    except ProbeError as error:
        print(f"Probe error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
