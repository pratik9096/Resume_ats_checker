// background.js - Manifest V3 service worker
// Keeps track of whether a JD was detected on the currently active tab,
// and updates the toolbar badge accordingly (Scenario 1/2/3 from the plan).

const tabJdStatus = {}; // tabId -> boolean (JD detected or not)

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === "JD_DETECTED" && sender.tab) {
    tabJdStatus[sender.tab.id] = true;
    chrome.action.setBadgeText({ tabId: sender.tab.id, text: "\u2713" });
    chrome.action.setBadgeBackgroundColor({ tabId: sender.tab.id, color: "#16a34a" });
  }
  if (message.type === "JD_NOT_DETECTED" && sender.tab) {
    tabJdStatus[sender.tab.id] = false;
    chrome.action.setBadgeText({ tabId: sender.tab.id, text: "" });
  }
  // Always return true if we might respond asynchronously elsewhere
  return false;
});

chrome.tabs.onRemoved.addListener((tabId) => {
  delete tabJdStatus[tabId];
});
