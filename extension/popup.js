// popup.js
// State machine: NO_RESUME -> READY -> LOADING -> RESULTS / ERROR

const BACKEND_URL = "http://localhost:8080"; // change to your deployed URL later

const els = {
  noResume: document.getElementById("state-no-resume"),
  ready: document.getElementById("state-ready"),
  loading: document.getElementById("state-loading"),
  results: document.getElementById("state-results"),
  error: document.getElementById("state-error"),

  fileInput: document.getElementById("resume-file-input"),
  uploadBtn: document.getElementById("upload-btn"),
  uploadStatus: document.getElementById("upload-status"),

  resumeFilename: document.getElementById("resume-filename"),
  changeResumeBtn: document.getElementById("change-resume-btn"),
  jdTextarea: document.getElementById("jd-textarea"),
  detectionNote: document.getElementById("detection-note"),
  analyzeBtn: document.getElementById("analyze-btn"),

  scoreCircle: document.getElementById("score-circle"),
  scoreValue: document.getElementById("score-value"),
  previousScanNote: document.getElementById("previous-scan-note"),
  matchedKeywords: document.getElementById("matched-keywords"),
  missingKeywords: document.getElementById("missing-keywords"),
  atsWarningsList: document.getElementById("ats-warnings-list"),
  aiSuggestionsList: document.getElementById("ai-suggestions-list"),
  aiErrorNote: document.getElementById("ai-error-note"),
  analyzeAgainBtn: document.getElementById("analyze-again-btn"),

  errorMessage: document.getElementById("error-message"),
  retryBtn: document.getElementById("retry-btn"),
};

let currentJobUrl = "";
let currentJobTitle = "";

function showState(name) {
  [els.noResume, els.ready, els.loading, els.results, els.error].forEach((s) => s.classList.add("hidden"));
  els[name].classList.remove("hidden");
}

async function getStoredResume() {
  return new Promise((resolve) => {
    chrome.storage.local.get(["resumeId", "resumeFileName"], (data) => resolve(data));
  });
}

async function init() {
  const { resumeId, resumeFileName } = await getStoredResume();

  if (!resumeId) {
    // Scenario 6: no resume uploaded yet
    showState("noResume");
    return;
  }

  els.resumeFilename.textContent = resumeFileName || "resume.pdf";
  showState("ready");
  await tryAutoDetectJD();
}

// --- Upload flow ---
els.uploadBtn.addEventListener("click", async () => {
  const file = els.fileInput.files[0];
  if (!file) {
    els.uploadStatus.textContent = "Please choose a PDF file first.";
    els.uploadStatus.classList.add("error");
    return;
  }

  els.uploadBtn.disabled = true;
  els.uploadStatus.classList.remove("error");
  els.uploadStatus.textContent = "Uploading...";

  try {
    const formData = new FormData();
    formData.append("file", file);

    const res = await fetch(`${BACKEND_URL}/api/resume/upload`, {
      method: "POST",
      body: formData,
    });
    const data = await res.json();

    if (!res.ok) {
      throw new Error(data.error || "Upload failed.");
    }

    chrome.storage.local.set({ resumeId: data.resumeId, resumeFileName: data.fileName }, () => {
      els.resumeFilename.textContent = data.fileName;
      showState("ready");
      tryAutoDetectJD();
    });
  } catch (err) {
    els.uploadStatus.textContent = err.message || "Could not reach the server. Is the backend running?";
    els.uploadStatus.classList.add("error");
  } finally {
    els.uploadBtn.disabled = false;
  }
});

els.changeResumeBtn.addEventListener("click", () => {
  chrome.storage.local.remove(["resumeId", "resumeFileName"], () => {
    showState("noResume");
  });
});

