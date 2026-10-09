"""Admin-only API router.

All endpoints require the header:
    X-Admin-Token: 123

This is a placeholder for a real auth system. Replace with JWT or Firebase Auth
before going to production.
"""
from __future__ import annotations

import base64
from typing import List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi import Body
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from src.infrastructure.database.database import get_db_session
from src.adapters.repositories import SQLAlchemyRequestRepository, SQLAlchemyIngredientRepository
from src.adapters.models import DBIngredientBan

ADMIN_TOKEN = "123"

admin_router = APIRouter(prefix="/api/admin", tags=["admin"])


# ------------------------------------------------------------------ #
#  Auth guard
# ------------------------------------------------------------------ #

def require_admin(x_admin_token: Optional[str] = Header(None, alias="x-admin-token")):
    """Dependency that enforces the admin token on every admin endpoint."""
    if x_admin_token != ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="Forbidden: invalid admin token")


# ------------------------------------------------------------------ #
#  Pydantic request/response schemas
# ------------------------------------------------------------------ #

class StatusUpdate(BaseModel):
    status: str  # pending | approved | rejected


class CreateProductBody(BaseModel):
    name: str
    barcode: str
    brand: Optional[str] = None
    category: Optional[str] = None
    source: str = "local"
    published: bool = True


class CreateIngredientBody(BaseModel):
    id: str          # slug, e.g. "titanium-dioxide"
    name: str
    category: Optional[str] = None
    e_number: Optional[str] = None
    inci_name: Optional[str] = None
    cas_number: Optional[str] = None
    bans: Optional[List[dict]] = None  # list of {country_code, status, condition, regulation_ref}


class CreateBanBody(BaseModel):
    ingredient_id: str
    country_code: str
    status: str       # banned | restricted | permitted
    condition: Optional[str] = None
    regulation_ref: Optional[str] = None


# ------------------------------------------------------------------ #
#  Product request management
# ------------------------------------------------------------------ #

@admin_router.get("/requests/products", dependencies=[Depends(require_admin)])
async def list_product_requests(
    status: Optional[str] = None,
    db: AsyncSession = Depends(get_db_session),
):
    """List all product submission requests, optionally filtered by status."""
    repo = SQLAlchemyRequestRepository(db)
    items = await repo.list_product_requests(status=status)
    return [
        {
            "id": r.id,
            "status": r.status,
            "product_name": r.product_name,
            "brand": r.brand,
            "barcode": r.barcode,
            "country_code": r.country_code,
            "category": r.category,
            "created_at": r.created_at.isoformat() if r.created_at else None,
        }
        for r in items
    ]


@admin_router.get("/requests/products/{request_id}", dependencies=[Depends(require_admin)])
async def get_product_request(
    request_id: int,
    db: AsyncSession = Depends(get_db_session),
):
    """Get full detail of a product request, including base64-encoded images."""
    repo = SQLAlchemyRequestRepository(db)
    r = await repo.get_product_request(request_id)
    if not r:
        raise HTTPException(status_code=404, detail="Product request not found")

    def encode(b: Optional[bytes]) -> Optional[str]:
        return base64.b64encode(b).decode() if b else None

    return {
        "id": r.id,
        "status": r.status,
        "product_name": r.product_name,
        "brand": r.brand,
        "barcode": r.barcode,
        "country_code": r.country_code,
        "category": r.category,
        "ingredients_text": r.ingredients_text,
        "requester_notes": r.requester_notes,
        "created_at": r.created_at.isoformat() if r.created_at else None,
        "image_1": encode(r.image_1),
        "image_2": encode(r.image_2),
        "image_3": encode(r.image_3),
    }


@admin_router.put("/requests/products/{request_id}/status", dependencies=[Depends(require_admin)])
async def update_product_request_status(
    request_id: int,
    body: StatusUpdate,
    db: AsyncSession = Depends(get_db_session),
):
    """Approve or reject a product submission request."""
    allowed = {"pending", "approved", "rejected"}
    if body.status not in allowed:
        raise HTTPException(status_code=422, detail=f"status must be one of {allowed}")
    repo = SQLAlchemyRequestRepository(db)
    r = await repo.update_product_request_status(request_id, body.status)
    if not r:
        raise HTTPException(status_code=404, detail="Product request not found")
    return {"id": r.id, "status": r.status}


# ------------------------------------------------------------------ #
#  Ingredient request management
# ------------------------------------------------------------------ #

