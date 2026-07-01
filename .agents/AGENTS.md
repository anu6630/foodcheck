# FoodCheck Workspace Rules

This is a mono-repo containing the frontend, mobile, and backend code for the **FoodCheck** app.

## Project Structure
- `/backend`: Node.js/Python backend service powering the API, matching database, and AI vision integrations.
- `/mobile`: Android/iOS/Flutter app targeting consumer-facing scanning and ingredient check experiences.
- `/web`: Web frontend/app providing admin portals, product directory search, or general marketing interface.

## Global Agent Guidelines
1. **Directory Context**: Before starting work, identify which directory you are operating in (`backend`, `mobile`, or `web`) and strictly adhere to the guidelines and rules outlined in that directory's `.cursorrules` or local documentation.
2. **Clean Architecture**: For `backend` and `mobile`, you MUST follow **Clean Architecture** principles. Keep the core domain logic independent of frameworks, UI, and external data sources.
3. **Responsive & Aesthetically Premium**: For `web` and `mobile`, prioritize high-fidelity UI design. Avoid basic placeholders; use curated color palettes and rich aesthetics.
4. **Documentation**: Maintain all existing codebase comments, and document new classes/methods clearly.
5. **Symbol Linking**: When explaining code to the user, always format paths and code symbol names as markdown links targeting the specific file or lines.
