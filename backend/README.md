# FoodCheck Backend Service

This is the backend service of the FoodCheck mono-repo. It provides the REST API for product searches, ingredient database lookups, and image recognition using Gemini Vision.

## Architecture
This project follows **Clean Architecture** principles. See the [.cursorrules](.cursorrules) file for detailed layer rules and coding standards.

### Structure
- `src/domain`: Domain entities and application use cases (pure logic).
- `src/adapters`: Controllers (FastAPI routers), repository implementations (SQLAlchemy), and gateway services.
- `src/infrastructure`: Framework configurations (FastAPI application, Alembic migrations, database clients, environment variables).

## Database Configuration
This project uses **Supabase** (PostgreSQL) as its database.
- Create a **separate database** specifically for FoodCheck (e.g., `foodcheck`) within your existing Supabase database cluster.
- Configure your `.env` file with `DATABASE_URL` and `DIRECT_URL` pointing to the custom database name inside the connection URI.

## API Automation Testing (Karate)
This project uses the **Karate Framework** for automated integration and contract testing of all endpoints.
- Feature files are stored in `tests/karate/features/`.
- Ensure new endpoints have corresponding `.feature` tests checking HTTP statuses, payloads, schemas, and response validation.
- Mock setups should be defined to stub third-party APIs (such as Gemini Vision and Open Food Facts) during testing.


