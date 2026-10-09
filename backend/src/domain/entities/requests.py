"""Pydantic domain entities for community contribution requests."""
from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class ProductRequestCreate:
    """Data needed to open a new product-addition request."""
    product_name: str
    country_code: str
    brand: Optional[str] = None
    barcode: Optional[str] = None
    category: Optional[str] = None
    ingredients_text: Optional[str] = None
    requester_notes: Optional[str] = None
    # Up to 3 image byte blobs
    image_1: Optional[bytes] = None
    image_2: Optional[bytes] = None
    image_3: Optional[bytes] = None


@dataclass
class ProductRequestSummary:
    """Lightweight summary returned in list endpoints (no image bytes)."""
    id: int
    status: str
    product_name: str
    brand: Optional[str]
    barcode: Optional[str]
    country_code: str
    category: Optional[str]
    created_at: datetime


@dataclass
class ProductRequestDetail(ProductRequestSummary):
    """Full detail including image presence flags (not raw bytes over wire)."""
    ingredients_text: Optional[str]
    requester_notes: Optional[str]
    has_image_1: bool = False
    has_image_2: bool = False
    has_image_3: bool = False


@dataclass
class IngredientRequestCreate:
    """Data needed to open a new ingredient-check request."""
    ingredient_name: str
    e_number: Optional[str] = None
    category: Optional[str] = None
    requester_notes: Optional[str] = None


@dataclass
class IngredientRequestSummary:
    """Lightweight summary returned in list endpoints."""
    id: int
    status: str
    ingredient_name: str
    e_number: Optional[str]
    category: Optional[str]
    created_at: datetime


@dataclass
class IngredientRequestDetail(IngredientRequestSummary):
    """Full detail including notes."""
    requester_notes: Optional[str]
