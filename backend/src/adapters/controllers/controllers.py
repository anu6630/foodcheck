from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Query, Header
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional

from src.infrastructure.database.database import get_db_session
from src.adapters.repositories import SQLAlchemyProductRepository, SQLAlchemyIngredientRepository
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
