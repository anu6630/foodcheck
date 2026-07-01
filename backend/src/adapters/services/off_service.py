import os
import logging
import httpx
from typing import Optional
from src.domain.interfaces import IProductLookupService
from src.domain.entities import Product, Ingredient, ProductIngredient

logger = logging.getLogger(__name__)

class OpenFoodFactsService(IProductLookupService):
    def __init__(self):
        self.mock_mode = os.getenv("MOCK_GEMINI", "false").lower() == "true" or os.getenv("MOCK_OFF", "false").lower() == "true"

    async def lookup_barcode(self, barcode: str) -> Optional[Product]:
        if self.mock_mode:
            logger.info(f"Open Food Facts Service running in MOCK mode. Querying barcode {barcode}")
            if barcode == "999999999999":
                return None
            return Product(
                id=None,
                name="Mock Product",
                brand="Mock Brand",
                barcode=barcode,
                category="Mock Category",
                source="open_food_facts",
                ingredients=[]
            )

        url = f"https://world.openfoodfacts.org/api/v3/product/{barcode}.json"
        headers = {
            "User-Agent": "FoodCheckApp - PythonBackend - Version 1.0"
        }
        
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(url, headers=headers)
                if response.status_code != 200:
                    logger.warning(f"Open Food Facts API returned status {response.status_code} for barcode {barcode}")
                    return None
                
                data = response.json()
                if "product" not in data or not data["product"]:
                    logger.info(f"Barcode {barcode} not found on Open Food Facts")
                    return None
                
                prod_data = data["product"]
                
                # Extract basic product details
                name = prod_data.get("product_name") or prod_data.get("product_name_en") or "Unknown Product"
                brand = prod_data.get("brands")
                # Split brand by comma and take first if multiple
                if brand:
                    brand = brand.split(",")[0].strip()
                
                category = prod_data.get("categories")
                if category:
                    category = category.split(",")[0].strip()
                
                # Extract ingredients list
                ingredients_list = []
                off_ingredients = prod_data.get("ingredients")
                
                if off_ingredients and isinstance(off_ingredients, list):
                    # We have structured ingredients
                    for i, ing in enumerate(off_ingredients):
                        raw_id = ing.get("id") or ""
                        # Strip "en:" or "fr:" prefix
                        cleaned_id = raw_id.split(":")[-1].strip().lower().replace(" ", "-") if raw_id else ""
                        ing_name = ing.get("text") or cleaned_id.replace("-", " ").title()
                        
                        if cleaned_id and ing_name:
                            # Try to extract E-number
                            e_number = None
                            if cleaned_id.startswith("e") and cleaned_id[1:].isdigit():
                                e_number = cleaned_id.upper()
                            
                            ingredient = Ingredient(
                                id=cleaned_id,
                                name=ing_name,
                                e_number=e_number
                            )
                            ingredients_list.append(ProductIngredient(
                                ingredient=ingredient,
                                order=i
                            ))
                else:
                    # Fallback to parsing raw ingredients text
                    ingredients_text = prod_data.get("ingredients_text") or prod_data.get("ingredients_text_en") or ""
                    if ingredients_text:
                        from src.domain.usecases import clean_and_split_ingredients
                        parsed_names = clean_and_split_ingredients(ingredients_text)
                        for i, name_token in enumerate(parsed_names):
                            cleaned_id = name_token.lower().replace(" ", "-").strip(".*")
                            ingredient = Ingredient(
                                id=cleaned_id,
                                name=name_token
                            )
                            ingredients_list.append(ProductIngredient(
                                ingredient=ingredient,
                                order=i
                            ))
                
                return Product(
                    id=None,
                    name=name,
                    brand=brand,
                    barcode=barcode,
                    category=category,
                    source="open_food_facts",
                    ingredients=ingredients_list
                )
                
        except Exception as e:
            logger.error(f"Error querying Open Food Facts API: {e}")
            return None
