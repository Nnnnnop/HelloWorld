# SIG Resource Sharing Platform

## Local Development

### Backend
```bash
./mvnw spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

## Default Admin
- username: `admin`
- password: `Admin@123456`

## Docker Deployment
```bash
docker compose --env-file .env.example up --build
```

Services:
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`
- Elasticsearch: `localhost:9200`

## Office Preview (DOCX/XLSX/PPTX)
- The detail page now converts `docx/xlsx/pptx` to PDF for embedded preview.
- Install LibreOffice and make sure `soffice` is available in PATH, or set `SOFFICE_PATH` explicitly.
- Download keeps the original uploaded file type (not the converted PDF).

## OAuth2/OIDC
Set provider values via env vars:
- `OAUTH_CLIENT_ID`
- `OAUTH_CLIENT_SECRET`
- `OAUTH_AUTH_URI`
- `OAUTH_TOKEN_URI`
- `OAUTH_USERINFO_URI`
- `OAUTH_JWK_URI`
