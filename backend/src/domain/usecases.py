import re
from typing import List, Optional, Dict, Any

from src.domain.entities import Product, Ingredient, ProductIngredient, IngredientBan, IngredientName, ScanAgentResult

from src.domain.interfaces import (
    IProductRepository, IIngredientRepository, IVisionService, IProductLookupService, IScanAgentService
)

# Countries currently seeded/tracked by the regulatory dataset. Hardcoded here since it
# rarely changes; verified against the live `countries` table when this was added.
TRACKED_COUNTRIES = ["IN", "EU", "US", "AU"]

class SearchProductUseCase:
    def __init__(self, product_repo: IProductRepository):
        self.product_repo = product_repo

    async def execute(self, query: str) -> List[Dict[str, Any]]:
        products = await self.product_repo.search_by_name(query)
        result = []
        for p in products:
            annotated_ingredients = annotate_ingredients(p.ingredients)
            severity = determine_product_severity(annotated_ingredients)
            result.append({
                "product": p,
                "severity": severity,
                "ingredients": annotated_ingredients
            })
        return result

class GetProductDetailsUseCase:
    def __init__(self, product_repo: IProductRepository, lookup_service: IProductLookupService):
        self.product_repo = product_repo
        self.lookup_service = lookup_service

    async def execute(self, barcode: str) -> Optional[Dict[str, Any]]:
        product = await self.product_repo.get_by_barcode(barcode)
        if not product:
            # Fallback to Open Food Facts API lookup
            product = await self.lookup_service.lookup_barcode(barcode)
            if product:
                # Save product locally for future offline lookups
                await self.product_repo.save(product)

        if not product:
            return None

        annotated_ingredients = annotate_ingredients(product.ingredients)
        severity = determine_product_severity(annotated_ingredients)
        return {
            "product": product,
            "severity": severity,
            "ingredients": annotated_ingredients
        }

class ScanImageUseCase:
    def __init__(
        self,
        vision_service: IVisionService,
        product_repo: IProductRepository,
        ingredient_repo: IIngredientRepository,
        lookup_service: IProductLookupService
    ):
        self.vision_service = vision_service
        self.product_repo = product_repo
        self.ingredient_repo = ingredient_repo
        self.lookup_service = lookup_service

    async def execute(self, image_bytes: bytes, mock_type: Optional[str] = None) -> Dict[str, Any]:
        # Step 1: Call Gemini Vision to determine intent & extract fields
        extracted = await self.vision_service.analyze_image(image_bytes, mock_type=mock_type)

        intent = extracted.get("intent", "ingredients")

        if intent == "product":
            barcode = extracted.get("barcode")
            product_name = extracted.get("product_name")
            brand = extracted.get("brand")

            # Try to lookup product
            product = None
            if barcode:
                # Direct barcode lookup
                get_details = GetProductDetailsUseCase(self.product_repo, self.lookup_service)
                product_res = await get_details.execute(barcode)
                if product_res:
                    return {
                        "intent": "product",
                        "match_score": 100,
                        "data": product_res
                    }

            # Search by name if barcode lookup failed or wasn't provided
            search_query = f"{brand or ''} {product_name or ''}".strip()
            if search_query:
                search_usecase = SearchProductUseCase(self.product_repo)
                results = await search_usecase.execute(search_query)
                if results:
                    # Return top match
                    return {
                        "intent": "product",
                        "match_score": 90,
                        "data": results[0]
                    }

            # Fallback if no matching product found: classify text OCR as ingredient list
            intent = "ingredients"

        # Step 2: Handle "ingredients" intent (or fallback from failed product lookup)
        raw_text = extracted.get("ingredients_text") or extracted.get("product_name") or ""
        if not raw_text:
            return {
                "intent": "ingredients",
                "ingredients": [],
                "matching_products": []
            }

        # Parse and match ingredients (shared helper, also used by ScanWithAgentUseCase)
        matched_ingredients = await match_ingredients_from_text(self.ingredient_repo, raw_text)
        matched_names = [ing["name"] for ing in matched_ingredients if ing.get("id")]

        # Secondary action: find products that contain similar ingredients
        scored_products = await self.product_repo.search_by_ingredient_names(matched_names)
        matching_products_scores = []
        for p, ratio in scored_products:
            annotated_p_ings = annotate_ingredients(p.ingredients)
            matching_products_scores.append({
                "product": p,
                "match_score": int(ratio * 100),
                "severity": determine_product_severity(annotated_p_ings)
            })

        return {
            "intent": "ingredients",
            "ingredients": matched_ingredients,
            "matching_products": matching_products_scores[:5]  # Top 5 matches
        }

