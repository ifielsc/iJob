# Business modules

Each module owns its routes, query functions, views, and tests. Import generic contracts from `shared/api/values` and `shared/runtime/contracts`, and generic editors from `shared/forms` or `shared/ui`. Cross-domain components and domain contracts are exposed only through the owning module's `public/components.ts` or `public/contracts.ts`. Do not import another module's internal files. ESLint enforces this boundary. Public entry points must remain explicit and must not re-export whole module trees. Pinia owns client state; TanStack Query owns remote state. Theme/locale changes must not be used as component keys.

Shared account validation is under shared/auth; cross-security DTO parsers are in security-contracts.ts. Keep the accepted module layouts and interaction conventions. Password-bearing commands use uncached, non-retrying request functions so secrets never become TanStack mutation variables or persisted state.
