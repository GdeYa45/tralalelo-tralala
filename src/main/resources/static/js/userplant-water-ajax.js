// Этап 6.2 (P0): AJAX-кнопка "Полил" на карточке UserPlant.
// DoD: нажатие добавляет CareEvent и возвращает JSON (обновляет "следующий полив" и список задач).

(() => {
    "use strict";

    function $(id) {
        return document.getElementById(id);
    }

    function setText(el, text) {
        if (!el) return;
        el.textContent = text ?? "";
    }

    function show(el) {
        if (!el) return;
        el.style.display = "block";
    }

    function hide(el) {
        if (!el) return;
        el.style.display = "none";
    }

    function clearChildren(el) {
        if (!el) return;
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function formatDateRu(isoDate) {
        if (!isoDate) return "—";
        // isoDate = "YYYY-MM-DD"
        const [y, m, d] = isoDate.split("-");
        if (!y || !m || !d) return isoDate;
        return `${d}.${m}.${y}`;
    }

    function renderTasks(tasks) {
        const block = $("tasksBlock");
        if (!block) return;

        let list = $("tasksList");
        if (!list) {
            list = document.createElement("div");
            list.id = "tasksList";
            list.style.marginTop = "8px";
            block.appendChild(list);
        }

        clearChildren(list);

        const arr = Array.isArray(tasks) ? tasks : [];
        if (arr.length === 0) {
            const empty = document.createElement("div");
            empty.className = "muted";
            empty.style.marginTop = "8px";
            empty.textContent = "Пока нет задач.";
            list.appendChild(empty);
            return;
        }

        arr.forEach((t) => {
            const row = document.createElement("div");
            row.className = "task-row";

            const wrap = document.createElement("div");
            wrap.style.display = "flex";
            wrap.style.justifyContent = "space-between";
            wrap.style.gap = "10px";
            wrap.style.flexWrap = "wrap";

            const left = document.createElement("div");
            left.style.fontWeight = "700";
            left.textContent = t.typeLabel || t.type || "Уход";

            const right = document.createElement("div");
            right.className = "muted";
            right.textContent = formatDateRu(t.dueDate);

            wrap.appendChild(left);
            wrap.appendChild(right);
            row.appendChild(wrap);

            list.appendChild(row);
        });
    }

    document.addEventListener("DOMContentLoaded", () => {
        const btn = $("btnWaterNow");
        const next = $("nextWateringText");
        const status = $("waterAjaxStatus");
        const error = $("waterAjaxError");

        if (!btn) return;

        btn.addEventListener("click", async () => {
            hide(error);
            setText(error, "");
            setText(status, "Добавляю полив...");

            btn.disabled = true;

            const plantId = btn.dataset.plantId;

            let resp;
            try {
                const doFetch = (window.csrfFetch && typeof window.csrfFetch === "function")
                    ? window.csrfFetch
                    : fetch;

                resp = await doFetch(`/api/plants/${encodeURIComponent(plantId)}/water`, {
                    method: "POST",
                    headers: {
                        "Accept": "application/json",
                        "Content-Type": "application/json",
                        "X-Requested-With": "XMLHttpRequest",
                    },
                    body: JSON.stringify({}),
                });
            } catch (e) {
                setText(status, "");
                setText(error, "Ошибка сети");
                show(error);
                btn.disabled = false;
                return;
            }

            let data = null;
            try {
                data = await resp.json();
            } catch {
                // ignore
            }

            if (!resp.ok) {
                setText(status, "");
                const msg = data && (data.message || data.code)
                    ? `${data.code ?? "ERROR"}: ${data.message ?? ""}`
                    : "Ошибка запроса";
                setText(error, msg);
                show(error);
                btn.disabled = false;
                return;
            }

            // success
            if (data && typeof data.nextWateringText === "string") {
                setText(next, data.nextWateringText);
            }
            renderTasks(data ? data.tasks : []);
            setText(status, "Готово: полив добавлен, план обновлён");
            btn.disabled = false;
        });
    });
})();