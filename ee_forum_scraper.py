#!/usr/bin/env python3
"""
EE Community Forum Scraper
==========================
Extracts the top 100 most-viewed threads and top 100 unresolved (no accepted solution)
threads from https://community.ee.co.uk using Playwright + BeautifulSoup.

Outputs two JSON files:
  - top_100_most_asked_ee_queries.json
  - top_100_unresolved_ee_queries.json

Usage:
  python ee_forum_scraper.py
"""

import asyncio
import json
import logging
import random
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from bs4 import BeautifulSoup
from playwright.async_api import async_playwright, TimeoutError as PlaywrightTimeout

# ── Config ─────────────────────────────────────────────────────────────────
MAX_THREADS_PER_DATASET = 100
MAX_PAGES_PER_BOARD = 2           # pages per board (top ~20 threads * 24 boards = ~480, more than enough)
MIN_THREADS_TARGET = 300          # stop scraping once we have this many unique threads
PAGE_LOAD_TIMEOUT_MS = 30_000
NAV_DELAY_BASE = (0.8, 1.8)       # random delay range between page loads
MAX_RETRIES = 2
LOG_LEVEL = logging.INFO

# Known forum boards (category → board-id mapping)
BOARDS = [
    ("EE Community info & News",  "/t5/EE-Community-info-News/bd-p/AboutCommunity"),
    ("General Chit Chat",         "/t5/General-Chit-Chat/bd-p/general_chat"),
    ("Apple Devices",             "/t5/Apple-Devices/bd-p/apple"),
    ("Android Devices",           "/t5/Android-Devices/bd-p/android"),
    ("Other phones & Devices",    "/t5/Other-phones-Devices/bd-p/other"),
    ("Broadband & Landline",      "/t5/Broadband-Landline/bd-p/broadband"),
    ("Digital Home Phone",        "/t5/Digital-Home-Phone/bd-p/DHP"),
    ("TV",                        "/t5/TV/bd-p/TV"),
    ("Gaming",                    "/t5/Gaming/bd-p/EE_Gaming"),
    ("Mobile Broadband",          "/t5/Mobile-Broadband/bd-p/4GEE-WiFi"),
    ("Security",                  "/t5/Security/bd-p/online-safety"),
    ("Mobile Network discussions", "/t5/Mobile-Network-discussions/bd-p/Network-Status"),
    ("Mobile Services",           "/t5/Mobile-Services/bd-p/MobileServices"),
    ("Mobile Data",               "/t5/Mobile-Data/bd-p/4g"),
    ("SIM cards & Porting",       "/t5/SIM-cards-Porting/bd-p/sim-cards"),
    ("Roaming",                   "/t5/Roaming/bd-p/Roaming"),
    ("EE app and website",        "/t5/EE-app-and-website/bd-p/My-EE-account"),
    ("EE One",                    "/t5/EE-One/bd-p/EEOne"),
    ("Contracts & Billing",       "/t5/Contracts-Billing/bd-p/bills"),
    ("Pay as you go",             "/t5/Pay-as-you-go/bd-p/Payasyougo"),
    ("Orders",                    "/t5/Orders/bd-p/my-orders"),
    ("Offers & Add-ons",          "/t5/Offers-Add-ons/bd-p/Deals-offers"),
    ("Small Business",            "/t5/Small-Business/bd-p/small-business"),
    ("Community feedback Group",  "/t5/Community-feedback-Group/bd-p/CommunityFeedbackGroupforum-board"),
]

BASE_URL = "https://community.ee.co.uk"
USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
)

# ── Logging ────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("ee_scraper")


# ── Helpers ────────────────────────────────────────────────────────────────

def _clean_number(text: str) -> int:
    """Convert strings like '1.2k', '1,234', '65' into an integer."""
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


def _parse_date(time_tag) -> str | None:
    """Extract a normalised date string from a <time> element."""
    if not time_tag:
        return None
    # The datetime attribute is a placeholder format string; actual date is in the text content.
    raw = time_tag.get_text(strip=True)
    # Normalise whitespace: "21-12-2022\n                   9:51:01 AM" -> "21-12-2022 9:51:01 AM"
    date_str = " ".join(raw.split()) if raw else ""
    return date_str or None


def _is_solved(article) -> bool:
    """Check if a thread article has an accepted solution indicator."""
    # Check article-level class
    classes = article.get("class", [])
    if any("solved" in c for c in classes):
        return True
    # Check for the <i class="custom-thread-solved"> inside h3
    h3 = article.find("h3")
    if h3:
        solved_icon = h3.find("i", class_=lambda c: c and "solved" in c if c else False)
        if solved_icon:
            return True
    # Check for any solved indicator in the article
    solved_tag = article.find(
        lambda t: t.name in ("i", "span", "small")
        and t.get("class")
        and any("solved" in c for c in t.get("class"))
    )
    if solved_tag:
        return True
    return False


