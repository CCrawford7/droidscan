#!/usr/bin/env python3
"""
EE Network Experience & Mobile Broadband Scraper
==================================================
Targeted scraper for threads about:
  1) Network experiences over mobile phones (coverage, signal, data, calls, SMS, 
     WiFi Calling, VoLTE, APN, roaming, network outages, etc.)
  2) Mobile broadband devices (4G/5G WiFi routers, hotspots, SIM-based routers)

Outputs two JSON files:
  - top_100_most_asked_ee_queries.json   (top 100 by views, network-focused)
  - top_100_unresolved_ee_queries.json   (top 100 unresolved by views, network-focused)

Usage:
  pip install playwright beautifulsoup4
  python3 -m playwright install chromium
  python3 ee_network_scraper.py
"""

import asyncio
import json
import logging
import random
import re
from datetime import datetime, timezone
from pathlib import Path

from bs4 import BeautifulSoup
from playwright.async_api import async_playwright, TimeoutError as PlaywrightTimeout

# ── Config ─────────────────────────────────────────────────────────────────
MAX_PER_DATASET = 100
TARGET_TOTAL = 600                    # keep scraping until we have ~600 net-relevant threads
MIN_PAGES_PER_BOARD = 8              # at least this many pages per board (for breadth across topics)
PAGE_LOAD_TIMEOUT_MS = 30_000
NAV_DELAY_BASE = (0.8, 2.0)
MAX_RETRIES = 2
MAX_PAGES_PER_BOARD = 25
LOG_LEVEL = logging.INFO

BASE_URL = "https://community.ee.co.uk"
USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
)

# ── Network-focused boards (ordered by relevance) ─────────────────────────
NETWORK_BOARDS = [
    # Tier 1 — directly about mobile network / mobile broadband
    ("Mobile Network discussions", "/t5/Mobile-Network-discussions/bd-p/Network-Status"),
    ("Mobile Services",            "/t5/Mobile-Services/bd-p/MobileServices"),
    ("Mobile Data",                "/t5/Mobile-Data/bd-p/4g"),
    ("Mobile Broadband",           "/t5/Mobile-Broadband/bd-p/4GEE-WiFi"),
    ("Roaming",                    "/t5/Roaming/bd-p/Roaming"),
    ("SIM cards & Porting",        "/t5/SIM-cards-Porting/bd-p/sim-cards"),
    # Tier 2 — phone-level network issues
    ("Android Devices",            "/t5/Android-Devices/bd-p/android"),
    ("Apple Devices",              "/t5/Apple-Devices/bd-p/apple"),
    # Tier 3 — broadband overlap with mobile
    ("Broadband & Landline",       "/t5/Broadband-Landline/bd-p/broadband"),
]

# ── Title keywords that indicate relevance to the two topic areas ─────────
# A thread must match at least one keyword (or be in a directly-relevant board)
NETWORK_KEYWORDS = [
    # Mobile signal / coverage
    "signal", "coverage", "reception", "no service", "network",
    "bars", "weak signal", "no signal", "poor signal",
    # Mobile data
    "mobile data", "cellular data", "data not working", "data not work",
    "mobile internet", "4g", "5g", "lte", "3g", "h+",
    "data connection", "internet not working", "mobile data not",
    "can't connect to internet", "no internet", "slow data",
    # WiFi Calling / VoLTE
    "wifi calling", "wifi-calling", "wifi call", "voLTE", "vowifi", "vonr",
    # Calls & SMS
    "can't call", "cannot call", "can't make calls", "cannot make calls",
    "call drop", "call quality", "call failed",
    "can't text", "cannot text", "can't send text", "cannot send text",
    "sms", "mms", "text message", "send text",
    # APN
    "apn", "access point name",
    # Network errors / outages
    "network outage", "network down", "network issue", "network problem",
    "emergency calls", "not registered on network",
    "network error", "connection refused",
    # Speed / performance
    "speed test", "slow internet", "slow speed", "low speed",
    "speed drop", "throughput",
    # Roaming
    "roaming", "data roaming", "roam like home", "roaming charges",
    # SIM / eSIM
    "sim", "esim", "e sim", "physical sim",
    # Mobile broadband devices
    "mobile broadband", "4gee wifi", "5gee", "mini wifi", "wifi mini",
    "hotspot", "portable wifi", "mifi", "dongle",
    "4g router", "5g router", "mobile router", "sim router",
    "broadband", "fibre", "fttp", "fttc",
    "hub manager", "router login", "192.168",
    # WiFi / connectivity (when combined with phone/mobile context is implicit via board)
    "wifi not working", "wifi keeps dropping", "can't connect to wifi",
    "unable to connect", "connection issue",
    # Account / plan impacting service
    "data allowance", "data cap", "unlimited data",
    "tethering", "personal hotspot",
    # Phone-specific
    "iphone network", "android network", "samsung network",
    "pixel network", "oneplus network",
]

