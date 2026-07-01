# FoodCheck Mobile App

This is the mobile application of the FoodCheck mono-repo. It allows users to scan ingredient labels, look up products, and check ingredient statuses (banned, restricted, permitted) across various countries.

## Architecture
This project follows **Clean Architecture** principles to separate UI, business logic, caching, and networking. Detailed layer rules can be found in the [.cursorrules](.cursorrules) file.

### Structure
- `lib/domain`: Core domain models and business use cases.
- `lib/data`: API client implementations, offline caching, and data repositories.
- `lib/presentation`: UI pages, widgets, and state management.
