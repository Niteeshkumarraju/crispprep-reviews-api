# Deployment notes (pending)

Status (18 Nov 2026): the backend is deploy-ready and tested on PostgreSQL and MySQL. Hosting not set up yet.

## Plan: free hosting
- **Database:** Neon (free PostgreSQL, no time limit, ~0.5 GB): neon.tech
- **App server:** Render (free web service, Docker): render.com

## 1. Neon
1. Sign up at neon.tech, create project `crispprep`, region nearest India (e.g. Singapore).
2. Note Host, Database (usually `neondb`), User, Password. Keep them private.

## 2. Render
1. Sign up with GitHub, New -> Web Service -> this repo.
2. Runtime: **Docker**, Instance type: **Free**, Name: `crispprep-api`.
3. Environment variables:

| Key | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://NEON-HOST/neondb?sslmode=require` |
| `DB_USER` | Neon user |
| `DB_PASSWORD` | Neon password |
| `JWT_SECRET` | new random secret (32+ chars), not the laptop one |
| `ADMIN_EMAIL` | admin's email |
| `GOOGLE_CLIENT_ID` | Google OAuth client id |

4. Create, wait for "Live" (first build ~5-10 min).
5. Check: `https://crispprep-api.onrender.com/api/pdfs/slug/Polity-Day-02-Preamble/summary`

## 3. Google Cloud
OAuth client -> Authorized JavaScript origins -> add `https://crispprep.pages.dev`.
Publish the app (Audience -> Publish) so any Google account can sign in.

## 4. Keep it awake (optional)
Free Render sleeps after ~15 min idle. cron-job.org: GET `https://YOUR-API/api/health` every 10 min.

## After it's live (website work, not done yet)
- Sign-in popup (Google + email) on crispprep.pages.dev
- Star ratings, average and reviews on every PDF page (API: `/api/pdfs/slug/{slug}/summary`)
- Update the website CSP (connect-src + Google sign-in) and the privacy policy (we store student emails)