# Boards that are inherently relevant regardless of title keywords
ALWAYS_RELEVANT_BOARDS = {
    "Mobile Network discussions",
    "Mobile Data",
    "Mobile Broadband",
    "Roaming",
}


# ── Logging ────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("ee_network_scraper")


# ── Helpers ────────────────────────────────────────────────────────────────

def _clean_number(text: str) -> int:
    text = text.strip().lower().replace(",", "").replace(" ", "")
    if not text:
        return 0
    multiplier = 1
    if text.endswith("k"):
        multiplier = 1000
        text = text[:-1]
    elif text.endswith("m"):
        multiplier = 1_000_000
        text = text[:-1]
    try:
        return int(float(text) * multiplier)
    except ValueError:
        return 0


def _parse_date(time_tag) -> str:
    if not time_tag:
        return ""
    raw = time_tag.get_text(strip=True)
    return " ".join(raw.split()) if raw else ""


def _is_solved(article) -> bool:
    classes = article.get("class", [])
    if any("solved" in c for c in classes):
        return True
    h3 = article.find("h3")
    if h3:
        solved_icon = h3.find("i", class_=lambda c: c and "solved" in c if c else False)
        if solved_icon:
            return True
    solved_tag = article.find(
        lambda t: t.name in ("i", "span", "small")
        and t.get("class")
        and any("solved" in c for c in t.get("class"))
    )
    return bool(solved_tag)


def _extract_category(article) -> str:
    aside = article.find("aside")
    if aside:
        cat_link = aside.find("a", href=lambda h: h and "/bd-p/" in h)
        if cat_link:
            return cat_link.get_text(strip=True)
        for a in aside.find_all("a", href=True):
            txt = a.get_text(strip=True)
            if txt and len(txt) > 3 and "user" not in a.get("href", ""):
                return txt
    return ""


def _is_relevant(title: str, category: str) -> bool:
    """Check if a thread is relevant to mobile-network or mobile-broadband topics."""
    # Always include threads from inherently-relevant boards
    if category in ALWAYS_RELEVANT_BOARDS:
        return True
    # For other boards, check keywords in title
    t = title.lower()
    return any(kw in t for kw in NETWORK_KEYWORDS)


def _extract_thread_data(article, rank: int = 0) -> dict | None:
    """Parse a single <article> element into our schema dict."""
    h3 = article.find("h3")
    if not h3:
        return None
    link = h3.find("a", href=True)
    if not link:
        return None
    title = link.get_text(strip=True)
    url_rel = link["href"]
    full_url = url_rel if url_rel.startswith("http") else BASE_URL + url_rel

    is_solved = _is_solved(article)
    category = _extract_category(article)

    # Relevance filter
    if not _is_relevant(title, category):
        return None

    time_tag = article.find("time")
    date_posted = _parse_date(time_tag)

    views = 0
    replies = 0

    views_li = article.find("li", class_=lambda c: c and "custom-tile-views" in c if c else False)
    if views_li:
        bold = views_li.find("b")
        vt = bold.get_text(strip=True) if bold else views_li.get_text(strip=True)
        vt = re.sub(r"(?i)views?", "", vt).strip()
        views = _clean_number(vt)

    replies_li = article.find("li", class_=lambda c: c and "custom-tile-replies" in c if c else False)
    if replies_li:
        bold = replies_li.find("b")
        rt = bold.get_text(strip=True) if bold else replies_li.get_text(strip=True)
        rt = re.sub(r"(?i)replies?", "", rt).strip()
        replies = _clean_number(rt)

    return {
        "rank": rank,
        "title": title,
        "category": category,
        "url": full_url,
        "views": views,
        "replies": replies,
        "is_solved": is_solved,
        "date_posted": date_posted or "",
        "scraped_at": datetime.now(timezone.utc).isoformat(),
    }