async def match_ingredients_from_text(ingredient_repo: IIngredientRepository, raw_text: str) -> List[Dict[str, Any]]:
    """
    Parses free-text ingredients (e.g. OCR'd from a label) into tokens, resolves each
    token against the ingredient repository, and annotates/sorts the result by severity.
    Shared between ScanImageUseCase and ScanWithAgentUseCase's "ingredients_only" fallback.
    """
    parsed_tokens = clean_and_split_ingredients(raw_text)
    matched_ingredients = []

    for token in parsed_tokens:
        ingredient = await ingredient_repo.find_by_name_or_synonym(token)
        if ingredient:
            matched_ingredients.append(annotate_ingredient(ingredient))
        else:
            # Add unclassified ingredient as gray badge
            matched_ingredients.append({
                "id": None,
                "name": token,
                "category": None,
                "e_number": None,
                "severity": "gray",
                "matched_bans": []
            })

    # Order by severity: Red -> Amber -> Green/Permitted -> Gray
    severity_rank = {"red": 0, "amber": 1, "green": 2, "permitted": 2, "gray": 3}
    matched_ingredients.sort(key=lambda x: severity_rank.get(x["severity"], 3))
    return matched_ingredients

def score_candidate(name_match: float, ingredient_overlap: float) -> int:
    """
    Combines a name-similarity score and an ingredient-overlap ratio (both 0.0-1.0)
    into a single 0-100 confidence score, weighted 50/50. Used by the scan agent tools
    and unified search to keep scoring consistent instead of duplicating the weighting
    logic in multiple places.
    """
    name_match = max(0.0, min(1.0, name_match))
    ingredient_overlap = max(0.0, min(1.0, ingredient_overlap))
    return int(round((name_match * 0.5 + ingredient_overlap * 0.5) * 100))

def clean_and_split_ingredients(text: str) -> List[str]:
    # Strip common text prefix like "Ingredients:" or "INCI:"
    text = re.sub(r'(?i)^(ingredients|composition|contains|contains less than|inci|ingredients list)\s*:\s*', '', text)
    # Split by commas, semicolons, and newlines that aren't inside parentheses
    # A regex to split on commas or semicolons outside parentheses:
    pattern = r'[;,]|\n'
    # For simplicity, replace parentheses contents or handle them.
    # Let's split by comma outside parentheses
    tokens = []
    current_token = []
    paren_depth = 0
    for char in text:
        if char == '(':
            paren_depth += 1
            current_token.append(char)
        elif char == ')':
            paren_depth -= 1
            current_token.append(char)
        elif char in (',', ';', '\n') and paren_depth == 0:
            token = "".join(current_token).strip()
            if token:
                tokens.append(token)
            current_token = []
        else:
            current_token.append(char)
    if current_token:
        token = "".join(current_token).strip()
        if token:
            tokens.append(token)

    # Clean individual tokens
    cleaned_tokens = []
    for t in tokens:
        # Strip percentage markers e.g. "soy lecithin (3%)" or "salt 2.5%" -> "soy lecithin", "salt"
        t = re.sub(r'\s*\(\s*\d+(\.\d+)?\s*%\s*\)', '', t)
        t = re.sub(r'\s*\d+(\.\d+)?\s*%', '', t)
        # Strip trailing dot/punctuation
        t = t.strip(" .*,")
        if t and len(t) > 1:
            cleaned_tokens.append(t)
    return cleaned_tokens

def annotate_ingredient(ingredient: Ingredient) -> Dict[str, Any]:
    # Find every country that bans or restricts this ingredient, not just one
    matched_bans = []
    severity = "gray"  # No country row means unknown, not verified-safe

    for ban in ingredient.bans:
        if ban.status == "banned":
            severity = "red"
            matched_bans.append(ban)
        elif ban.status == "restricted":
            if severity != "red":
                severity = "amber"
            matched_bans.append(ban)
        elif ban.status == "permitted":
            if severity == "gray":
                severity = "green"

    return {
        "id": ingredient.id,
        "name": ingredient.name,
        "category": ingredient.category,
        "e_number": ingredient.e_number,
        "severity": severity,
        "matched_bans": [
            {
                "country_code": ban.country_code,
                "status": ban.status,
                "condition": ban.condition,
                "regulation_ref": ban.regulation_ref
            } for ban in matched_bans
        ]
    }

