import os
import json
import base64
import logging
from typing import Any, Dict, List, Optional

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_google_genai import ChatGoogleGenerativeAI

from src.domain.entities import ScanAgentResult
from src.domain.interfaces import IScanAgentService, IProductRepository, IIngredientRepository
from src.adapters.services.agent_tools import build_scan_tools

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are an expert food & cosmetic safety assistant for the FoodCheck app.
You will be shown one or two photos of a product - typically a front-of-pack/branding photo
and/or a photo of the ingredients label.

Your job:
1. Extract whatever you can see: product_name, brand, a country_hint (ISO-ish guess like
   "IN", "EU", "US", "AU" based on language/packaging/labels, or null if unclear), and the
   full ingredients_text if an ingredients panel is visible.
2. Use the provided tools to search FoodCheck's own product database for a matching product:
   - Prefer `search_by_name` when you have a plausible product name/brand.
   - Use `search_by_ingredients` when you have an ingredients list but low confidence in the
     product name.
   - Use `search_by_country` only if you need to reason about what's banned/restricted in a
     hinted country - it does not find matching products.
   You may call multiple tools, but do not call more tools than necessary.
3. Once you're done investigating, reply with ONLY a single JSON object (no markdown fences,
   no extra prose) in exactly this shape:
   {
     "product_name": "<string or null>",
     "brand": "<string or null>",
     "country_hint": "<string or null>",
     "ingredients_text": "<string or null>"
   }
