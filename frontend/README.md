# StreetAid frontend

This is a standalone Vite client. It talks to the Spring Boot API using the `/api` contract; it does not contain backend credentials or data-loading logic.

## Local development

1. Start the Spring Boot backend from the repository root on port `8080`.
2. In this directory, run `npm install` once, then `npm run dev`.
3. Open the Vite URL shown in the terminal (normally `http://localhost:5173`). Vite proxies `/api` requests to `http://localhost:8080`.

Copy `.env.example` to `.env.local` to configure a separately hosted API with `VITE_API_BASE_URL`. Set `VITE_GOOGLE_MAPS_API_KEY` to enable the optional map. Do not put server-side API credentials in frontend environment variables: Vite exposes `VITE_*` values to browser code.

Build for deployment with `npm run build`; output is written to `dist/`. Host that directory with a static web server and set the backend's `FRONTEND_ORIGIN` to the deployed frontend origin. Keep the API at the configured base URL.
