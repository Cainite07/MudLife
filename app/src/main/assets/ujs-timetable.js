// Inspect the currently rendered personal timetable. Do not submit forms or change the school page.
(function readTimetable(courseUrl) {
    "use strict";
    const fail = (message, waiting = false) => ({ error: message, waiting });
    const text = (node) => (node && (node.innerText || node.textContent) || "").trim();
    const shown = (node) => node.getClientRects().length > 0 &&
        node.ownerDocument.defaultView.getComputedStyle(node).visibility !== "hidden";
    // Android supplies the canonical route for this WebView, without any student data.
    const course = new URL(courseUrl);
    const basePath = course.pathname.slice(0, course.pathname.lastIndexOf("/kbcx/")) + "/";
    const inAcademicSite = (location) => location.origin === course.origin &&
        location.pathname.startsWith(basePath);
    const isCoursePage = (location) => inAcademicSite(location) && location.pathname === course.pathname;
    if (!isCoursePage(document.location)) return fail("请在当前教务入口重新打开个人课表。");

    // The academic portal can open menu pages inside a same-origin frame.
    // Ignore inaccessible documents and inactive semester/menu panels.
    function addTables(doc, tables, depth) {
        if (!inAcademicSite(doc.location)) return;
        if (isCoursePage(doc.location)) {
            const table = doc.getElementById("kbgrid_table_0");
            if (table && shown(table)) tables.push({ table, location: doc.location });
        }
        if (depth === 4) return;
        for (const frame of doc.querySelectorAll("iframe, frame")) {
            if (!shown(frame)) continue;
            try {
                if (frame.contentDocument) addTables(frame.contentDocument, tables, depth + 1);
            } catch (_) {
                // Cross-origin frames do not expose their documents.
            }
        }
    }

    function pending(block, title) {
        if (/待筛选/.test(text(title))) return true;
        // The school's table legend explicitly identifies red italic titles as pending.
        for (const node of [title, ...title.querySelectorAll("*")]) {
            const style = block.ownerDocument.defaultView.getComputedStyle(node);
            const rgb = style.color.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
            if (rgb && Number(rgb[1]) >= 150 && Number(rgb[1]) > Number(rgb[2]) * 1.8 &&
                Number(rgb[1]) > Number(rgb[3]) * 1.8 &&
                (style.fontStyle !== "normal" || Number(rgb[2]) + Number(rgb[3]) === 0)) return true;
        }
        return false;
    }

    function readCourse(block, slot) {
        const title = block.querySelector(".title");
        const name = text(title).replace(/\s+/g, " ");
        if (!name || name.length > 160) return null;
        if (pending(block, title)) return { pending: true };
        const fields = {};
        for (const p of block.querySelectorAll("p")) {
            const icon = Array.from(p.children).find((child) => child.tagName === "SPAN");
            if (!icon) continue;
            const label = (icon.getAttribute("data-original-title") ||
                icon.getAttribute("title") || "").trim();
            const key = /^(节\s*\/\s*周|节次\/周次)$/.test(label) ||
                (icon.classList.contains("glyphicon-time") || icon.querySelector(".glyphicon-time")) ? "time" :
                label === "上课地点" ? "place" : label === "教师" ? "teacher" : "";
            if (!key) continue;
            const value = text(Array.from(p.children).find((child) => child.tagName === "FONT"));
            if (Object.prototype.hasOwnProperty.call(fields, key) || value.length > 300) return null;
            fields[key] = value;
        }
        const schedule = (fields.time || "").match(
            /^[（(]\s*(\d{1,2})(?:\s*[-－–—~～至]\s*(\d{1,2}))?\s*节\s*[)）]\s*(.+)$/
        );
        if (!schedule) return null;
        const [weekday, startPeriod] = slot;
        const endPeriod = Number(schedule[2] || schedule[1]);
        if (Number(schedule[1]) !== startPeriod || endPeriod < startPeriod || endPeriod > 30) return null;
        const weeks = schedule[3].trim();
        // Preserve the school's week notation, including discontinuous and odd/even weeks.
        const ranges = weeks.split(/[,，、;；]/);
        if (weeks.length > 200 || !ranges.every((range) => {
            const r = range.trim().match(/^(\d{1,2})(?:\s*[-－–—~～至]\s*(\d{1,2}))?\s*周?(?:\s*[（(]\s*[单双]\s*周?\s*[)）])?$/);
            return r && Number(r[1]) >= 1 && Number(r[2] || r[1]) >= Number(r[1]) &&
                Number(r[2] || r[1]) <= 53;
        })) return null;
        return { name, weekday, startPeriod, endPeriod, weeks,
            place: fields.place || "", teacher: fields.teacher || "" };
    }

    const tables = [];
    addTables(document, tables, 0);
    if (tables.length !== 1) return fail("请在教务网页打开“学生个人课表”，选好学期并查询。", tables.length === 0);
    const { table, location } = tables[0];
    const doc = table.ownerDocument;
    if (doc.readyState === "loading" || Array.from(doc.querySelectorAll(
        "[aria-busy='true'], .blockUI.blockOverlay, .layui-layer-loading, #loading"
    )).some(shown) || (doc.defaultView.jQuery && doc.defaultView.jQuery.active > 0)) {
        return fail("教务正在加载，请等待查询完成。", true);
    }
    const header = table.rows[0] && table.rows[0].cells[0];
    const termNode = header && header.querySelector("h6");
    const term = text(termNode).match(/^(\d{4})\s*[-－–—]\s*(\d{4})\s*学年\s*第\s*([123一二三])\s*学期$/);
    if (!term || Number(term[2]) !== Number(term[1]) + 1) return fail("未读到该课表的学年学期，请重新查询。", true);
    const termNumber = { "一": "1", "二": "2", "三": "3" }[term[3]] || term[3];
    // Changing an academic dropdown can leave the previous table in place until 查询 completes.
    const selected = (key) => {
        const select = doc.querySelector("select#" + key + ", select[name='" + key + "']");
        return select && select.options[select.selectedIndex] || null;
    };
    const yearOption = selected("xnm");
    const termOption = selected("xqm");
    if (yearOption || termOption) {
        const year = yearOption && (text(yearOption).match(/(\d{4})/) || String(yearOption.value).match(/^(\d{4})$/));
        const label = termOption && text(termOption).match(/^(?:第\s*)?([一二三123])(?:\s*学期)?$/);
        const semester = label && ({ "一": "1", "二": "2", "三": "3" }[label[1]] || label[1]);
        if ((yearOption && (!year || year[1] !== term[1])) || (termOption && semester !== termNumber)) {
            return fail("网页所选学期与课表表头尚未一致，请点校方“查询”。", true);
        }
    }
    const student = text(header).match(/学\s*号\s*[:：]\s*([A-Za-z0-9_.-]+)/);
    if (!student) return fail("课表未显示学号，无法核对当前账号。");
    const rows = [];
    let pendingCount = 0;
    let unknown = 0;
    let seen = 0;
    const allBlocks = table.querySelectorAll(".timetable_con");
    for (const cell of table.querySelectorAll("td.td_wrap")) {
        const id = cell.id.match(/^([1-7])-([1-9]\d?)$/);
        const blocks = cell.querySelectorAll(".timetable_con");
        // Reject unfamiliar nonempty cells instead of saving only the familiar portion.
        const remainder = cell.cloneNode(true);
        for (const node of remainder.querySelectorAll(".timetable_con, script, style")) node.remove();
        if (text(remainder).replace(/[\s\u00a0]/g, "")) unknown++;
        for (const block of blocks) {
            seen++;
            const course = id && readCourse(block, [Number(id[1]), Number(id[2])]);
            if (!course) unknown++;
            else if (course.pending) pendingCount++;
            else rows.push(course);
        }
    }
    if (unknown || seen !== allBlocks.length) return fail("课表中有未能识别的课程或节次。请核对校方网页，本次未导入。");
    if (!rows.length) return fail(pendingCount ? "当前课表仅显示待筛选课程，未导入。" : "所选学期没有可读取的课程，未导入。");
    // A table can be inside the academic portal's frame. Exclude its query and fragment.
    const coursePage = location.protocol + "//" + location.hostname + location.pathname;
    return { term: term[1] + "-" + term[2] + "学年第" + termNumber + "学期", rows, pendingCount,
        coursePage, student: student[1] };
})
