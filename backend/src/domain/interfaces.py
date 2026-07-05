from abc import ABC, abstractmethod
from typing import List, Optional, Dict, Any, Tuple
from src.domain.entities import Product, Ingredient, ScanAgentResult

class IProductRepository(ABC):
    @abstractmethod
    async def get_by_barcode(self, barcode: str) -> Optional[Product]:
        pass

    @abstractmethod
    async def search_by_name(self, query: str) -> List[Product]:
        pass

    @abstractmethod
    async def search_by_ingredient_names(self, names: List[str]) -> List[Tuple[Product, float]]:
        """
        Resolves the given ingredient names/synonyms to ingredient ids, then returns
        every product containing at least one of them, paired with the overlap ratio
        (0.0-1.0) of (matching ingredients) / (total ingredients in that product).
        Sorted by overlap ratio descending.
        """
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
    async def search_ingredients(self, query: str, limit: int = 10) -> List[Ingredient]:
        """
        Ranked, multi-result ingredient search across primary name, e-number, and
        synonym table. Exact/prefix matches rank above plain substring matches.
        """
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

class IScanAgentService(ABC):
    @abstractmethod
    async def analyze_images(self, images: List[bytes]) -> ScanAgentResult:
        """
        Analyzes one or more product images (e.g. front-of-pack + ingredients label)
        using an LLM agent with tool access to the product/ingredient database, and
        returns a ScanAgentResult with any extracted fields and ranked candidate
        product matches.
        """
        pass
