import asyncio
import os
import base64
from src.adapters.services.gemini_agent_service import GeminiScanAgentService, SYSTEM_PROMPT
from src.adapters.repositories import SQLAlchemyProductRepository, SQLAlchemyIngredientRepository
from src.infrastructure.database.database import AsyncSessionLocal, engine
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import SystemMessage, HumanMessage
from src.adapters.services.agent_tools import build_scan_tools

async def test():
    with open("../samples/bournvita_ingredients.jpg", "rb") as f:
        img_bytes = f.read()

    async with AsyncSessionLocal() as session:
        product_repo = SQLAlchemyProductRepository(session)
        ingredient_repo = SQLAlchemyIngredientRepository(session)
        
        service = GeminiScanAgentService(product_repo, ingredient_repo)
        
        print("API Key:", service.api_key)
        
        # Manually run the LLM call once to see its raw text response and tool calls
        llm = ChatGoogleGenerativeAI(model="gemini-2.5-flash", google_api_key=service.api_key, temperature=0)
        tools, collected_candidates = build_scan_tools(product_repo, ingredient_repo)
        llm_with_tools = llm.bind_tools(tools)
        
        image_part = {
            "type": "image_url",
            "image_url": f"data:image/jpeg;base64,{base64.b64encode(img_bytes).decode('utf-8')}"
        }
        messages = [
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=[
                {"type": "text", "text": "Analyze the attached product image(s)."},
                image_part
            ])
        ]
        
        print("Invoking LLM...")
        ai_message = await llm_with_tools.ainvoke(messages)
        print("Raw Content:", ai_message.content)
        print("Tool Calls:", ai_message.tool_calls)

if __name__ == "__main__":
    asyncio.run(test())
