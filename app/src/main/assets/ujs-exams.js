// Query the official personal exam grid in the same WebView session.
(async function queryExams(examPage, coursePage, year, semester, semesterCode) {
    const page = new URL(examPage);
    const course = new URL(coursePage);
    if (location.origin !== page.origin || location.pathname !== page.pathname || course.origin !== page.origin) {
        return { error: "请重新打开学校的考试信息查询页。" };
    }

    const query = new URL(page);
    query.searchParams.delete("layout");
    query.searchParams.set("doType", "query");
    const headers = { "X-Requested-With": "XMLHttpRequest", "Accept": "application/json" };
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    const fail = (error) => ({ error });
    const invalid = () => fail("学校返回的考试信息不完整，请在教务网页核对后重试。");
    const field = (row, key) => {
        const value = row[key];
        return typeof value === "string" || typeof value === "number" ? String(value).trim() : "";
    };
    const matches = (response, url) => {
        const actual = new URL(response.url);
        return actual.origin === url.origin && actual.pathname === url.pathname;
    };
    const authPage = (response) => response.status === 401 || /\/(?:cas\/login|xtgl\/login[^/]*|login)$/.test(new URL(response.url).pathname);

    try {
        const pageSize = 50;
        let rows = [];
        let expected = null;
        for (let index = 1; index <= 20; index++) {
            const response = await fetch(query, {
                method: "POST",
                credentials: "same-origin",
                signal: controller.signal,
                headers,
                body: new URLSearchParams({
                    xnm: year, xqm: semesterCode, ksmcdmb_id: "", kch: "", kc: "", ksrq: "", kkbm_id: "",
                    "queryModel.showCount": pageSize, "queryModel.currentPage": index,
                    "queryModel.sortName": "", "queryModel.sortOrder": "asc",
                    _search: "false", nd: Date.now(), time: "0"
                })
            });
            if (authPage(response)) return { auth: true };
            if (!response.ok || !matches(response, query)) return invalid();

            const text = await response.text();
            if (text.trimStart().startsWith("<")) {
                const doc = new DOMParser().parseFromString(text, "text/html");
                if (doc.querySelector('input[type="password"]') ||
                    doc.querySelector("h5")?.textContent.trim() === "用户登录") return { auth: true };
                return invalid();
            }
            const payload = JSON.parse(text);
            const recordCount = payload?.totalResult ?? payload?.records ?? payload?.totalCount;
            if (!/^\d+$/.test(String(recordCount))) return invalid();
            const count = Number(recordCount);
            const items = payload.items == null && count === 0 ? [] : payload.items;
            if (count > 20 * pageSize || !Array.isArray(items) || (expected !== null && expected !== count) ||
                (items.length === 0 && rows.length < count)) return invalid();
            expected = count;
            rows = rows.concat(items);
            if (rows.length >= count) break;
        }
        if (rows.length !== expected) return invalid();
        // The teaching page replaces Array.some. Check the returned rows directly.
        let student = "";
        let needsIdentity = rows.length === 0;
        for (const row of rows) {
            if (!row || !field(row, "kcmc")) return invalid();
            const rowYear = field(row, "xnm");
            const rowTerm = field(row, "xqm");
            if ((rowYear && rowYear !== String(year)) ||
                (rowTerm && rowTerm !== String(semester) && rowTerm !== String(semesterCode))) return invalid();
            const rowStudent = field(row, "xh");
            if (!rowStudent) needsIdentity = true;
            else if (student && student !== rowStudent) return fail("教务考试结果包含不同学号，无法导入。");
            else student = rowStudent;
        }

        // Empty results and grids that omit student IDs still need a verified account before replacing a cache.
        if (needsIdentity) {
            const identity = new URL("xskbcx_cxXsKb.html?gnmkdm=N2151", course);
            const response = await fetch(identity, {
                method: "POST", credentials: "same-origin", signal: controller.signal, headers,
                body: new URLSearchParams({ xnm: year, xqm: semesterCode })
            });
            if (authPage(response)) return { auth: true };
            if (!response.ok || !matches(response, identity)) return invalid();
            const personal = await response.json().catch(() => null);
            const courseStudent = personal?.xsxx && field(personal.xsxx, "XH");
            if (!courseStudent) return fail("教务页面未显示学号，无法核对当前账号。");
            if (student && student !== courseStudent) return fail("教务考试结果与当前教务账号不一致。");
            student = courseStudent;
        }

        return {
            student,
            rows: rows.map(row => ({
                course: field(row, "kcmc"), time: field(row, "kssj"), room: field(row, "cdmc"),
                campus: field(row, "cdxqmc") || field(row, "xqmc"), seat: field(row, "zwh"), session: field(row, "ksmc"),
                method: field(row, "ksfs"), note: field(row, "ksbz") || field(row, "bz1") || field(row, "bz")
            }))
        };
    } catch (error) {
        return fail(error.name === "AbortError" ? "教务查询超时，请重试。" : "无法读取学校的考试信息，请重试。");
    } finally {
        clearTimeout(timeout);
    }
})
