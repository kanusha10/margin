# Margin: ask any GitHub repo how it works

Paste a public GitHub URL. Margin clones it, indexes the code with Gemini embeddings in PostgreSQL (pgvector),
and answers questions with an **agent** that searches, opens files and follows the call chain, citing `path:line`.
Every step the agent takes is streamed live to the UI. Users sign in with JWT, and usage is rate limited.

**Stack:** Java 17, Spring Boot 3, Spring Security + JWT, JGit, PostgreSQL + pgvector, Gemini API, React + Vite, Docker.

## The 3 parts (and why you need 3 terminals)
| Part | What it is | Where it runs |
|---|---|---|
| Database | PostgreSQL with pgvector | Docker, port 5432 |
| Backend | Spring Boot API + the agent | your machine, port 8080 |
| Frontend | React website | your machine, port 5173 |

You open the **frontend** in the browser. It talks to the backend, which talks to the database and Gemini.

## Run it, step by step

### Step 1. Check your tools
Run these in a terminal. Each should print a version:
```
java -version      (needs 17 or higher)
mvn -v             (needs 3.9 or higher)
node -v            (needs 18 or higher)
docker --version   (and Docker Desktop must be open and running)
```
Missing one? Install it first (Temurin JDK 17, Apache Maven, Node.js LTS, Docker Desktop).

### Step 2. Get a Gemini API key
Go to https://aistudio.google.com/apikey, create a key and copy it.

### Step 3. Start the database (Terminal 1)
In the project folder (the one containing `docker-compose.yml`):
```
docker compose up -d db
```
If you ran an older version of this project before, reset its database first:
`docker compose down -v` and then the command above. (The tables changed when login was added.)

### Step 4. Start the backend (Terminal 2)
```
cd backend
```
Set your key, using the line for your system:
```
macOS / Linux :  export GEMINI_API_KEY=your-key
PowerShell    :  $env:GEMINI_API_KEY="your-key"
Windows cmd   :  set GEMINI_API_KEY=your-key
```
Then:
```
mvn spring-boot:run
```
The first run downloads libraries (a few minutes). It is ready when you see `Started MarginApplication`. Leave it running.

### Step 5. Start the frontend (Terminal 3)
```
cd frontend
npm install
npm run dev
```
Open **http://localhost:5173** in your browser.

### Step 6. Use it
1. Click **Create an account** and sign up (email + password of 8+ characters).
2. Paste a small public repo URL, for example `https://github.com/your-name/ai-resume-builder`, and click **Index repo**.
3. Wait 1-3 minutes. The dot next to the repo turns green when it is ready.
4. Ask: "Where is JWT authentication handled?" and watch the steps appear.

## Run everything with Docker instead
```
cp .env.example .env        (Windows: copy .env.example .env)
```
Edit `.env`: set `GEMINI_API_KEY` and a `JWT_SECRET` (any random text, 32+ characters). Then:
```
docker compose up --build
```
Open **http://localhost:3000**.

## Security and limits
- Passwords are hashed with BCrypt. Login returns a JWT (valid 72 hours) sent as `Authorization: Bearer ...`.
- Each user only sees their own repositories.
- Limits (change in `application.yml`): 3 repositories per user, 30 questions per hour, 10 login attempts per 15 minutes.
- Set `SIGNUP_CODE` to any text and only people who know it can create accounts. Recommended for a public deployment.
- Always set your own `JWT_SECRET` when deploying. The default is for local use only.

## Deploying
One Ubuntu server with Docker: clone the repo, create `.env` (key, `JWT_SECRET`, `SIGNUP_CODE`), change the frontend port in
`docker-compose.yml` to `"80:80"`, run `docker compose up -d --build`, open `http://<server-ip>`.
The server needs a persistent disk because the agent reads the cloned repos from it.

## Troubleshooting
| Problem | Fix |
|---|---|
| `extension "vector" is not available` | Use the `pgvector/pgvector:pg16` image, not plain Postgres. |
| `relation ... does not exist` / `column owner_id` errors | Old tables from an earlier version. Run `docker compose down -v`, then start the database again. |
| Connection refused on 5432 | Docker isn't running, or the database container hasn't started. |
| `JWT_SECRET must be at least 32 characters` | Your `JWT_SECRET` is too short. Use 32+ characters or unset it to use the dev default. |
| "GEMINI_API_KEY is not set" | Set it in the same terminal you run `mvn spring-boot:run` in. |
| Indexing fails with a rate limit | Free-tier limits. Remove the repo, wait a minute, try again, or use a smaller repo. |
| "Model not found" | Change `gemini.chat-model` in `application.yml` to a current Gemini Flash model. |
| Page says it can't reach the backend | The backend isn't running on port 8080. |
| Signed out suddenly | The token expired (72 hours) or `JWT_SECRET` changed. Sign in again. |

## Resume bullets (adjust to what you really did)
- Built an agentic codebase Q&A tool: Spring Boot backend indexes any GitHub repo into PostgreSQL/pgvector with Gemini embeddings and answers through a tool-calling agent loop (semantic search, grep, file reader).
- Secured the API with Spring Security and JWT, per-user data isolation, BCrypt hashing and sliding-window rate limits.
- Streamed live agent steps to a React UI over Server-Sent Events; containerised with Docker Compose.
