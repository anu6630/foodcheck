# FoodCheck Backend Service

This is the backend service of the FoodCheck mono-repo. It provides the REST API for product searches, ingredient database lookups, and image recognition using Gemini Vision.

## Architecture
This project follows **Clean Architecture** principles. See the [.cursorrules](.cursorrules) file for detailed layer rules and coding standards.

### Structure
- `src/domain`: Domain entities and application use cases (pure logic).
- `src/adapters`: Controllers, repository implementations, and gateway services.
- `src/infrastructure`: Framework configurations (Express, database clients, environment variables).
