# Resume-JD Match Checker

A Chrome extension + Spring Boot backend that scores your resume against a
job description in real time, with AI-powered improvement suggestions.

**Read [`docs/SETUP_GUIDE.md`](docs/SETUP_GUIDE.md) for full setup and deployment instructions.**

## Quick Start
1. `cd backend && mvn clean install && mvn spring-boot:run`
2. Load `extension/` folder as an unpacked extension in `chrome://extensions`
3. Upload your resume, visit a Naukri/LinkedIn job page, click the extension icon, hit Analyze

## Tech Stack
Java 17 · Spring Boot 3 · Spring Data JPA · MySQL · Apache PDFBox · Groq API (Llama 3.3) · JavaScript · Chrome Extension (Manifest V3) · JUnit 5