"""

FINAL_ANSWER_REMINDER = (
    "Based on everything above, respond now with ONLY the final JSON object described "
    "in your instructions - no markdown fences, no extra prose."
)


class GeminiScanAgentService(IScanAgentService):
    """
    LangChain tool-calling agent backing POST /api/scan. Uses Gemini (via
    ChatGoogleGenerativeAI) with a bounded manual tool-call loop rather than the
    higher-level create_tool_calling_agent/AgentExecutor helpers - this keeps iteration
    bounds explicit and avoids depending on multimodal-message quirks in those helpers.

    Mirrors the existing GeminiVisionService mock-mode convention (MOCK_GEMINI env var
    / optional mock_type param) so Karate tests can exercise all three downstream
    match_type branches deterministically without live Gemini calls or an agent loop.
    """

    MAX_ITERATIONS = 4

    def __init__(
        self,
        product_repo: IProductRepository,
        ingredient_repo: IIngredientRepository,
        api_key: Optional[str] = None
    ):
        self.product_repo = product_repo
        self.ingredient_repo = ingredient_repo
        self.api_key = api_key or os.getenv("GOOGLE_API_KEY")
        self.mock_mode = os.getenv("MOCK_GEMINI", "false").lower() == "true" or not self.api_key

    async def analyze_images(self, images: List[bytes], mock_type: Optional[str] = None) -> ScanAgentResult:
        if self.mock_mode:
            logger.info("Gemini Scan Agent Service running in MOCK mode.")
            return await self._get_mock_response(mock_type)

        try:
            return await self._run_agent(images)
        except Exception as e:
            logger.error(f"Error running Gemini scan agent: {e}. Falling back to mock data.")
            return await self._get_mock_response(mock_type)

    async def _run_agent(self, images: List[bytes]) -> ScanAgentResult:
        llm = ChatGoogleGenerativeAI(model="gemini-2.5-flash", google_api_key=self.api_key, temperature=0)
        tools, collected_candidates = build_scan_tools(self.product_repo, self.ingredient_repo)
        llm_with_tools = llm.bind_tools(tools)
        tools_by_name = {t.name: t for t in tools}

        image_parts = [
            {
                "type": "image_url",
                "image_url": f"data:image/jpeg;base64,{base64.b64encode(img).decode('utf-8')}"
            }
            for img in images
        ]
        messages: List[BaseMessage] = [
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=[
                {"type": "text", "text": "Analyze the attached product image(s)."},
                *image_parts
            ])
        ]

        extracted: Dict[str, Any] = {}
        for _ in range(self.MAX_ITERATIONS):
            ai_message: AIMessage = await llm_with_tools.ainvoke(messages)
            messages.append(ai_message)

            if not ai_message.tool_calls:
                extracted = self._parse_final_json(ai_message.content)
                break

            for tool_call in ai_message.tool_calls:
                tool_fn = tools_by_name.get(tool_call["name"])
                if tool_fn is None:
                    tool_result = f"Unknown tool: {tool_call['name']}"
                else:
                    tool_result = await tool_fn.ainvoke(tool_call["args"])
                messages.append(ToolMessage(content=str(tool_result), tool_call_id=tool_call["id"]))
        else:
            # Exhausted MAX_ITERATIONS without a plain final answer - force one.
            final_ai_message = await llm.ainvoke(messages + [HumanMessage(content=FINAL_ANSWER_REMINDER)])
            extracted = self._parse_final_json(final_ai_message.content)

        # Dedupe candidates by product identity, keeping the best score seen for each -
        # the same product can be surfaced by more than one tool call (e.g. matched by
        # both name and ingredient overlap).
        best_by_id: Dict[Any, tuple] = {}
        for product, score in collected_candidates:
            key = product.id if product.id is not None else product.barcode
            if key not in best_by_id or score > best_by_id[key][1]:
                best_by_id[key] = (product, score)
        candidates = sorted(best_by_id.values(), key=lambda c: c[1], reverse=True)

        return ScanAgentResult(
            product_name=extracted.get("product_name"),
            brand=extracted.get("brand"),
            country_hint=extracted.get("country_hint"),
            ingredients_text=extracted.get("ingredients_text"),
            candidates=candidates
        )

    @staticmethod
    def _parse_final_json(content: Any) -> Dict[str, Any]:
        if isinstance(content, str):
            text = content
        elif isinstance(content, list):
            text = "".join([part.get("text", "") if isinstance(part, dict) else str(part) for part in content])
        else:
            text = str(content)
            
        text = text.strip()
        # Strip markdown fences if the model added them despite instructions
        if text.startswith("```"):
            text = text.strip("`")
            if text.lower().startswith("json"):
                text = text[4:]
            text = text.strip()
        try:
            return json.loads(text)
        except (json.JSONDecodeError, TypeError):
            logger.warning(f"Could not parse scan agent final answer as JSON: {text[:200]!r}")
            return {}

    async def _get_mock_response(self, mock_type: Optional[str] = None) -> ScanAgentResult:
        m_type = mock_type or os.getenv("MOCK_SCAN_TYPE", "ingredients_only")

        if m_type == "confident_match":
            product = await self.product_repo.get_by_barcode("016000452327")
            candidates = [(product, 95)] if product else []
            return ScanAgentResult(
                product_name="White Cookie Frosting",
                brand="Betty Crocker",
                country_hint="US",
                ingredients_text="sugar, wheat starch, titanium dioxide, sodium benzoate, artificial flavor",
                candidates=candidates
            )

        if m_type == "ambiguous":
            p1 = await self.product_repo.get_by_barcode("016000452327")
            p2 = await self.product_repo.get_by_barcode("111111111111")
            candidates = []
            if p1:
                candidates.append((p1, 70))
            if p2:
                candidates.append((p2, 62))
            return ScanAgentResult(
                product_name="White Bread",
                brand=None,
                country_hint=None,
                ingredients_text="wheat flour, sugar, sodium benzoate, yeast",
                candidates=candidates
            )

        if m_type == "ingredients_only":
            return ScanAgentResult(
                product_name=None,
                brand=None,
                country_hint="IN",
                ingredients_text="potassium bromate, E171, tartrazine, water, wheat flour, sugar",
                candidates=[]
            )

        # Unknown mock_type: behave like an empty/failed extraction
        return ScanAgentResult()
