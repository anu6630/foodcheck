from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Query, Header
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Optional, List

from src.infrastructure.database.database import get_db_session
from src.adapters.repositories import SQLAlchemyProductRepository, SQLAlchemyIngredientRepository
from src.adapters.services.gemini_service import GeminiVisionService
from src.adapters.services.off_service import OpenFoodFactsService
from src.domain.usecases import SearchProductUseCase, GetProductDetailsUseCase, ScanImageUseCase

router = APIRouter(prefix="/api")

def get_countries_list(countries: str = Query("IN", description="Comma-separated country codes (e.g. IN,EU,US)")) -> List[str]:
    return [c.strip().upper() for c in countries.split(",") if c.strip()]

@router.post("/scan")
async def scan_image(
    image: UploadFile = File(...),
    countries: List[str] = Depends(get_countries_list),
    x_mock_image_type: Optional[str] = Header(None, alias="x-mock-image-type"),
    db: AsyncSession = Depends(get_db_session)
):
    try:
        # Read uploaded image bytes
        image_bytes = await image.read()
        
        # Instantiate dependencies
        product_repo = SQLAlchemyProductRepository(db)
        ingredient_repo = SQLAlchemyIngredientRepository(db)
        vision_service = GeminiVisionService()
        lookup_service = OpenFoodFactsService()
        
        # Execute use case
        usecase = ScanImageUseCase(vision_service, product_repo, ingredient_repo, lookup_service)
        result = await usecase.execute(image_bytes, countries, mock_type=x_mock_image_type)
        
        return result

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Image scanning failed: {str(e)}")

@router.get("/products/search")
async def search_products(
    q: str = Query("", description="Product name or brand search query"),
    countries: List[str] = Depends(get_countries_list),
    db: AsyncSession = Depends(get_db_session)
):
    try:
        product_repo = SQLAlchemyProductRepository(db)
        usecase = SearchProductUseCase(product_repo)
        results = await usecase.execute(q, countries)
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")

@router.get("/products/{barcode}")
async def get_product_details(
    barcode: str,
    countries: List[str] = Depends(get_countries_list),
    db: AsyncSession = Depends(get_db_session)
):
    product_repo = SQLAlchemyProductRepository(db)
    lookup_service = OpenFoodFactsService()
    
    usecase = GetProductDetailsUseCase(product_repo, lookup_service)
    result = await usecase.execute(barcode, countries)
    
    if not result:
        raise HTTPException(status_code=404, detail="Product not found")
        
    return result
