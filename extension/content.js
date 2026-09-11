// content.js
// Runs on Naukri and LinkedIn job pages. Extracts the job description text,
// clicks "show more" if present, and re-extracts on SPA navigation.
// Covers Scenarios 1, 2, 4, 5 from the design.

(function () {
  // Site-specific selectors, tried in order. If all fail, we fall back to a
  // generic heuristic (largest text block), and if THAT fails too, the popup
  // simply shows an empty textarea for manual paste (Scenario 2/3).
  const SITE_SELECTORS = {
    "naukri.com": {
      jd: [".styles_JDC__dang-inner-html__h0K4t", ".job-desc", "[class*='jobDescription']"],
      showMore: null // Naukri usually shows full JD on the job page already
    },
    "linkedin.com": {
      jd: [".jobs-description__content", ".jobs-box__html-content", "#job-details"],
      showMore: ["button.jobs-description__footer-button", "button[aria-label*='Click to see more']"]
    }
  };

  function getSiteConfig() {
    const host = window.location.hostname;
    for (const key of Object.keys(SITE_SELECTORS)) {
      if (host.includes(key)) return SITE_SELECTORS[key];
    }
    return null;
  }

  function clickShowMoreIfPresent(config) {
    if (!config || !config.showMore) return Promise.resolve();
    for (const sel of config.showMore) {
      const btn = document.querySelector(sel);
      if (btn) {
        btn.click();
        // give the DOM time to expand
        return new Promise((resolve) => setTimeout(resolve, 400));
      }
    }
    return Promise.resolve();
  }

  function tryExtractWithSelectors(config) {
    if (!config) return null;
    for (const sel of config.jd) {
      const el = document.querySelector(sel);
      if (el && el.innerText && el.innerText.trim().length > 100) {
        return el.innerText.trim();
      }
    }
    return null;
  }

  // Fallback heuristic: find the element on the page with the most text,
  // used when site-specific selectors fail (page redesign, unsupported layout).
  function fallbackExtractLargestTextBlock() {
    let best = null;
    let bestLength = 0;
    document.querySelectorAll("div, section, article").forEach((el) => {
      const text = el.innerText || "";
      if (text.length > bestLength && text.length < 20000) {
        // avoid grabbing the whole page/body wrapper
        if (el.children.length < 50) {
          bestLength = text.length;
          best = text;
        }
      }
    });
    return bestLength > 150 ? best.trim() : null;
  }

  async function extractAndReport() {
    const config = getSiteConfig();
    await clickShowMoreIfPresent(config);

    let jdText = tryExtractWithSelectors(config);
    if (!jdText) {
      jdText = fallbackExtractLargestTextBlock();
    }

    if (jdText) {
      window.__lastDetectedJD = jdText;
      window.__lastDetectedTitle = document.title;
      chrome.runtime.sendMessage({ type: "JD_DETECTED" });
    } else {
      window.__lastDetectedJD = null;
      chrome.runtime.sendMessage({ type: "JD_NOT_DETECTED" });
    }
  }

  // Respond to the popup asking for the current JD text
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "GET_CURRENT_JD") {
      // Re-extract fresh every time the popup opens (Scenario 5: SPA navigation)
      extractAndReport().then(() => {
        sendResponse({
          jobDescription: window.__lastDetectedJD || "",
          jobTitle: window.__lastDetectedTitle || document.title,
          jobUrl: window.location.href,
          detected: !!window.__lastDetectedJD
        });
      });
      return true; // keep sendResponse channel open for async work
    }
  });

  // Initial extraction on page load
  extractAndReport();

  // Scenario 5: Naukri/LinkedIn are SPAs - watch for DOM changes so switching
  // between job listings without a full page reload still re-triggers detection.
  let debounceTimer = null;
  const observer = new MutationObserver(() => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(extractAndReport, 600);
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