// --- JD auto-detection (Scenarios 1, 2, 3, 5) ---
async function tryAutoDetectJD() {
  els.jdTextarea.value = "";
  els.detectionNote.textContent = "Checking this page for a job description...";

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

    // Scenario 3: unsupported site - content script never injected there
    const supportedSite = tab?.url && (tab.url.includes("naukri.com") || tab.url.includes("linkedin.com"));
    if (!supportedSite) {
      els.detectionNote.textContent = "This site isn't auto-supported yet - paste the job description below.";
      return;
    }

    chrome.tabs.sendMessage(tab.id, { type: "GET_CURRENT_JD" }, (response) => {
      if (chrome.runtime.lastError || !response) {
        // Scenario 2: content script didn't respond / page not ready
        els.detectionNote.textContent = "Couldn't auto-detect the JD on this page - paste it below.";
        return;
      }

      currentJobUrl = response.jobUrl || tab.url || "";
      currentJobTitle = response.jobTitle || tab.title || "";

      if (response.detected && response.jobDescription) {
        els.jdTextarea.value = response.jobDescription;
        els.detectionNote.textContent = "Job description auto-detected - feel free to edit before analyzing.";
      } else {
        els.detectionNote.textContent = "Couldn't auto-detect the JD on this page - paste it below.";
      }
    });
  } catch (err) {
    els.detectionNote.textContent = "Paste the job description below to analyze.";
  }
}

// --- Analyze flow ---
els.analyzeBtn.addEventListener("click", () => runAnalysis());
els.analyzeAgainBtn.addEventListener("click", () => {
  showState("ready");
  tryAutoDetectJD();
});
els.retryBtn.addEventListener("click", () => runAnalysis());

async function runAnalysis() {
  const jobDescription = els.jdTextarea.value.trim();

  // Scenario 8: reject too-short/invalid input client-side too (fast feedback)
  if (jobDescription.split(/\s+/).length < 30) {
    showState("error");
    els.errorMessage.textContent = "This doesn't look like a full job description. Please paste the complete JD text.";
    return;
  }

  const { resumeId } = await getStoredResume();
  if (!resumeId) {
    showState("noResume");
    return;
  }

  showState("loading");

  try {
    const res = await fetch(`${BACKEND_URL}/api/analyze`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        resumeId,
        jobDescription,
        jobUrl: currentJobUrl,
        jobTitle: currentJobTitle,
      }),
    });

    const data = await res.json();

    if (!res.ok) {
      throw new Error(data.error || "Analysis failed. Please try again.");
    }

    renderResults(data);
  } catch (err) {
    // Scenario 7: backend/network error - clear message, never a frozen popup
    showState("error");
    els.errorMessage.textContent = err.message || "Could not reach the server. Is the backend running?";
  }
}

function renderResults(data) {
  showState("results");

  els.scoreValue.textContent = `${data.matchScorePercent}%`;
  const color = data.matchScorePercent >= 70 ? "#16a34a" : data.matchScorePercent >= 40 ? "#d97706" : "#dc2626";
  els.scoreCircle.style.background = color;

  if (data.previouslyScanned && data.previousScanSummary) {
    els.previousScanNote.textContent = data.previousScanSummary;
    els.previousScanNote.classList.remove("hidden");
  } else {
    els.previousScanNote.classList.add("hidden");
  }

  els.matchedKeywords.innerHTML = (data.matchedKeywords || [])
    .map((k) => `<span>${escapeHtml(k)}</span>`).join("") || "<span>None found</span>";

  els.missingKeywords.innerHTML = (data.missingKeywords || [])
    .map((k) => `<span>${escapeHtml(k)}</span>`).join("") || "<span>None - great match!</span>";

  els.atsWarningsList.innerHTML = (data.atsWarnings || []).length
    ? data.atsWarnings.map((w) => `<li>${escapeHtml(w)}</li>`).join("")
    : "<li>No formatting issues detected.</li>";

  // Scenario 7: AI suggestions can independently fail without breaking the score
  if (data.aiSuggestionsAvailable && data.aiSuggestions && data.aiSuggestions.length) {
    els.aiSuggestionsList.innerHTML = data.aiSuggestions.map((s) => `<li>${escapeHtml(s)}</li>`).join("");
    els.aiErrorNote.classList.add("hidden");
  } else {
    els.aiSuggestionsList.innerHTML = "";
    els.aiErrorNote.textContent = data.aiErrorMessage || "AI suggestions unavailable right now - try again shortly.";
    els.aiErrorNote.classList.remove("hidden");
  }
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

init();