# ── Scraper Core ───────────────────────────────────────────────────────────

class EENetworkScraper:
    """Targeted scraper for mobile-network and mobile-broadband threads."""

    def __init__(self):
        self.all_threads: list[dict] = []
        self.seen_urls: set[str] = set()
        self.stats = {"boards_scraped": 0, "pages_fetched": 0, "filtered_out": 0}

    async def _make_browser(self):
        p = await async_playwright().start()
        browser = await p.chromium.launch(
            headless=True,
            args=["--no-sandbox", "--disable-blink-features=AutomationControlled"],
        )
        ctx = await browser.new_context(
            user_agent=USER_AGENT,
            viewport={"width": 1920, "height": 1080},
            locale="en-GB",
            timezone_id="Europe/London",
            extra_http_headers={"Accept-Language": "en-GB,en;q=0.9"},
        )
        return p, browser, ctx

    async def _close_browser(self, p, browser):
        await browser.close()
        await p.stop()

    async def _random_delay(self):
        await asyncio.sleep(random.uniform(*NAV_DELAY_BASE))

    async def _scrape_board_page(
        self, page, board_name: str, board_path: str, page_num: int
    ) -> list[dict]:
        """Scrape one page of a board sorted by views. Returns relevants-thread dicts."""
        if page_num == 1:
            url = f"{BASE_URL}{board_path}?sort=views"
        else:
            url = f"{BASE_URL}{board_path}/page/{page_num}?sort=views"

        log.info("  Page %s", page_num)

        for attempt in range(1, MAX_RETRIES + 1):
            try:
                await page.goto(url, timeout=PAGE_LOAD_TIMEOUT_MS, wait_until="networkidle")
                await page.wait_for_timeout(2000)
                try:
                    await page.wait_for_selector("article.custom-message-tile", timeout=10_000)
                except PlaywrightTimeout:
                    log.warning("  No tiles found (attempt %s)", attempt)
                    return []
                break
            except PlaywrightTimeout as e:
                log.warning("  Timeout (attempt %s): %s", attempt, e)
                if attempt < MAX_RETRIES:
                    await asyncio.sleep(2 ** attempt)
                else:
                    return []

        html = await page.content()
        soup = BeautifulSoup(html, "html.parser")
        articles = soup.find_all(
            "article", class_=lambda c: c and "custom-message-tile" in c if c else False
        )

        if not articles:
            return []

        threads = []
        for art in articles:
            data = _extract_thread_data(art)
            if data is None:
                self.stats["filtered_out"] += 1
                continue
            if data["url"] not in self.seen_urls:
                self.seen_urls.add(data["url"])
                threads.append(data)

        log.info("  → %d relevant threads", len(threads))
        self.stats["pages_fetched"] += 1
        return threads

    async def _has_next_page(self, page) -> bool:
        try:
            next_links = await page.query_selector_all("li.lia-paging-page-next a")
            return len(next_links) > 0
        except Exception:
            return False

    async def scrape_all(self):
        """Scrape all network-relevant boards, ensuring breadth across topics."""
        log.info(
            "Starting network-focused scrape — %d boards, min %d pages each, target %d threads",
            len(NETWORK_BOARDS), MIN_PAGES_PER_BOARD, TARGET_TOTAL,
        )
        print(f"  {'Board':40s} {'Pages':>5}  {'Found':>6}")
        print(f"  {'-'*40} {'-'*5}  {'-'*6}")

        p, browser, ctx = await self._make_browser()
        page = await ctx.new_page()

        try:
            # Phase 1: hit every board for at least MIN_PAGES_PER_BOARD pages
            for board_name, board_path in NETWORK_BOARDS:
                total_for_board = 0
                for pg in range(1, MAX_PAGES_PER_BOARD + 1):
                    threads = await self._scrape_board_page(page, board_name, board_path, pg)
                    self.all_threads.extend(threads)
                    total_for_board += len(threads)

                    has_next = await self._has_next_page(page)
                    if not has_next or pg >= MIN_PAGES_PER_BOARD:
                        break
                    await self._random_delay()

                self.stats["boards_scraped"] += 1
                print(f"  {board_name:40s} {min(pg, MAX_PAGES_PER_BOARD):>5}  {total_for_board:>6}")
                await self._random_delay()

            # Phase 2: continue scraping boards (prioritising mobile broadband) if we need more threads
            if len(self.all_threads) < TARGET_TOTAL:
                log.info("Phase 2: need %d more threads — deeper scrape on high-yield boards", TARGET_TOTAL - len(self.all_threads))
                # Re-sort boards by yield (descending) so we focus on productive ones
                board_yields = []
                for board_name, board_path in NETWORK_BOARDS:
                    count = sum(1 for t in self.all_threads if t["category"] == board_name)
                    board_yields.append((board_name, board_path, count))
                board_yields.sort(key=lambda x: -x[2])

                for board_name, board_path, _ in board_yields:
                    if len(self.all_threads) >= TARGET_TOTAL:
                        break
                    # Continue from where we left off (next page)
                    total_added = 0
                    for pg in range(MIN_PAGES_PER_BOARD + 1, MAX_PAGES_PER_BOARD + 1):
                        if len(self.all_threads) >= TARGET_TOTAL:
                            break
                        threads = await self._scrape_board_page(page, board_name, board_path, pg)
                        self.all_threads.extend(threads)
                        total_added += len(threads)
                        has_next = await self._has_next_page(page)
                        if not has_next:
                            break
                        await self._random_delay()
                    if total_added > 0:
                        log.info("  +%d more from %s", total_added, board_name)

        finally:
            await self._close_browser(p, browser)

        # Final sort by views descending
        self.all_threads.sort(key=lambda t: t["views"], reverse=True)
        log.info(
            "Done. %d relevant threads collected (%d filtered out by keywords)",
            len(self.all_threads), self.stats["filtered_out"],
        )
        return self.all_threads

    # ── Dataset builders ──

    def build_top_asked(self) -> list[dict]:
        sorted_all = sorted(self.all_threads, key=lambda t: t["views"], reverse=True)
        top = sorted_all[:MAX_PER_DATASET]
        for i, t in enumerate(top, 1):
            t["rank"] = i
        return top

    def build_top_unresolved(self) -> list[dict]:
        unresolved = [t for t in self.all_threads if not t["is_solved"]]
        unresolved.sort(key=lambda t: t["views"], reverse=True)
        top = unresolved[:MAX_PER_DATASET]
        for i, t in enumerate(top, 1):
            t["rank"] = i
        return top

    @staticmethod
    def export_json(data: list[dict], filepath: str):
        path = Path(filepath)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        log.info("Written %d records → %s", len(data), path.resolve())


