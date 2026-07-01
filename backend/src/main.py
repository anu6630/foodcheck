from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from src.adapters.controllers.controllers import router

app = FastAPI(
    title="FoodCheck API",
    description="Backend service to check food & cosmetic ingredients regulatory status across countries.",
    version="1.0.0"
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

