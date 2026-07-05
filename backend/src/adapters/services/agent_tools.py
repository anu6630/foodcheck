"""
LangChain tool definitions used by GeminiScanAgentService's tool-calling agent loop.

Design notes:
- Tools are built as closures over repository instances that are constructed fresh
  per-request by the caller (see controllers.py) - there is no module-level/global
  repository state, which keeps this testable and avoids leaking DB sessions across
  requests.
- LangChain tools are normally string-in/string-out so the LLM can reason over the
  result. But the calling code needs real `Product`/`Ingredient` domain objects, not
  free text. Re-parsing the LLM's prose to recover structured data is fragile, so we
  use the common "collector" pattern instead: each tool call both (a) returns a compact
  JSON-serializable summary for the LLM's own reasoning, and (b) appends any structured
  (Product, score) matches it finds to a `collected_candidates` list held in the closure.
  The caller (GeminiScanAgentService) reads that list after the agent loop finishes.
"""
import difflib
import json
import logging
from typing import Any, Dict, List, Tuple

from langchain_core.tools import tool

from src.domain.entities import Product
from src.domain.interfaces import IIngredientRepository, IProductRepository
from src.domain.usecases import score_candidate

logger = logging.getLogger(__name__)


def _product_summary(p: Product) -> Dict[str, Any]:
    return {
        "name": p.name,
        "brand": p.brand,
        "barcode": p.barcode,
        "ingredient_count": len(p.ingredients)
    }


def build_scan_tools(
    product_repo: IProductRepository,
    ingredient_repo: IIngredientRepository
) -> Tuple[list, List[Tuple[Product, int]]]:
    """
    Returns (tools, collected_candidates):
      - tools: a list of LangChain Tool objects bound to the given repos.
      - collected_candidates: a mutable list the tools append (Product, score) tuples
        to as they run. The caller reads this list after the agent finishes to build
        the final ScanAgentResult.candidates.

    Note: this deviates slightly from a plain `-> list` return type because the
    collector list must be visible to the caller too - returning only the tools would
    leave no way to retrieve the structured matches without re-parsing LLM text.
    """
    collected_candidates: List[Tuple[Product, int]] = []

    @tool
    async def search_by_name(query: str) -> str:
        """Search the FoodCheck product database by product name or brand.
        Use this when you have extracted a plausible product name and/or brand from
        the image(s). Returns a compact JSON summary of any matches found."""
        products = await product_repo.search_by_name(query)
        if not products:
            return json.dumps({"matches": []})

        summaries = []
        for p in products:
            name_match = difflib.SequenceMatcher(
                None,
                (query or "").strip().lower(),
                f"{p.brand or ''} {p.name}".strip().lower()
            ).ratio()
            score = score_candidate(name_match, 0.0)
            collected_candidates.append((p, score))
            summaries.append({**_product_summary(p), "score": score})

        summaries.sort(key=lambda s: s["score"], reverse=True)
        return json.dumps({"matches": summaries[:5]})

    @tool
    async def search_by_ingredients(ingredient_names: List[str]) -> str:
        """Search the FoodCheck product database for products whose ingredient list
        overlaps with the given list of ingredient names. Use this when you extracted
        an ingredients list from the image(s) but don't have a confident product
        name/brand. Returns a compact JSON summary of any matches found."""
        scored = await product_repo.search_by_ingredient_names(ingredient_names)
        if not scored:
            return json.dumps({"matches": []})

        summaries = []
        for p, overlap in scored:
            score = score_candidate(0.0, overlap)
            collected_candidates.append((p, score))
            summaries.append({**_product_summary(p), "overlap_ratio": round(overlap, 2), "score": score})

        summaries.sort(key=lambda s: s["score"], reverse=True)
        return json.dumps({"matches": summaries[:5]})

    @tool
    async def search_by_country(country_code: str) -> str:
        """Look up which ingredients are banned or restricted in a given country code
        (e.g. "IN", "EU", "US", "AU"). Useful for reasoning about regulatory status once
        ingredients are known. This tool is informational only - it does not contribute
        product candidates, since a country match alone says nothing about which
        specific product was scanned.

        Perf note: there's no dedicated country-scoped repository query, so this loads
        the full product catalog via search_by_name("") and filters bans in Python.
        That's fine for the current small (~3 product) catalog; a real implementation
        would push this down to a query filtering ingredient_bans.country_code directly
        if the catalog grows."""
        code = (country_code or "").strip().upper()
        all_products = await product_repo.search_by_name("")
        matches = []
        for p in all_products:
            for pi in p.ingredients:
                for ban in pi.ingredient.bans:
                    if ban.country_code == code and ban.status in ("banned", "restricted"):
                        matches.append({
                            "product": p.name,
                            "ingredient": pi.ingredient.name,
                            "status": ban.status
                        })
        return json.dumps({"country": code, "matches": matches[:10]})

    tools = [search_by_name, search_by_ingredients, search_by_country]
    return tools, collected_candidates