def _extract_category(article) -> str:
    """Extract the board/category name from the article's aside section."""
    aside = article.find("aside")
    if aside:
        cat_link = aside.find("a", href=lambda h: h and "/bd-p/" in h)
        if cat_link:
            return cat_link.get_text(strip=True)
        # Fallback: look for any link inside aside
        for a in aside.find_all("a", href=True):
            txt = a.get_text(strip=True)
            if txt and len(txt) > 3 and "user" not in a.get("href", ""):
                return txt
    return ""


def _extract_thread_data(article, rank: int = 0) -> dict | None:
    """Parse a single <article> element into our schema dict."""
    # ── Title & URL ──
    h3 = article.find("h3")
    if not h3:
        return None
    link = h3.find("a", href=True)
    if not link:
        return None
    title = link.get_text(strip=True)
    url_rel = link["href"]
    full_url = url_rel if url_rel.startswith("http") else BASE_URL + url_rel

    # ── Solved status ──
    is_solved = _is_solved(article)

    # ── Category ──
    category = _extract_category(article)

    # ── Date posted ──
    time_tag = article.find("time")
    date_posted = _parse_date(time_tag)

    # ── Views & Replies ──
    views = 0
    replies = 0

    views_li = article.find("li", class_=lambda c: c and "custom-tile-views" in c if c else False)
    if views_li:
        # The number is in <b> or just text before "Views"
        bold = views_li.find("b")
        views_text = bold.get_text(strip=True) if bold else views_li.get_text(strip=True)
        views_text = re.sub(r"(?i)views?", "", views_text).strip()
        views = _clean_number(views_text)

    replies_li = article.find("li", class_=lambda c: c and "custom-tile-replies" in c if c else False)
    if replies_li:
        bold = replies_li.find("b")
        replies_text = bold.get_text(strip=True) if bold else replies_li.get_text(strip=True)
        replies_text = re.sub(r"(?i)replies?", "", replies_text).strip()
        replies = _clean_number(replies_text)

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

