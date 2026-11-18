# CrispPrep Reviews & Ratings API

Backend for the ratings and reviews feature of [CrispPrep](https://crispprep.pages.dev), a free study-notes site for government-exam aspirants.

**Stack:** Java 21 · Spring Boot 4 · Spring Data JPA (Hibernate) · MySQL 8 · Bean Validation · OpenAPI/Swagger

## Features
- List PDFs and add **1–5 star ratings**, with **one rating per user per PDF** (409 on a duplicate)
- **Average rating per PDF** and **top-rated PDFs**, computed in SQL with JOIN, AVG and GROUP BY
- **Consent-based reviews:** a review is rejected without consent, and only admin-approved reviews are public
- **Clean errors:** one JSON shape for every error (400 / 404 / 409 / 500), with the exact field that failed
- **Swagger UI** to explore and test every endpoint

## Endpoints
| Method | Path | What it does |
|---|---|---|
| GET | `/api/pdfs` | List PDFs |
| POST | `/api/pdfs/{id}/ratings` | Add a rating `{"userName","stars"}` |
| GET | `/api/pdfs/{id}/summary` | Average rating + count |
| GET | `/api/pdfs/top` | Top 5 PDFs by average rating |
| POST | `/api/pdfs/{id}/reviews` | Submit a review (needs `consent: true`) |
| GET | `/api/pdfs/{id}/reviews` | Approved reviews only |
| PUT | `/api/admin/reviews/{id}/approve` | Approve a review |

## Database design
```mermaid
erDiagram
    PDF ||--o{ RATING : has
    PDF ||--o{ REVIEW : has
    PDF { bigint id PK
          varchar title
          varchar subject }
    RATING { bigint id PK
             int stars
             varchar user_name
             bigint pdf_id FK
             datetime created_at }
    REVIEW { bigint id PK
             varchar user_name
             varchar text
             boolean consent
             boolean approved
             bigint pdf_id FK
             datetime created_at }
```
`rating` has a unique constraint on `(pdf_id, user_name)`.

## Architecture
`Controller → Repository → MySQL`, with DTOs for input and output and a global `@RestControllerAdvice` for errors.

## Run locally
1. Create the database: `CREATE DATABASE crispprep;`
2. Set the password (it is never stored in code): `$env:DB_PASSWORD="your-password"`
3. Run: `./mvnw spring-boot:run`
4. Open: http://localhost:8080/swagger-ui/index.html

## Next steps
- Spring Security + JWT so only admins can approve reviews
- Flyway migrations instead of `ddl-auto=update`
- Unit and integration tests
- Deploy and connect to the CrispPrep website