# ── Entry point ────────────────────────────────────────────────────────────

async def main():
    scraper = EENetworkScraper()
    await scraper.scrape_all()

    top_asked = scraper.build_top_asked()
    scraper.export_json(top_asked, "top_100_most_asked_ee_queries.json")

    top_unresolved = scraper.build_top_unresolved()
    scraper.export_json(top_unresolved, "top_100_unresolved_ee_queries.json")

    print("\n" + "=" * 60)
    print("NETWORK-FOCUSED SCRAPE SUMMARY")
    print("=" * 60)
    print(f"  Total relevant threads: {len(scraper.all_threads)}")
    print(f"  Filtered out (irrelevant): {scraper.stats['filtered_out']}")
    print(f"  Top-100 most-asked:   {top_asked[0]['views']:,} — {top_asked[-1]['views']:,} views")
    solved_count = sum(1 for t in top_unresolved if t["is_solved"])
    print(f"  Top-100 unresolved:   {top_unresolved[0]['views']:,} — {top_unresolved[-1]['views']:,} views")
    print(f"  Unresolved solved count: {solved_count} (should be 0)")
    print()

    # Show category spread
    cats = {}
    for t in top_asked:
        cats[t["category"]] = cats.get(t["category"], 0) + 1
    print("  Category spread (most-asked):")
    for c, n in sorted(cats.items(), key=lambda x: -x[1]):
        print(f"    {c:40s} {n:>3} threads")

    # Show first few
    print("\n  Top 5 most-asked:")
    for t in top_asked[:5]:
        solved = "✓" if t["is_solved"] else "○"
        print(f"    #{t['rank']:>3}  {t['views']:>9,} views  {t['replies']:>5} replies  {solved}  {t['title'][:65]}")

    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
