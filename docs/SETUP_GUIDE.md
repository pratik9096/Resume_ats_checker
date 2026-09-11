# Resume-JD Match Checker (Chrome Extension + Spring Boot + Groq AI)

A Chrome extension that checks how well your resume matches a job description
before you apply — with a rule-based ATS match score plus AI-generated
improvement suggestions powered by Groq (Llama).

---

## Project Structure

```
resume-ats-project/
├── backend/                 Spring Boot REST API
│   ├── pom.xml
│   └── src/main/java/com/atschecker/resumeats/
│       ├── controller/       ResumeController, AnalyzeController
│       ├── service/          PdfExtractionService, MatchingService, GroqService
│       ├── model/            Resume, ScanHistory (JPA entities)
│       ├── repository/       Spring Data JPA repositories
│       ├── dto/               Request/response objects
│       └── config/            CORS config, global exception handler
├── extension/                Chrome Extension (Manifest V3)
│   ├── manifest.json
│   ├── popup.html / popup.css / popup.js
│   ├── content.js            Extracts JD text from job pages
│   ├── background.js         Service worker (badge state)
│   └── icons/
└── README.md (this file)
```

---

## Part 1 — Backend Setup

### Prerequisites
- Java 17+ (`java -version`)
- Maven 3.9+ (`mvn -version`) — or use your IDE's built-in Maven
- MySQL 8+ running locally (or a free cloud MySQL like Railway/Aiven)
- A free Groq API key from https://console.groq.com (no credit card required)

### Step 1: Create the database
```sql
CREATE DATABASE resume_ats_checker;
```
(Or skip this — `application.properties` has `createDatabaseIfNotExist=true`,
so Spring Boot will create it automatically on first run if your MySQL user
has permission.)

### Step 2: Configure `application.properties`
Open `backend/src/main/resources/application.properties` and update:
```properties
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

### Step 3: Set your Groq API key (as an environment variable — never hardcode it)
```bash
# macOS/Linux
export GROQ_API_KEY=your_actual_key_here

# Windows PowerShell
$env:GROQ_API_KEY="your_actual_key_here"
```

### Step 4: Build and run
```bash
cd backend
mvn clean install
mvn spring-boot:run
```
The backend will start on **http://localhost:8080**.

### Step 5: Verify it's working
```bash
curl -X POST http://localhost:8080/api/resume/upload -F "file=@/path/to/your/resume.pdf"
```
You should get back a JSON response with a `resumeId`.

> **Note on this sandbox:** this code was written and reviewed here, but not
> compiled here — Maven Central isn't reachable from this environment. Run
> `mvn clean install` on your own machine (with normal internet access) as
> your first step; if you hit a dependency or compile error, paste the error
> back to me and I'll fix it immediately.

---

## Part 2 — Load the Chrome Extension

1. Open Chrome and go to `chrome://extensions`
2. Turn on **Developer mode** (top-right toggle)
3. Click **Load unpacked**
4. Select the `extension/` folder
5. You'll see the extension icon appear in your toolbar

### Using it
1. Click the extension icon → upload your resume PDF once (stored on your backend, reused every time)
2. Visit a job posting on **Naukri** or **LinkedIn** → the extension auto-detects the JD (green badge on the icon)
3. Click the icon → the JD text box is pre-filled → click **Analyze Match**
4. On any other site, or if auto-detection fails, just paste the JD text manually — everything else works the same

### Reloading after code changes
Every time you edit extension files, go to `chrome://extensions` and click the
refresh icon on the extension's card.

---

## Part 3 — How Each Feature Maps to the Code

| Feature | File(s) |
|---|---|
| PDF text extraction | `PdfExtractionService.java` (Apache PDFBox) |
| Keyword/skill dictionary | `skills-dictionary.txt` |
| Match score + missing keywords | `MatchingService.java` (pure Java, no AI — deterministic & explainable) |
| ATS formatting checks | `MatchingService.runAtsFormatChecks()` |
| AI-generated suggestions | `GroqService.java` (calls Groq's chat completions API) |
| Scan history / "already checked this job" | `ScanHistory` entity + `AnalyzeController` |
| Auto-detect JD on Naukri/LinkedIn | `content.js` (site-specific selectors) |
| "Show more" button auto-click | `content.js` → `clickShowMoreIfPresent()` |
| SPA navigation re-detection | `content.js` → `MutationObserver` |
| Manual paste fallback | `popup.html` textarea (always editable) |
| Independent AI-failure handling | `AnalyzeController` calls matching + Groq separately; `AnalyzeResponse` has separate `aiSuggestionsAvailable` flag |

---

## Part 4 — Deployment (so you have a live link for your resume)

### Backend → Render (free tier)
1. Push the `backend/` folder to a GitHub repo
2. Go to https://render.com → New → Web Service → connect your repo
3. Environment: **Docker** or **Java**; Build command: `mvn clean install`; Start command: `java -jar target/resume-ats-checker.jar`
4. Add environment variables in Render's dashboard:
   - `GROQ_API_KEY` = your key
   - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (pointing to your cloud MySQL — Render, Railway, or Aiven all offer free/cheap MySQL)
5. Once deployed, note your live URL (e.g. `https://resume-ats-checker.onrender.com`)

### Update the extension to use your deployed backend
In `extension/popup.js`, change:
```js
const BACKEND_URL = "http://localhost:8080";
```
to:
```js
const BACKEND_URL = "https://resume-ats-checker.onrender.com";
```
Also add your deployed URL to `host_permissions` in `manifest.json`.

### Publish to Chrome Web Store (optional)
1. Zip the `extension/` folder
2. Go to the [Chrome Web Store Developer Dashboard](https://chrome.google.com/webstore/devconsole) → pay the one-time $5 fee
3. Upload the zip, fill in listing details, screenshots, and a short privacy note (you handle resume text — mention that clearly)
4. Submit for review (usually a few days)

This step is optional for a resume project — "Load unpacked" plus a short
screen-recording demo works perfectly well too.

---

## Part 5 — Talking About This Project in Interviews

Good points to mention:
- **Why rule-based scoring, not pure AI**: the match score is deterministic and explainable — every percentage point can be traced to a specific keyword. AI is used only where it adds real value (natural-language suggestions), not for the parts that need to be reliable.
- **Why Groq over OpenAI/Claude for this**: free tier + very fast inference, which matters for a snappy extension UX.
- **Security**: the AI API key never reaches the browser — it lives only on the backend, called server-side.
- **Graceful degradation**: if the Groq call fails, the match score and ATS warnings still show — one failing dependency doesn't break the whole feature.
- **Real-world edge cases handled**: SPA navigation (MutationObserver), truncated JD text behind "show more" buttons, unsupported sites (manual paste fallback), duplicate scans of the same job.

---

## Known Limitations (worth stating upfront, not hiding)
- Auto-detection is only built for Naukri and LinkedIn; other sites rely on manual paste.
- The skills dictionary is a static list — a fresher project scope, not a full NLP pipeline. Good next step to mention if asked "what would you improve?": using a proper NLP library (e.g. spaCy via a Python microservice) for smarter keyword extraction.
- No user accounts/login — resume is tied to a single `resumeId` stored locally in the browser, which is fine for personal use but would need auth for a multi-user product.
