from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Query, Header, Form
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional
from pydantic import BaseModel

from src.infrastructure.database.database import get_db_session
from src.adapters.repositories import SQLAlchemyProductRepository, SQLAlchemyIngredientRepository, SQLAlchemyRequestRepository
from src.adapters.services.gemini_agent_service import GeminiScanAgentService
from src.adapters.services.off_service import OpenFoodFactsService
from src.domain.usecases import (
    SearchProductUseCase,
    GetProductDetailsUseCase,
    ScanWithAgentUseCase,
    UnifiedSearchUseCase,
    GetIngredientDetailsUseCase
)

router = APIRouter(prefix="/api")

@router.post("/scan")
async def scan_image(
    images: List[UploadFile] = File(...),
    x_mock_image_type: Optional[str] = Header(None, alias="x-mock-image-type"),
    db: AsyncSession = Depends(get_db_session)
):
    try:
        # Read uploaded image bytes (1-2 images: e.g. front-of-pack and/or ingredients label)
        image_bytes_list = [await img.read() for img in images]

        # Instantiate dependencies
        product_repo = SQLAlchemyProductRepository(db)
        ingredient_repo = SQLAlchemyIngredientRepository(db)
        agent_service = GeminiScanAgentService(product_repo, ingredient_repo)

        # Execute use case
        usecase = ScanWithAgentUseCase(agent_service, ingredient_repo)
        result = await usecase.execute(image_bytes_list, mock_type=x_mock_image_type)

        return result

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Image scanning failed: {str(e)}")

@router.get("/search")
async def unified_search(
    q: str = Query("", description="Unified search query across products and ingredients"),
    db: AsyncSession = Depends(get_db_session)
):
    try:
        product_repo = SQLAlchemyProductRepository(db)
        ingredient_repo = SQLAlchemyIngredientRepository(db)
        usecase = UnifiedSearchUseCase(product_repo, ingredient_repo)
        result = await usecase.execute(q)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")

@router.get("/ingredients/{ingredient_id}")
async def get_ingredient_details(
    ingredient_id: str,
    db: AsyncSession = Depends(get_db_session)
):
    ingredient_repo = SQLAlchemyIngredientRepository(db)
    usecase = GetIngredientDetailsUseCase(ingredient_repo)
    result = await usecase.execute(ingredient_id)

    if not result:
        raise HTTPException(status_code=404, detail="Ingredient not found")

    return result

@router.get("/products/search")
async def search_products(
    q: str = Query("", description="Product name or brand search query"),
    db: AsyncSession = Depends(get_db_session)
):
    try:
        product_repo = SQLAlchemyProductRepository(db)
        usecase = SearchProductUseCase(product_repo)
        results = await usecase.execute(q)
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")

@router.get("/products/{barcode}")
async def get_product_details(
    barcode: str,
    db: AsyncSession = Depends(get_db_session)
):
    product_repo = SQLAlchemyProductRepository(db)
    lookup_service = OpenFoodFactsService()

    usecase = GetProductDetailsUseCase(product_repo, lookup_service)
    result = await usecase.execute(barcode)

    if not result:
        raise HTTPException(status_code=404, detail="Product not found")

    return result


# --------------------------------------------------------------------------- #
#  Community contribution endpoints (user-facing)
# --------------------------------------------------------------------------- #

@router.post("/requests/product", status_code=201)
async def submit_product_request(
    product_name: str = Form(...),
    country_code: str = Form(...),
    brand: Optional[str] = Form(None),
    barcode: Optional[str] = Form(None),
    category: Optional[str] = Form(None),
    ingredients_text: Optional[str] = Form(None),
    requester_notes: Optional[str] = Form(None),
    image_1: Optional[UploadFile] = File(None),
    image_2: Optional[UploadFile] = File(None),
    image_3: Optional[UploadFile] = File(None),
    db: AsyncSession = Depends(get_db_session),
):
    """Flow 1: User submits a product addition request with up to 3 images."""
    try:
        repo = SQLAlchemyRequestRepository(db)
        img1 = await image_1.read() if image_1 else None
        img2 = await image_2.read() if image_2 else None
        img3 = await image_3.read() if image_3 else None

        req = await repo.create_product_request(
            product_name=product_name,
            country_code=country_code,
            brand=brand,
            barcode=barcode,
            category=category,
            ingredients_text=ingredients_text,
            requester_notes=requester_notes,
            image_1=img1,
            image_2=img2,
            image_3=img3,
        )
        return {"request_id": req.id, "status": req.status}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to submit product request: {str(e)}")


class IngredientRequestBody(BaseModel):
    ingredient_name: str
    e_number: Optional[str] = None
    category: Optional[str] = None
    requester_notes: Optional[str] = None


@router.post("/requests/ingredient", status_code=201)
async def submit_ingredient_request(
    body: IngredientRequestBody,
    db: AsyncSession = Depends(get_db_session),
):
    """Flow 2: User submits an ingredient check request."""
    try:
        repo = SQLAlchemyRequestRepository(db)
        req = await repo.create_ingredient_request(
            ingredient_name=body.ingredient_name,
            e_number=body.e_number,
            category=body.category,
            requester_notes=body.requester_notes,
        )
        return {"request_id": req.id, "status": req.status}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to submit ingredient request: {str(e)}")