class EEForumScraper:
    """Orchestrates scraping of the EE Khoros community forum."""

    def __init__(self):
        self.all_threads: list[dict] = []        # master list, deduplicated
        self.seen_urls: set[str] = set()

    # ── Browser lifecycle ──

    async def _make_browser(self):
        p = await async_playwright().start()
        browser = await p.chromium.launch(
            headless=True,
            args=[
                "--no-sandbox",
                "--disable-blink-features=AutomationControlled",
            ],
        )
        ctx = await browser.new_context(
            user_agent=USER_AGENT,
            viewport={"width": 1920, "height": 1080},
            locale="en-GB",
            timezone_id="Europe/London",
            permissions=[],
            extra_http_headers={
                "Accept-Language": "en-GB,en;q=0.9",
            },
        )
        return p, browser, ctx

    async def _close_browser(self, p, browser):
        await browser.close()
        await p.stop()

    async def _random_delay(self):
        await asyncio.sleep(random.uniform(*NAV_DELAY_BASE))

    # ── Page scraping logic ──

    async def _scrape_board_page(self, page, board_name: str, board_path: str, page_num: int) -> list[dict]:
        """Scrape one page of a board sorted by views. Returns list of thread dicts."""
        if page_num == 1:
            url = f"{BASE_URL}{board_path}?sort=views"
        else:
            url = f"{BASE_URL}{board_path}/page/{page_num}?sort=views"

        log.info("  Fetching %s  page %s", board_name, page_num)

        for attempt in range(1, MAX_RETRIES + 1):
            try:
                await page.goto(url, timeout=PAGE_LOAD_TIMEOUT_MS, wait_until="networkidle")
                await page.wait_for_timeout(2000)
                # Wait for thread tiles to appear
                try:
                    await page.wait_for_selector("article.custom-message-tile", timeout=10000)
                except PlaywrightTimeout:
                    log.warning("  No thread tiles found on %s page %s (attempt %s)", board_name, page_num, attempt)
                    return []
                break
            except PlaywrightTimeout as e:
                log.warning("  Timeout on %s page %s (attempt %s): %s", board_name, page_num, attempt, e)
                if attempt < MAX_RETRIES:
                    await asyncio.sleep(2 ** attempt)
                else:
                    log.error("  Failed to load %s page %s after %s retries", board_name, page_num, MAX_RETRIES)
                    return []

        html = await page.content()
        soup = BeautifulSoup(html, "html.parser")
        articles = soup.find_all("article", class_=lambda c: c and "custom-message-tile" in c if c else False)

        if not articles:
            log.warning("  No articles on %s page %s", board_name, page_num)
            return []

        threads = []
        for art in articles:
            data = _extract_thread_data(art)
            if data and data["url"] not in self.seen_urls:
                self.seen_urls.add(data["url"])
                threads.append(data)

        log.info("  → %d new threads from %s page %s", len(threads), board_name, page_num)
        return threads

    async def _has_next_page(self, page) -> bool:
        """Check if the current page has a 'Next' pagination link."""
        try:
            next_links = await page.query_selector_all("li.lia-paging-page-next a")
            return len(next_links) > 0
        except Exception:
            return False

    # ── Main scrape loop ──

    async def scrape_all(self):
        """Iterate over all boards, collect enough threads for top-100 analysis."""
        log.info("Starting EE Forum scrape — %d boards", len(BOARDS))
        p, browser, ctx = await self._make_browser()
        page = await ctx.new_page()

        try:
            total_boards = len(BOARDS)
            for idx, (board_name, board_path) in enumerate(BOARDS, 1):
                if len(self.all_threads) >= MIN_THREADS_TARGET and idx > 6:
                    log.info(
                        "  Reached %d threads across %d boards — stopping early",
                        len(self.all_threads), idx - 1,
                    )
                    break

                log.info("[%d/%d] Board: %s", idx, total_boards, board_name)

                for pg in range(1, MAX_PAGES_PER_BOARD + 1):
                    threads = await self._scrape_board_page(page, board_name, board_path, pg)
                    self.all_threads.extend(threads)

                    # Check if we should continue to next page
                    has_next = await self._has_next_page(page)
                    if not has_next:
                        log.info("  No more pages for %s (stopped at page %s)", board_name, pg)
                        break

                    # Short delay between pages
                    await self._random_delay()

                # Progress report
                log.info(
                    "  [Progress] Collected %d unique threads so far",
                    len(self.all_threads),
                )

                await self._random_delay()

        finally:
            await self._close_browser(p, browser)

        # Deduplicate (safety net) and sort
        self.all_threads.sort(key=lambda t: t["views"], reverse=True)
        log.info(
            "Scrape complete. Total unique threads collected: %d",
            len(self.all_threads),
        )
        return self.all_threads

    # ── Dataset builders ──

    def build_top_asked(self) -> list[dict]:
        """Return top 100 threads by view count (all threads)."""
        sorted_threads = sorted(self.all_threads, key=lambda t: t["views"], reverse=True)
        top = sorted_threads[:MAX_THREADS_PER_DATASET]
        for i, t in enumerate(top, 1):
            t["rank"] = i
        return top

    def build_top_unresolved(self) -> list[dict]:
        """Return top 100 unresolved threads (is_solved == False) by view count."""
        unresolved = [t for t in self.all_threads if not t["is_solved"]]
        unresolved.sort(key=lambda t: t["views"], reverse=True)
        top = unresolved[:MAX_THREADS_PER_DATASET]
        for i, t in enumerate(top, 1):
            t["rank"] = i
        return top

    def export_json(self, data: list[dict], filepath: str):
        """Write dataset to a JSON file."""
        path = Path(filepath)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        log.info("Written %d records → %s", len(data), path.resolve())


# ── Entry point ────────────────────────────────────────────────────────────

async def main():
    scraper = EEForumScraper()
    await scraper.scrape_all()

    # Dataset 1: Top 100 most asked (highest view count)
    top_asked = scraper.build_top_asked()
    scraper.export_json(top_asked, "top_100_most_asked_ee_queries.json")

    # Dataset 2: Top 100 unresolved (no accepted solution)
    top_unresolved = scraper.build_top_unresolved()
    scraper.export_json(top_unresolved, "top_100_unresolved_ee_queries.json")

    # Quick summary
    print("\n" + "=" * 60)
    print("SCRAPE SUMMARY")
    print("=" * 60)
    print(f"  Total unique threads collected: {len(scraper.all_threads)}")
    print(f"  Top-100 most-asked range:      {top_asked[0]['views']:,} — {top_asked[-1]['views']:,} views")
    solved_count = sum(1 for t in top_unresolved if t["is_solved"])
    print(f"  Top-100 unresolved range:       {top_unresolved[0]['views']:,} — {top_unresolved[-1]['views']:,} views")
    print(f"  (Unresolved dataset solved count: {solved_count} — should be 0!)")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
