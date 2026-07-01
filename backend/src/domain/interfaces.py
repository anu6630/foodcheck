from abc import ABC, abstractmethod
from typing import List, Optional, Dict, Any
from src.domain.entities import Product, Ingredient

class IProductRepository(ABC):
    @abstractmethod
    async def get_by_barcode(self, barcode: str) -> Optional[Product]:
        pass

    @abstractmethod
    async def search_by_name(self, query: str) -> List[Product]:
        pass

    @abstractmethod
    async def save(self, product: Product) -> Product:
        pass

class IIngredientRepository(ABC):
    @abstractmethod
    async def get_by_id(self, ingredient_id: str) -> Optional[Ingredient]:
        pass

    @abstractmethod
    async def find_by_name_or_synonym(self, name: str) -> Optional[Ingredient]:
        pass

    @abstractmethod
    async def save(self, ingredient: Ingredient) -> Ingredient:
        pass

class IVisionService(ABC):
    @abstractmethod
    async def analyze_image(self, image_bytes: bytes) -> Dict[str, Any]:
        """
        Analyzes image and returns a dict with:
        {
          "intent": "product" | "ingredients",
          "product_name": str or None,
          "brand": str or None,
          "barcode": str or None,
          "ingredients_text": str or None
        }
        """
        pass

class IProductLookupService(ABC):
    @abstractmethod
    async def lookup_barcode(self, barcode: str) -> Optional[Product]:
        """
        Query third party APIs (e.g. Open Food Facts API) for barcode details.
        """
        pass
