import requests
import json

NM_ID = 698772746
CHRT_ID = 970559653

url = "https://card.wb.ru/cards/v4/detail"

params = {
    "nm": str(NM_ID),
    "dest": "-1257786",
    "locale": "ru",
    "curr": "rub",
}

headers = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/140.0 Safari/537.36"
    ),
    "Accept": "application/json",
}

try:
    response = requests.get(
        url,
        params=params,
        headers=headers,
        timeout=15,
    )

    print("URL:", response.url)
    print("HTTP status:", response.status_code)
    print("Content-Type:", response.headers.get("Content-Type"))

    if response.status_code != 200:
        print("\nОтвет WB:")
        print(response.text[:2000])
        raise SystemExit

    data = response.json()

    products = data.get("products")
    if products is None:
        products = data.get("data", {}).get("products", [])

    if not products:
        print("\nТовар не найден в ответе WB.")
        print(json.dumps(data, ensure_ascii=False, indent=2)[:5000])
        raise SystemExit

    product = products[0]

    print("\n=== ТОВАР ===")
    print("nmId:", product.get("id") or product.get("nmId"))
    print("Название:", product.get("name"))
    print("Бренд:", product.get("brand"))
    print("Рейтинг:", product.get("reviewRating") or product.get("rating"))
    print("Отзывов:", product.get("feedbacks"))

    print("\n=== РАЗМЕРЫ / ВАРИАНТЫ ===")

    found_chrt = False

    for size in product.get("sizes", []):
        option_id = (
            size.get("optionId")
            or size.get("chrtId")
            or size.get("id")
        )

        print(
            f"optionId={option_id}, "
            f"name={size.get('name')}, "
            f"origName={size.get('origName')}"
        )

        if str(option_id) == str(CHRT_ID):
            found_chrt = True
            print("  ^ нужный вариант из URL")

    if not found_chrt:
        print(
            f"\nВариант chrtId={CHRT_ID} "
            "не найден среди размеров товара."
        )

    print("\n=== ЦЕНА ===")

    # Структура cards/v4 может меняться,
    # поэтому сначала выводим основные price-поля.
    for key in [
        "priceU",
        "salePriceU",
        "price",
        "sizes",
    ]:
        if key in product:
            print(f"{key} =", product[key])

    print("\n=== RAW PRODUCT JSON ===")
    print(json.dumps(product, ensure_ascii=False, indent=2))

except requests.exceptions.Timeout:
    print("TIMEOUT: Wildberries не ответил вовремя.")

except requests.exceptions.ConnectionError as e:
    print("CONNECTION ERROR:")
    print(e)

except requests.exceptions.RequestException as e:
    print("HTTP ERROR:")
    print(e)

except ValueError as e:
    print("WB вернул невалидный JSON:")
    print(e)