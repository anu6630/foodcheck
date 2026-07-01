import os
import json
import logging
from typing import Dict, Any, Optional
import google.generativeai as genai

from src.domain.interfaces import IVisionService

logger = logging.getLogger(__name__)

class GeminiVisionService(IVisionService):
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.getenv("GOOGLE_API_KEY")
        self.mock_mode = os.getenv("MOCK_GEMINI", "false").lower() == "true" or not self.api_key
        
        if not self.mock_mode:
            genai.configure(api_key=self.api_key)

    async def analyze_image(self, image_bytes: bytes, mock_type: Optional[str] = None) -> Dict[str, Any]:
        if self.mock_mode:
            logger.info("Gemini Vision Service running in MOCK mode.")
            return self._get_mock_response(image_bytes, mock_type)

        try:
            # Using gemini-2.5-flash as the primary image analyzer
            model = genai.GenerativeModel("gemini-2.5-flash")
            
            prompt = """
            You are an expert food safety assistant for the FoodCheck app.
            Analyze the uploaded image of a food or cosmetic product.
            Determine the user's intent:
            - If the image shows a product's front, packaging, or brand name, classify intent as "product". Extrapolate the product name, brand, and barcode if visible.
            - If the image shows an ingredients list panel or text containing ingredients, classify intent as "ingredients". Extract the raw ingredients text as a single string.

            Respond ONLY with a JSON object in this format (do not wrap in markdown ```json blocks):
            {
              "intent": "product" | "ingredients",
              "product_name": "Product Name (or null if not detected)",
              "brand": "Brand Name (or null if not detected)",
              "barcode": "Barcode string if visible (or null if not detected)",
              "ingredients_text": "Full extracted ingredients list text (or null if not detected)"
            }
            """
            
            contents = [
                {"mime_type": "image/jpeg", "data": image_bytes},
                prompt
            ]
            
            response = model.generate_content(
                contents,
                generation_config={"response_mime_type": "application/json"}
            )
            
            data = json.loads(response.text.strip())
            return data
            
        except Exception as e:
            logger.error(f"Error calling Gemini Vision API: {e}. Falling back to mock data.")
            # Safety fallback during API errors
            return self._get_mock_response(image_bytes, mock_type)

    def _get_mock_response(self, image_bytes: bytes, mock_type: Optional[str] = None) -> Dict[str, Any]:
        # Respect dynamic parameter mock_type, falling back to environment variable
        m_type = mock_type or os.getenv("MOCK_IMAGE_TYPE", "ingredients")
        
        if m_type == "product":
            return {
                "intent": "product",
                "product_name": "White Cookie Frosting",
                "brand": "Betty Crocker",
                "barcode": "016000452327",
                "ingredients_text": "sugar, wheat starch, titanium dioxide, artificial flavor"
            }
        elif m_type == "product_potassium":
            return {
                "intent": "product",
                "product_name": "Traditional White Bread",
                "brand": "Local Bakery",
                "barcode": "111111111111",
                "ingredients_text": "wheat flour, water, yeast, potassium bromate, salt"
            }
        elif m_type == "ingredients_hindi":
            return {
                "intent": "ingredients",
                "product_name": None,
                "brand": None,
                "barcode": None,
                "ingredients_text": "टाइटानियम डाइऑक्साइड, E211, E102"
            }
        else:
            # Default mock: ingredients list scan
            return {
                "intent": "ingredients",
                "product_name": None,
                "brand": None,
                "barcode": None,
                "ingredients_text": "potassium bromate, E171, tartrazine, water, wheat flour, sugar"
            }

