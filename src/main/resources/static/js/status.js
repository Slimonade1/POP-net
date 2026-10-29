(() => {
    const dataElement = document.getElementById("status-data");
    if (!dataElement) {
        return;
    }
    const data = JSON.parse(dataElement.textContent);
    const now = new Date(data.now);

    const zone = "Europe/Copenhagen";
    const dayFormat = new Intl.DateTimeFormat("en-GB", {weekday: "short", day: "numeric", month: "short", timeZone: zone});
    const timeFormat = new Intl.DateTimeFormat("en-GB", {hour: "2-digit", minute: "2-digit", timeZone: zone});
    const labels = {o: "Working", s: "Having problems", d: "Down"};
    const classes = {o: "status--operational", s: "status--sick", d: "status--down"};

    const tip = document.createElement("div");
    tip.className = "tip";
    tip.setAttribute("role", "status");
    tip.hidden = true;
    document.body.append(tip);

    let range = "week";
    let active = null;
    let pointerFocus = false;

    function span(a, b, last) {
        const end = last ? "now"
            : dayFormat.format(a) === dayFormat.format(b) ? timeFormat.format(b)
            : `${dayFormat.format(b)}, ${timeFormat.format(b)}`;
        return `${dayFormat.format(a)}, ${timeFormat.format(a)} to ${end}`;
    }

    function bucket(index) {
        const starts = data.ranges[range].starts;
        const last = index === starts.length - 1;
        const from = new Date(starts[index]);
        const to = last ? now : new Date(starts[index + 1]);
        return {from, to, label: span(from, to, last)};
    }

    function patternOf(target) {
        return JSON.parse(target.dataset.patterns)[range];
    }

    function incidentsIn(target, from, to) {
        return JSON.parse(target.dataset.incidents || "[]").filter((incident) =>
            new Date(incident.start) < to && (incident.end === null || new Date(incident.end) > from));
    }

    function incidentLine(incident, from) {
        const start = new Date(incident.start);
        const end = incident.end === null ? "now" : timeFormat.format(new Date(incident.end));
        const day = dayFormat.format(start) === dayFormat.format(from) ? "" : `${dayFormat.format(start)}, `;
        return `${day}${timeFormat.format(start)} to ${end}: ${incident.summary}`;
    }

    function minutesText(ms) {
        const minutes = Math.max(1, Math.round(ms / 60000));
        if (minutes < 60) {
            return `${minutes} min`;
        }
        const hours = Math.floor(minutes / 60);
        return minutes % 60 ? `${hours} h ${minutes % 60} min` : `${hours} h`;
    }

    function shareLines(target, from, to) {
        const length = to - from;
        const totals = {d: 0, s: 0};
        for (const incident of incidentsIn(target, from, to)) {
            const start = Math.max(new Date(incident.start), from);
            const end = Math.min(incident.end === null ? now : new Date(incident.end), to);
            totals[incident.status] += Math.max(0, end - start);
        }
        return ["d", "s"].filter((status) => totals[status] > 0).map((status) => {
            const percent = Math.max(1, Math.round(totals[status] / length * 100));
            return `${labels[status]} ${minutesText(totals[status])} of ${minutesText(length)} (${percent} %)`;
        });
    }

    function slices(target) {
        return patternOf(target).length;
    }

    function indexAt(target, clientX) {
        const box = target.getBoundingClientRect();
        const count = slices(target);
        return Math.min(count - 1, Math.max(0, Math.floor((clientX - box.left) / box.width * count)));
    }

    function valueText(value) {
        return value === null ? "No answer" : `${value} ms`;
    }

    function describe(target, index) {
        const {from, to, label} = bucket(index);
        const shares = shareLines(target, from, to);
        const incidents = incidentsIn(target, from, to).map((incident) => incidentLine(incident, from));
        if (target.classList.contains("strip")) {
            const heading = shares.shift() ?? labels[patternOf(target)[index]];
            return [heading, label, ...shares, ...incidents];
        }
        return [valueText(JSON.parse(target.dataset.values)[range][index]), label, ...shares, ...incidents];
    }

    function cellBox(chart, index, count) {
        const width = chart.viewBox.baseVal.width;
        const scale = width / chart.getBoundingClientRect().width;
        const gap = 2 * scale;
        const cell = (width - gap * (count - 1)) / count;
        return {x: index * (cell + gap), width: cell, center: index * (cell + gap) + cell / 2};
    }

    function mark(target, index) {
        if (target.classList.contains("strip")) {
            for (const cell of target.children) {
                cell.classList.remove("is-selected");
            }
            target.children[index].classList.add("is-selected");
            return;
        }
        const cross = target.querySelector(".chart__cross");
        const {center} = cellBox(target, index, slices(target));
        cross.setAttribute("x1", center);
        cross.setAttribute("x2", center);
        cross.classList.add("is-visible");
    }

    function show(target, index) {
        if (active && active.target !== target) {
            hide();
        }
        active = {target, index};
        mark(target, index);

        const [heading, ...lines] = describe(target, index);
        const strong = document.createElement("strong");
        strong.textContent = heading;
        const parts = [strong];
        for (const line of lines) {
            const row = document.createElement("span");
            row.className = "tip__line";
            row.textContent = line;
            parts.push(row);
        }
        tip.replaceChildren(...parts);
        tip.hidden = false;

        const box = target.getBoundingClientRect();
        const center = box.left + box.width * (index + 0.5) / slices(target);
        const left = Math.min(Math.max(8, center - tip.offsetWidth / 2), innerWidth - tip.offsetWidth - 8);
        tip.style.left = `${left + scrollX}px`;
        tip.style.top = `${box.top + scrollY - tip.offsetHeight - 10}px`;
    }

    function hide() {
        if (!active) {
            return;
        }
        active.target.querySelectorAll(".is-selected").forEach((cell) => cell.classList.remove("is-selected"));
        active.target.querySelector(".chart__cross")?.classList.remove("is-visible");
        active = null;
        tip.hidden = true;
    }

    function drawStrip(strip) {
        strip.replaceChildren(...[...patternOf(strip)].map((status) => {
            const cell = document.createElement("span");
            cell.className = `strip__cell ${classes[status]}`;
            return cell;
        }));
    }

    function niceCeiling(value) {
        const magnitude = 10 ** Math.floor(Math.log10(Math.max(value, 1)));
        return [1, 2, 5, 10].map((factor) => factor * magnitude).find((step) => step >= value * 1.1);
    }

    function drawChart(chart) {
        const values = JSON.parse(chart.dataset.values)[range];
        const pattern = patternOf(chart);
        const height = chart.viewBox.baseVal.height;
        const max = niceCeiling(Math.max(...values.filter((value) => value !== null)));

        let path = "";
        let drawing = false;
        values.forEach((value, index) => {
            if (value === null) {
                drawing = false;
                return;
            }
            const x = cellBox(chart, index, values.length).center;
            const y = height - value / max * height;
            path += `${drawing ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)} `;
            drawing = true;
        });
        chart.querySelector(".chart__line").setAttribute("d", path.trim());

        const bands = chart.querySelector(".chart__bands");
        bands.replaceChildren(...[...pattern].flatMap((status, index) => {
            if (status === "o") {
                return [];
            }
            const band = document.createElementNS("http://www.w3.org/2000/svg", "rect");
            band.setAttribute("class", `chart__band ${classes[status]}`);
            const box = cellBox(chart, index, values.length);
            band.setAttribute("x", box.x);
            band.setAttribute("width", box.width);
            band.setAttribute("y", 0);
            band.setAttribute("height", height);
            return [band];
        }));

        const grid = chart.closest(".chart").querySelector(".chart__grid");
        grid.querySelector('[data-grid="top"]').textContent = `${max} ms`;
        grid.querySelector('[data-grid="middle"]').textContent = max / 2;
        chart.setAttribute("aria-label", `Response time in the ${data.ranges[range].period}, up to ${max} ms`);
    }

    function filterIncidents() {
        const from = new Date(data.ranges[range].from);
        for (const item of document.querySelectorAll(".incident[data-start]")) {
            item.hidden = !(new Date(item.dataset.start) < now && new Date(item.dataset.end) > from);
        }
        for (const group of document.querySelectorAll(".past__group")) {
            group.hidden = !group.querySelector(".incident:not([hidden])");
        }
        for (const empty of document.querySelectorAll("[data-incidents-empty]")) {
            empty.hidden = Boolean(empty.parentElement.querySelector(".incident:not([hidden])"));
        }
    }

    function selectRange(name) {
        hide();
        range = name;
        for (const button of document.querySelectorAll(".ranges button")) {
            button.setAttribute("aria-pressed", String(button.dataset.range === name));
        }
        document.querySelectorAll("[data-period]").forEach((el) => el.textContent = data.ranges[name].period);
        document.querySelectorAll("[data-axis-start]").forEach((el) => el.textContent = data.ranges[name].axisStart);
        document.querySelectorAll("[data-uptime]").forEach((el) => el.textContent = JSON.parse(el.dataset.uptime)[name]);
        document.querySelectorAll(".strip[data-patterns]").forEach(drawStrip);
        document.querySelectorAll(".chart__plot[data-values]").forEach(drawChart);
        filterIncidents();
    }

    for (const target of document.querySelectorAll(".strip[data-patterns], .chart__plot[data-values]")) {
        target.addEventListener("pointerdown", (event) => {
            pointerFocus = true;
            show(target, indexAt(target, event.clientX));
        });
        target.addEventListener("pointermove", (event) => {
            if (event.pointerType === "mouse" || event.buttons) {
                show(target, indexAt(target, event.clientX));
            }
        });
        target.addEventListener("pointerleave", (event) => {
            if (event.pointerType === "mouse") {
                hide();
            }
        });
        target.addEventListener("focus", () => {
            if (!pointerFocus) {
                show(target, slices(target) - 1);
            }
            pointerFocus = false;
        });
        target.addEventListener("blur", hide);
        target.addEventListener("keydown", (event) => {
            const moves = {ArrowLeft: -1, ArrowRight: 1, Home: -Infinity, End: Infinity};
            if (!active || active.target !== target || !(event.key in moves)) {
                return;
            }
            event.preventDefault();
            show(target, Math.min(slices(target) - 1, Math.max(0, active.index + moves[event.key])));
        });
    }

    for (const button of document.querySelectorAll(".ranges button")) {
        button.addEventListener("click", () => selectRange(button.dataset.range));
    }

    document.addEventListener("pointerdown", (event) => {
        if (active && !active.target.contains(event.target)) {
            hide();
        }
    });
    addEventListener("resize", () => {
        hide();
        document.querySelectorAll(".chart__plot[data-values]").forEach(drawChart);
    });

    selectRange(document.querySelector('.ranges button[aria-pressed="true"]')?.dataset.range ?? "week");
})();