@admin_router.get("/requests/ingredients", dependencies=[Depends(require_admin)])
async def list_ingredient_requests(
    status: Optional[str] = None,
    db: AsyncSession = Depends(get_db_session),
):
    """List all ingredient check requests, optionally filtered by status."""
    repo = SQLAlchemyRequestRepository(db)
    items = await repo.list_ingredient_requests(status=status)
    return [
        {
            "id": r.id,
            "status": r.status,
            "ingredient_name": r.ingredient_name,
            "e_number": r.e_number,
            "category": r.category,
            "created_at": r.created_at.isoformat() if r.created_at else None,
        }
        for r in items
    ]


@admin_router.get("/requests/ingredients/{request_id}", dependencies=[Depends(require_admin)])
async def get_ingredient_request(
    request_id: int,
    db: AsyncSession = Depends(get_db_session),
):
    """Get full detail of an ingredient check request."""
    repo = SQLAlchemyRequestRepository(db)
    r = await repo.get_ingredient_request(request_id)
    if not r:
        raise HTTPException(status_code=404, detail="Ingredient request not found")
    return {
        "id": r.id,
        "status": r.status,
        "ingredient_name": r.ingredient_name,
        "e_number": r.e_number,
        "category": r.category,
        "requester_notes": r.requester_notes,
        "created_at": r.created_at.isoformat() if r.created_at else None,
    }


@admin_router.put("/requests/ingredients/{request_id}/status", dependencies=[Depends(require_admin)])
async def update_ingredient_request_status(
    request_id: int,
    body: StatusUpdate,
    db: AsyncSession = Depends(get_db_session),
):
    """Approve or reject an ingredient check request."""
    allowed = {"pending", "approved", "rejected"}
    if body.status not in allowed:
        raise HTTPException(status_code=422, detail=f"status must be one of {allowed}")
    repo = SQLAlchemyRequestRepository(db)
    r = await repo.update_ingredient_request_status(request_id, body.status)
    if not r:
        raise HTTPException(status_code=404, detail="Ingredient request not found")
    return {"id": r.id, "status": r.status}


# ------------------------------------------------------------------ #
#  Admin product catalog CRUD
# ------------------------------------------------------------------ #

@admin_router.put("/products/{product_id}/publish", dependencies=[Depends(require_admin)])
async def publish_product(
    product_id: int,
    db: AsyncSession = Depends(get_db_session),
):
    """Set a product's published flag to True, making it visible in scan/search."""
    repo = SQLAlchemyRequestRepository(db)
    product = await repo.publish_product(product_id)
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    return {"id": product.id, "name": product.name, "published": product.published}


@admin_router.post("/products", dependencies=[Depends(require_admin)], status_code=201)
async def create_product(
    body: CreateProductBody,
    db: AsyncSession = Depends(get_db_session),
):
    """Directly create a product in the catalog (bypasses request flow)."""
    repo = SQLAlchemyRequestRepository(db)
    product = await repo.create_product_direct(
        name=body.name,
        barcode=body.barcode,
        source=body.source,
        brand=body.brand,
        category=body.category,
        published=body.published,
    )
    return {"id": product.id, "name": product.name, "barcode": product.barcode, "published": product.published}


@admin_router.post("/ingredients", dependencies=[Depends(require_admin)], status_code=201)
async def create_ingredient(
    body: CreateIngredientBody,
    db: AsyncSession = Depends(get_db_session),
):
    """Directly create an ingredient in the catalog."""
    from src.adapters.models import DBIngredient, DBIngredientBan, DBIngredientName
    from sqlalchemy import select

    # Check for duplicate slug
    existing = await db.execute(
        select(DBIngredient).where(DBIngredient.id == body.id)
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=409, detail=f"Ingredient '{body.id}' already exists")

    ing = DBIngredient(
        id=body.id,
        name=body.name,
        category=body.category,
        e_number=body.e_number,
        inci_name=body.inci_name,
        cas_number=body.cas_number,
    )
    db.add(ing)

    if body.bans:
        for ban in body.bans:
            db.add(DBIngredientBan(
                ingredient_id=body.id,
                country_code=ban.get("country_code"),
                status=ban.get("status", "restricted"),
                condition=ban.get("condition"),
                regulation_ref=ban.get("regulation_ref"),
            ))

    await db.commit()
    return {"id": body.id, "name": body.name}


@admin_router.post("/bans", dependencies=[Depends(require_admin)], status_code=201)
async def create_ban(
    body: CreateBanBody,
    db: AsyncSession = Depends(get_db_session),
):
    """Add a regulatory ban record for an ingredient in a given country."""
    repo = SQLAlchemyRequestRepository(db)
    ban = await repo.create_ban_direct(
        ingredient_id=body.ingredient_id,
        country_code=body.country_code,
        status=body.status,
        condition=body.condition,
        regulation_ref=body.regulation_ref,
    )
    return {
        "id": ban.id,
        "ingredient_id": ban.ingredient_id,
        "country_code": ban.country_code,
        "status": ban.status,
    }