def annotate_ingredients(product_ingredients: List[ProductIngredient]) -> List[Dict[str, Any]]:
    annotated = []
    for pi in product_ingredients:
        ann = annotate_ingredient(pi.ingredient)
        ann["order"] = pi.order
        annotated.append(ann)
    # Sort by order
    annotated.sort(key=lambda x: x["order"])
    return annotated

def determine_product_severity(annotated_ingredients: List[Dict[str, Any]]) -> str:
    # If any ingredient is banned (red) -> red
    # If any is restricted (amber) and no banned -> amber
    # Otherwise green
    severities = [ing["severity"] for ing in annotated_ingredients]
    if "red" in severities:
        return "red"
    if "amber" in severities:
        return "amber"
    if "green" in severities:
        return "green"
    return "gray"

def _build_annotated_product(product: Product) -> Dict[str, Any]:
    """Shapes a Product the same way GetProductDetailsUseCase/SearchProductUseCase do."""
    annotated_ingredients = annotate_ingredients(product.ingredients)
    severity = determine_product_severity(annotated_ingredients)
    return {
        "product": product,
        "severity": severity,
        "ingredients": annotated_ingredients
    }

class ScanWithAgentUseCase:
    """
    Replaces ScanImageUseCase as the backing use case for POST /api/scan. Delegates
    image understanding + database lookup entirely to the injected IScanAgentService
    (an LLM agent with tool access to the product/ingredient repositories), then applies
    a confidence-based decision policy over the returned candidates.
    """
    def __init__(self, agent_service: IScanAgentService, ingredient_repo: IIngredientRepository):
        self.agent_service = agent_service
        self.ingredient_repo = ingredient_repo

    async def execute(self, images: List[bytes], mock_type: Optional[str] = None) -> Dict[str, Any]:
        # mock_type isn't part of the IScanAgentService ABC (mirrors how IVisionService's
        # concrete GeminiVisionService accepts it as an extra optional kwarg for testability).
        result: ScanAgentResult = await self.agent_service.analyze_images(images, mock_type=mock_type)

        candidates = sorted(result.candidates, key=lambda c: c[1], reverse=True)

        if candidates:
            top_product, top_score = candidates[0]
            is_confident = top_score >= 85 and (
                len(candidates) == 1 or (top_score - candidates[1][1]) >= 20
            )

            if is_confident:
                return {
                    "match_type": "confident",
                    "product": _build_annotated_product(top_product)
                }

            return {
                "match_type": "suggestions",
                "candidates": [
                    {"product": _build_annotated_product(product), "match_score": score}
                    for product, score in candidates[:5]
                ]
            }

        if result.ingredients_text:
            ingredients = await match_ingredients_from_text(self.ingredient_repo, result.ingredients_text)
            return {"match_type": "ingredients_only", "ingredients": ingredients}

        return {"match_type": "ingredients_only", "ingredients": []}

class UnifiedSearchUseCase:
    """
    Backs GET /api/search. Runs product name/brand search and ranked ingredient search
    in parallel result sets - the two are returned as separate arrays, not interleaved,
    since they represent different kinds of matches (a product vs. an ingredient entry).
    """
    def __init__(self, product_repo: IProductRepository, ingredient_repo: IIngredientRepository):
        self.product_repo = product_repo
        self.ingredient_repo = ingredient_repo

    async def execute(self, query: str) -> Dict[str, Any]:
        products = await SearchProductUseCase(self.product_repo).execute(query)
        ingredients = await self.ingredient_repo.search_ingredients(query)
        annotated_ingredients = [annotate_ingredient(ing) for ing in ingredients]
        return {
            "products": products,
            "ingredients": annotated_ingredients
        }

class GetIngredientDetailsUseCase:
    """
    Backs GET /api/ingredients/{id}. Unlike annotate_ingredient (which only surfaces
    banned/restricted bans for a severity badge), this returns every country's status
    - including "permitted" - alongside the list of countries FoodCheck currently tracks,
    so the client can distinguish "explicitly permitted" from "we don't have data".
    """
    def __init__(self, ingredient_repo: IIngredientRepository):
        self.ingredient_repo = ingredient_repo

    async def execute(self, ingredient_id: str) -> Optional[Dict[str, Any]]:
        ingredient = await self.ingredient_repo.get_by_id(ingredient_id)
        if not ingredient:
            return None
        return {
            "id": ingredient.id,
            "name": ingredient.name,
            "category": ingredient.category,
            "e_number": ingredient.e_number,
            "inci_name": ingredient.inci_name,
            "cas_number": ingredient.cas_number,
            "names": ingredient.names,
            "bans": ingredient.bans,
            "tracked_countries": TRACKED_COUNTRIES
        }
