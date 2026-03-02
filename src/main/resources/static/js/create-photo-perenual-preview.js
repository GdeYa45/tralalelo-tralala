// 6.5 (P1) AJAX “подтянуть вид из Perenual по выбранному кандидату”
// DoD: после клика по кандидату UI делает запрос и получает карточку вида/превью + кнопку “Импортировать”.

(() => {
    "use strict";

    const $ = (id) => document.getElementById(id);

    function show(el) { if (el) el.style.display = "block"; }
    function hide(el) { if (el) el.style.display = "none"; }
    function setText(el, t) { if (el) el.textContent = t ?? ""; }
    function clear(el) { if (!el) return; while (el.firstChild) el.removeChild(el.firstChild); }

    function readCsrf() {
        const token = $("csrfToken") ? $("csrfToken").value : "";
        const header = $("csrfHeader") ? $("csrfHeader").value : "X-CSRF-TOKEN";
        return { token, header };
    }

    async function doFetch(url, options) {
        // если этап 6.3 уже подключён глобально — используем csrfFetch
        if (window.csrfFetch && typeof window.csrfFetch === "function") {
            return window.csrfFetch(url, options);
        }

        // fallback: добавим CSRF заголовок вручную
        const opts = options ? { ...options } : {};
        const headers = { ...(opts.headers || {}) };
        const { token, header } = readCsrf();
        if (token && header && !headers[header]) headers[header] = token;
        opts.headers = headers;
        return fetch(url, opts);
    }

    function buildPreviewCard(data) {
        const card = $("perenualPreviewCard");
        clear(card);

        const wrap = document.createElement("div");
        wrap.className = "perenual-preview";

        if (data.imageUrl) {
            const img = document.createElement("img");
            img.src = data.imageUrl;
            img.alt = data.name || "preview";
            img.className = "perenual-preview-img";
            wrap.appendChild(img);
        }

        const info = document.createElement("div");

        const title = document.createElement("div");
        title.className = "perenual-preview-title";
        title.textContent = data.name || "(без названия)";
        info.appendChild(title);

        if (data.scientificName) {
            const sci = document.createElement("div");
            sci.className = "muted";
            sci.style.marginTop = "4px";
            sci.textContent = `Scientific: ${data.scientificName}`;
            info.appendChild(sci);
        }

        const meta = document.createElement("div");
        meta.className = "muted";
        meta.style.marginTop = "6px";
        meta.textContent = `Perenual ID: ${data.perenualId}`;
        info.appendChild(meta);

        const actions = document.createElement("div");
        actions.style.marginTop = "10px";
        actions.style.display = "flex";
        actions.style.gap = "10px";
        actions.style.flexWrap = "wrap";

        if (data.alreadyImported && data.localSpeciesId) {
            const ok = document.createElement("span");
            ok.className = "badge";
            ok.textContent = "Уже в каталоге";
            actions.appendChild(ok);

            const link = document.createElement("a");
            link.className = "btn";
            link.href = `/app/species/${data.localSpeciesId}`;
            link.textContent = "Открыть карточку вида";
            actions.appendChild(link);
        } else {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "btn btn-primary";
            btn.textContent = "Импортировать";
            btn.dataset.perenualId = String(data.perenualId);
            btn.id = "btnPerenualImport";

            btn.addEventListener("click", async () => {
                btn.disabled = true;
                setText($("perenualPreviewStatus"), "Импортирую...");
                hide($("perenualPreviewError"));
                setText($("perenualPreviewError"), "");

                try {
                    const resp = await doFetch("/api/perenual/import", {
                        method: "POST",
                        headers: {
                            "Accept": "application/json",
                            "Content-Type": "application/json",
                            "X-Requested-With": "XMLHttpRequest",
                        },
                        body: JSON.stringify({ perenualId: Number(btn.dataset.perenualId) }),
                    });

                    const json = await resp.json().catch(() => null);

                    if (!resp.ok) {
                        const msg = json && (json.message || json.code)
                            ? `${json.code ?? "ERROR"}: ${json.message ?? ""}`
                            : "Ошибка импорта";
                        setText($("perenualPreviewStatus"), "");
                        setText($("perenualPreviewError"), msg);
                        show($("perenualPreviewError"));
                        btn.disabled = false;
                        return;
                    }

                    setText($("perenualPreviewStatus"), "Импортировано.");
                    buildPreviewCard({
                        ...data,
                        alreadyImported: true,
                        localSpeciesId: json.localSpeciesId,
                    });

                } catch (e) {
                    setText($("perenualPreviewStatus"), "");
                    setText($("perenualPreviewError"), "Ошибка сети");
                    show($("perenualPreviewError"));
                    btn.disabled = false;
                }
            });

            actions.appendChild(btn);
        }

        info.appendChild(actions);

        wrap.appendChild(info);
        card.appendChild(wrap);
    }

    // ====== ВАЖНО: защита от спама запросами ======
    let debounceTimer = null;
    let lastScheduled = null;
    let currentController = null;

    function schedulePreview(scientificName) {
        const name = (scientificName ?? "").trim();
        if (!name) return;

        lastScheduled = name;

        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            // если за время debounce уже выбрали другой вариант — грузим только последний
            loadPreview(lastScheduled);
        }, 350);
    }

    async function loadPreview(scientificName) {
        const wrap = $("perenualPreviewWrap");
        const status = $("perenualPreviewStatus");
        const err = $("perenualPreviewError");

        // отменяем прошлый запрос (если он ещё идёт)
        if (currentController) currentController.abort();
        currentController = new AbortController();

        show(wrap);
        hide(err);
        setText(err, "");
        setText(status, "Ищу вид в Perenual...");
        clear($("perenualPreviewCard"));

        try {
            const url = `/api/perenual/preview?scientificName=${encodeURIComponent(scientificName)}`;

            const resp = await doFetch(url, {
                method: "GET",
                headers: {
                    "Accept": "application/json",
                    "X-Requested-With": "XMLHttpRequest",
                },
                signal: currentController.signal,
            });

            const json = await resp.json().catch(() => null);

            // если запрос отменён — тихо выходим (не показываем “ошибка сети”)
            if (currentController.signal.aborted) return;

            if (!resp.ok) {
                const msg = json && (json.message || json.code)
                    ? `${json.code ?? "ERROR"}: ${json.message ?? ""}`
                    : "Ошибка запроса";
                setText(status, "");
                setText(err, msg);
                show(err);
                return;
            }

            setText(status, "");
            buildPreviewCard(json);

        } catch (e) {
            if (e && e.name === "AbortError") return; // нормальная отмена
            setText(status, "");
            setText(err, "Ошибка сети");
            show(err);
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        const radios = document.querySelectorAll("input[type='radio'][name$='selectedScientificName']");
        if (!radios || radios.length === 0) return;

        radios.forEach((r) => {
            r.addEventListener("change", () => {
                if (r.checked) schedulePreview(r.value);
            });
        });

        const preselected = Array.from(radios).find((r) => r.checked);
        if (preselected) schedulePreview(preselected.value);
    });
})();