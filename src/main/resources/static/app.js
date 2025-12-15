// 같은 오리진이면 빈 문자열 유지, 다르면 http://localhost:8080 처럼 지정
const API_BASE = "";

// 요소 캐시
const q = (s) => document.querySelector(s);
const viewCatalog   = q("#viewCatalog");
const viewCreate    = q("#viewCreate");
const viewInstances = q("#viewInstances");
const alertBox      = q("#alert");

// 상태
let currentTemplate = null; // "WEB" | "DB"

document.addEventListener("DOMContentLoaded", () => {
    // 내비게이션
    q("#btnHome").addEventListener("click", showCatalog);
    q("#cardWeb").addEventListener("click", () => openCreate("WEB"));
    q("#cardDb").addEventListener("click", () => openCreate("DB"));
    q("#cardInstances").addEventListener("click", showInstances);

    q("#btnBackFromCreate").addEventListener("click", showCatalog);
    q("#btnCancelCreate").addEventListener("click", showCatalog);

    q("#btnBackFromInstances").addEventListener("click", showCatalog);
    q("#btnReload").addEventListener("click", loadInstances);

    q("#btnCreate").addEventListener("click", submitCreate);

    // 시작 화면
    showCatalog();
});

// 뷰 전환
function hideAll() {
    viewCatalog.classList.add("is-hidden");
    viewCreate.classList.add("is-hidden");
    viewInstances.classList.add("is-hidden");
    alertMsg("");
}
function showCatalog() {
    hideAll();
    viewCatalog.classList.remove("is-hidden");
}
function openCreate(template) {
    currentTemplate = template;
    hideAll();
    q("#createTitle").textContent = template === "WEB" ? "웹 VM 생성" : "DB VM 생성";
    q("#inputName").value = "";
    viewCreate.classList.remove("is-hidden");
}
function showInstances() {
    hideAll();
    viewInstances.classList.remove("is-hidden");
    loadInstances();
}

// 알림
function alertMsg(msg, isError = false) {
    if (!msg) {
        alertBox.classList.add("is-hidden");
        alertBox.classList.remove("error");
        alertBox.textContent = "";
        return;
    }
    alertBox.textContent = msg;
    alertBox.classList.remove("is-hidden");
    if (isError) alertBox.classList.add("error");
    else alertBox.classList.remove("error");
}

// 생성
async function submitCreate() {
    const name = q("#inputName").value.trim();
    if (!name) {
        alertMsg("이름을 입력해 주세요.", true);
        return;
    }
    if (!currentTemplate) {
        alertMsg("템플릿이 선택되지 않았습니다.", true);
        return;
    }

    const btn = q("#btnCreate");
    const spinner = q("#spinnerCreate");
    btn.disabled = true; spinner.classList.remove("is-hidden"); alertMsg("");

    try {
        const resp = await fetch(`${API_BASE}/api/orders`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ template: currentTemplate, name })
        });
        if (!resp.ok) throw new Error(await resp.text());
        const data = await resp.json();

        alertMsg(`생성 완료: ${data.serverName || name} (${data.serverId || "ID 확인 중"})`);
        // 생성 후 목록으로 이동
        showInstances();
    } catch (e) {
        alertMsg(`생성 실패: ${e.message || e}`, true);
    } finally {
        btn.disabled = false; spinner.classList.add("is-hidden");
    }
}

// 목록 로드
async function loadInstances() {
    const grid = q("#instancesGrid");
    const empty = q("#instancesEmpty");
    const loading = q("#instancesLoading");
    const error = q("#instancesError");

    grid.innerHTML = "";
    empty.classList.add("is-hidden");
    error.classList.add("is-hidden");
    loading.classList.remove("is-hidden");

    try {
        const resp = await fetch(`${API_BASE}/api/instances`);
        if (!resp.ok) throw new Error(await resp.text());
        const items = await resp.json();

        renderInstances(items);
        if (!items || items.length === 0) empty.classList.remove("is-hidden");
    } catch (e) {
        error.textContent = `목록 로드 실패: ${e.message || e}`;
        error.classList.remove("is-hidden");
    } finally {
        loading.classList.add("is-hidden");
    }
}

function renderInstances(items) {
    const grid = q("#instancesGrid");
    grid.innerHTML = "";
    (items || []).forEach((it) => {
        const ips = (it.addresses || []).map(a => a.ip).join(", ") || "-";
        const st = (it.status || "").toUpperCase();
        let sClass = "s-unknown";
        let sText  = st;
        if (st === "ACTIVE") { sClass = "s-active"; sText = "가동(ACTIVE)"; }
        else if (st === "BUILD") { sClass = "s-build"; sText = "생성중(BUILD)"; }
        else if (st === "ERROR") { sClass = "s-error"; sText = "오류(ERROR)"; }
        else if (!st) { sText = "미확인"; }

        const el = document.createElement("div");
        el.className = "card instance";
        el.innerHTML = `
      <div class="card-inner">
        <div class="card-head" style="justify-content:space-between">
          <div class="card-title">${escapeHtml(it.name || "-")}</div>
          <span class="status ${sClass}">${escapeHtml(sText)}</span>
        </div>
        <div class="kv"><strong>ID:</strong> ${escapeHtml(it.id || "-")}</div>
        <div class="kv"><strong>IP:</strong> ${escapeHtml(ips)}</div>
        ${it.consoleUrl ? `<div class="kv"><a href="${it.consoleUrl}" target="_blank">콘솔 열기</a></div>` : ""}
        <div class="cta indigo" style="margin-top:auto;"></div>
      </div>
    `;
        grid.appendChild(el);
    });
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (m) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
}
