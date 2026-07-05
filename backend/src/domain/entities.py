from dataclasses import dataclass, field
from typing import List, Optional
from datetime import date

@dataclass
class Country:
    code: str
    name: str

@dataclass
class IngredientName:
    lang: str
    name: str

@dataclass
class IngredientBan:
    country_code: str
    status: str  # "banned" | "restricted" | "permitted"
    condition: Optional[str] = None
    regulation_ref: Optional[str] = None
    effective_date: Optional[date] = None

@dataclass
class Ingredient:
    id: str  # Unique slug/id (e.g. "titanium-dioxide")
    name: str  # Primary name (usually English)
    category: Optional[str] = None
    e_number: Optional[str] = None
    inci_name: Optional[str] = None
    cas_number: Optional[str] = None
    names: List[IngredientName] = field(default_factory=list)
    bans: List[IngredientBan] = field(default_factory=list)

@dataclass
class ProductIngredient:
    ingredient: Ingredient
    order: int

@dataclass
class Product:
    id: Optional[int]
    name: str
    brand: Optional[str]
    barcode: str
    category: Optional[str]
    source: str  # "local" | "open_food_facts"
    ingredients: List[ProductIngredient] = field(default_factory=list)

@dataclass
class ScanAgentResult:
    """Structured result of the scan agent's analysis of one or more product images."""
    product_name: Optional[str] = None
    brand: Optional[str] = None
    country_hint: Optional[str] = None
    ingredients_text: Optional[str] = None
    candidates: List[tuple] = field(default_factory=list)  # List[Tuple[Product, int]] - (product, score 0-100)
