from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import uvicorn
from dotenv import load_dotenv
from sqlalchemy import text
load_dotenv()

from src.adapters.controllers.controllers import router
from src.adapters.controllers.admin_controllers import admin_router
from src.infrastructure.database.database import engine, ensure_database_exists
from src.adapters.models import Base


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Run startup migrations before accepting requests."""
    ensure_database_exists()
    async with engine.begin() as conn:
        # Create any tables that don't exist yet (idempotent)
        await conn.run_sync(Base.metadata.create_all)
        # Add published column to products if it was created before this migration
        await conn.execute(text(
            "ALTER TABLE products ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT TRUE"
        ))
    yield


app = FastAPI(
    title="FoodCheck API",
    description="Backend service to check food & cosmetic ingredients regulatory status across countries.",
    version="1.0.0",
    lifespan=lifespan,
)

# Enable CORS for frontend, mobile, and web apps
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include endpoint routes
app.include_router(router)
app.include_router(admin_router)

@app.get("/")
async def root():
    return {
        "message": "Welcome to the FoodCheck API. Access API docs at /docs.",
        "docs_url": "/docs",
        "status": "healthy"
    }

@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "database": "connected"
    }

if __name__ == "__main__":
    uvicorn.run("src.main:app", host="0.0.0.0", port=8999, reload=True)
