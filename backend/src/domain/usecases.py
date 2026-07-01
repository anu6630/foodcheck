import re
from typing import List, Optional, Dict, Any

from src.domain.entities import Product, Ingredient, ProductIngredient, IngredientBan, IngredientName

from src.domain.interfaces import IProductRepository, IIngredientRepository, IVisionService, IProductLookupService

class SearchProductUseCase:
    def __init__(self, product_repo: IProductRepository):
        self.product_repo = product_repo

    async def execute(self, query: str, countries: List[str]) -> List[Dict[str, Any]]:
        products = await self.product_repo.search_by_name(query)
        result = []
        for p in products:
            annotated_ingredients = annotate_ingredients(p.ingredients, countries)
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

    async def execute(self, barcode: str, countries: List[str]) -> Optional[Dict[str, Any]]:
        product = await self.product_repo.get_by_barcode(barcode)
        if not product:
            # Fallback to Open Food Facts API lookup
            product = await self.lookup_service.lookup_barcode(barcode)
            if product:
                # Save product locally for future offline lookups
                await self.product_repo.save(product)

        if not product:
            return None

        annotated_ingredients = annotate_ingredients(product.ingredients, countries)
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

    async def execute(self, image_bytes: bytes, countries: List[str], mock_type: Optional[str] = None) -> Dict[str, Any]:
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
                product_res = await get_details.execute(barcode, countries)
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
                results = await search_usecase.execute(search_query, countries)
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

        # Parse and match ingredients
        parsed_tokens = clean_and_split_ingredients(raw_text)
        matched_ingredients = []
        matched_ids = []

        for token in parsed_tokens:
            ingredient = await self.ingredient_repo.find_by_name_or_synonym(token)
            if ingredient:
                annotated = annotate_ingredient(ingredient, countries)
                matched_ingredients.append(annotated)
                matched_ids.append(ingredient.id)
            else:
                # Add unclassified ingredient as gray badge
                matched_ingredients.append({
                    "id": None,
                    "name": token,
                    "category": None,
                    "e_number": None,
                    "severity": "gray",
                    "matched_ban": None
                })

        # Order by severity: Red -> Amber -> Green/Permitted -> Gray
        severity_rank = {"red": 0, "amber": 1, "green": 2, "permitted": 2, "gray": 3}
        matched_ingredients.sort(key=lambda x: severity_rank.get(x["severity"], 3))

        # Secondary action: Find products that contain similar ingredients
        # Let's search all products in DB and find matches
        all_products = await self.product_repo.search_by_name("")
        matching_products_scores = []
        for p in all_products:
            p_ing_ids = [pi.ingredient.id for pi in p.ingredients]
            intersect = set(p_ing_ids).intersection(set(matched_ids))
            if intersect:
                # Match score represents the percentage of matching ingredients
                score = int((len(intersect) / max(len(p_ing_ids), 1)) * 100)
                # Annotate product
                annotated_p_ings = annotate_ingredients(p.ingredients, countries)
                matching_products_scores.append({
                    "product": p,
                    "match_score": score,
                    "severity": determine_product_severity(annotated_p_ings)
                })

        # Sort matching products by score descending
        matching_products_scores.sort(key=lambda x: x["match_score"], reverse=True)

        return {
            "intent": "ingredients",
            "ingredients": matched_ingredients,
            "matching_products": matching_products_scores[:5]  # Top 5 matches
        }

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

def annotate_ingredient(ingredient: Ingredient, countries: List[str]) -> Dict[str, Any]:
    # Find relevant bans for selected countries
    relevant_bans = []
    severity = "green"  # Default is green
    matched_ban = None

    for ban in ingredient.bans:
        if ban.country_code in countries:
            relevant_bans.append(ban)
            # Escalate severity: red > amber > green
            if ban.status == "banned":
                severity = "red"
                matched_ban = ban
            elif ban.status == "restricted" and severity != "red":
                severity = "amber"
                matched_ban = ban

    return {
        "id": ingredient.id,
        "name": ingredient.name,
        "category": ingredient.category,
        "e_number": ingredient.e_number,
        "severity": severity,
        "matched_ban": {
            "country_code": matched_ban.country_code,
            "status": matched_ban.status,
            "condition": matched_ban.condition,
            "regulation_ref": matched_ban.regulation_ref
        } if matched_ban else None
    }

def annotate_ingredients(product_ingredients: List[ProductIngredient], countries: List[str]) -> List[Dict[str, Any]]:
    annotated = []
    for pi in product_ingredients:
        ann = annotate_ingredient(pi.ingredient, countries)
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
    return "green"
