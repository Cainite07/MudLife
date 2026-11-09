(function (checkHome, checkMissing, httpNotFound) {
    const body = document.body;
    if (!body || document.readyState === "loading") return false;
    const visible = el => el.getClientRects().length > 0 &&
        el.ownerDocument.defaultView.getComputedStyle(el).visibility !== "hidden";
    const hasLoginForm = doc => Array.from(doc.querySelectorAll('input[type="password"]')).some(visible);
    if (hasLoginForm(document)) return false;
    const text = body.innerText.trim();
    if (checkHome && text.includes("功能菜单") && text.includes("我的应用")) return true;
    if (!checkMissing) return false;
    const missing = value => value.length <= 400 && /\b404\b/.test(value) &&
        value.replace(/\s/g, "").includes("您访问的页面不存在");
    if (httpNotFound || missing(text)) return true;
    // Some portal shells show the entire result in one same-origin frame.
    if (text) return false;
    const frames = Array.from(body.querySelectorAll("iframe")).filter(visible);
    if (frames.length !== 1) return false;
    const rect = frames[0].getBoundingClientRect();
    if (rect.width < window.innerWidth * .75 || rect.height < window.innerHeight * .5) return false;
    const doc = frames[0].contentDocument;
    return !!doc && !!doc.body && !hasLoginForm(doc) && missing(doc.body.innerText.trim());
})
