(() => {
  const TOKEN_KEY = "aprax_admin_token";
  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => [...document.querySelectorAll(sel)];

  let token = localStorage.getItem(TOKEN_KEY) || "";
  let currentTab = "stats";

  function toast(msg, isErr = false) {
    const el = $("#toast");
    el.textContent = msg;
    el.classList.toggle("err", isErr);
    el.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { el.hidden = true; }, 3500);
  }

  async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetch(path, { ...options, headers });
    if (res.status === 401) {
      logout(false);
      throw new Error("Session expired — sign in again");
    }
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = { message: text }; }
    if (!res.ok) {
      const msg = data?.message || data?.error || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return data;
  }

  function showLogin() {
    $("#login-view").hidden = false;
    $("#app-view").hidden = true;
  }

  function showApp() {
    $("#login-view").hidden = true;
    $("#app-view").hidden = false;
    loadTab(currentTab);
  }

  function logout(callServer = true) {
    if (callServer && token) {
      api("/api/v1/auth/logout", { method: "POST" }).catch(() => {});
    }
    token = "";
    localStorage.removeItem(TOKEN_KEY);
    showLogin();
  }

  function setTab(name) {
    currentTab = name;
    $$(".tab").forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
    $$(".panel").forEach((p) => p.classList.toggle("active", p.id === `panel-${name}`));
    loadTab(name);
  }

  async function loadTab(name) {
    try {
      if (name === "stats") await loadStats();
      else if (name === "providers") await loadProviders();
      else if (name === "usage") await loadUsage();
      else if (name === "users") await loadUsers();
      else if (name === "audit") await loadAudit();
    } catch (e) {
      toast(e.message, true);
    }
  }

  function statCard(label, value, cls = "") {
    return `<div class="stat"><div class="label">${label}</div><div class="value ${cls}">${value}</div></div>`;
  }

  async function loadStats() {
    const s = await api("/api/v1/admin/stats");
    $("#stats-grid").innerHTML = [
      statCard("Backend", s.backendStatus, s.databaseOk ? "ok" : "err"),
      statCard("Database", s.databaseOk ? "up" : "down", s.databaseOk ? "ok" : "err"),
      statCard("Accounts", s.totalAccounts),
      statCard("Guest sessions", s.activeGuestSessions),
      statCard("Mailboxes", s.totalMailboxes),
      statCard("Requests 24h", s.requestsLast24h),
      statCard("Failures 24h", s.failuresLast24h, s.failuresLast24h > 0 ? "err" : "ok"),
      statCard("Rate limits 24h", s.rateLimitEventsLast24h),
    ].join("");

    const byP = Object.entries(s.mailboxesByProvider || {})
      .map(([k, v]) => `${k}: ${v}`).join(" · ") || "—";
    $("#stats-grid").insertAdjacentHTML(
      "beforeend",
      statCard("Mailboxes by provider", byP),
    );

    const tbody = $("#errors-table tbody");
    tbody.innerHTML = (s.recentErrors || []).map((e) => `
      <tr>
        <td>${esc(e.provider)}</td>
        <td>${esc(e.action)}</td>
        <td>${esc(e.errorCode || "—")}</td>
        <td>${e.statusCode ?? "—"}</td>
        <td>${esc(e.at)}</td>
      </tr>`).join("") || `<tr><td colspan="5" class="muted">No recent errors</td></tr>`;
  }

  async function loadProviders() {
    const data = await api("/api/v1/admin/providers");
    const list = data.providers || [];
    $("#providers-list").innerHTML = list.map((p) => {
      const reach = p.reachable == null ? "unknown" : p.reachable ? "reachable" : "unreachable";
      const q = p.quota
        ? `plan=${p.quota.plan || "?"} · mailboxes left=${p.quota.mailboxesRemaining ?? "—"}`
        : "quota unavailable";
      return `
      <article class="provider">
        <header>
          <strong>${esc(p.displayName)}</strong>
          <span class="badge ${p.configured ? "on" : "off"}">${p.configured ? "configured" : "not configured"}</span>
        </header>
        <div class="meta">
          id: ${esc(p.id)} · enabled: ${p.enabled ? "yes" : "no"} · key: ${p.keyConfigured ? esc(p.keyPreview || "set") : "missing"}<br/>
          health: ${reach}${p.healthMessage ? ` — ${esc(p.healthMessage)}` : ""}<br/>
          ${esc(q)}
        </div>
        <div class="actions">
          <button class="small" data-action="test" data-id="${esc(p.id)}">Test</button>
          <button class="small ghost" data-action="enable" data-id="${esc(p.id)}">Enable*</button>
          <button class="small ghost" data-action="disable" data-id="${esc(p.id)}">Disable*</button>
          ${p.documentationUrl ? `<a class="badge" href="${esc(p.documentationUrl)}" target="_blank" rel="noopener">Docs</a>` : ""}
        </div>
      </article>`;
    }).join("") || `<p class="muted">No providers registered.</p>`;

    $$("#providers-list button[data-action]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        btn.disabled = true;
        try {
          const res = await api(`/api/v1/admin/providers/${btn.dataset.id}/action`, {
            method: "POST",
            body: JSON.stringify({ action: btn.dataset.action }),
          });
          if (res.health) {
            toast(
              `${btn.dataset.id}: ${res.health.reachable ? "OK" : "FAIL"} — ${res.health.message || ""}`,
              !res.health.reachable,
            );
          } else if (res.note) {
            toast(res.note);
          } else {
            toast("Requested");
          }
          await loadProviders();
        } catch (e) {
          toast(e.message, true);
        } finally {
          btn.disabled = false;
        }
      });
    });
  }

  async function loadUsage() {
    const u = await api("/api/v1/admin/usage");
    $("#usage-summary").innerHTML = [
      statCard("Requests 30d", u.totalRequests),
      statCard("Failures 30d", u.totalFailures, u.totalFailures > 0 ? "err" : "ok"),
      statCard("Rate-limit events", u.rateLimitEvents),
      statCard(
        "By provider",
        Object.entries(u.byProvider || {}).map(([k, v]) => `${k}: ${v}`).join(" · ") || "—",
      ),
    ].join("");

    $("#usage-table tbody").innerHTML = (u.days || []).map((d) => `
      <tr>
        <td>${esc(d.date)}</td>
        <td>${d.total}</td>
        <td>${d.failed}</td>
        <td>${esc(Object.entries(d.byProvider || {}).map(([k, v]) => `${k}:${v}`).join(", ") || "—")}</td>
      </tr>`).join("") || `<tr><td colspan="4" class="muted">No usage yet</td></tr>`;
  }

  async function loadUsers() {
    const data = await api("/api/v1/admin/users?limit=100");
    $("#users-table tbody").innerHTML = (data.users || []).map((u) => `
      <tr>
        <td>${esc(u.email || u.id)}</td>
        <td>${esc(u.kind)}</td>
        <td>${esc(u.status)}</td>
        <td>${esc(u.createdAt)}</td>
        <td>
          ${u.status === "active"
            ? `<button class="small danger" data-suspend="${esc(u.id)}">Suspend</button>`
            : `<button class="small ok" data-activate="${esc(u.id)}">Activate</button>`}
        </td>
      </tr>`).join("") || `<tr><td colspan="5" class="muted">No users</td></tr>`;

    $$("#users-table [data-suspend]").forEach((b) =>
      b.addEventListener("click", () => setUserStatus(b.dataset.suspend, "suspended")));
    $$("#users-table [data-activate]").forEach((b) =>
      b.addEventListener("click", () => setUserStatus(b.dataset.activate, "active")));
  }

  async function setUserStatus(id, status) {
    try {
      await api(`/api/v1/admin/users/${encodeURIComponent(id)}/status`, {
        method: "POST",
        body: JSON.stringify({ status }),
      });
      toast(`User ${status}`);
      await loadUsers();
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function loadAudit() {
    const data = await api("/api/v1/admin/audit");
    $("#audit-table tbody").innerHTML = (data.entries || []).map((a) => `
      <tr>
        <td>${esc(a.at)}</td>
        <td>${esc(a.adminId || "—")}</td>
        <td>${esc(a.action)}</td>
        <td>${esc(a.target || "—")}</td>
        <td>${esc(a.details || "—")}</td>
      </tr>`).join("") || `<tr><td colspan="5" class="muted">No audit entries</td></tr>`;
  }

  function esc(s) {
    return String(s ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  $("#login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = $("#login-btn");
    const errEl = $("#login-error");
    errEl.hidden = true;
    btn.disabled = true;
    try {
      const data = await api("/api/v1/admin/login", {
        method: "POST",
        body: JSON.stringify({
          email: $("#login-email").value.trim(),
          password: $("#login-password").value,
        }),
      });
      if (data.role !== "ADMIN") throw new Error("Not an admin account");
      token = data.token;
      localStorage.setItem(TOKEN_KEY, token);
      showApp();
    } catch (err) {
      errEl.textContent = err.message;
      errEl.hidden = false;
    } finally {
      btn.disabled = false;
    }
  });

  $("#logout-btn").addEventListener("click", () => logout(true));
  $$(".tab").forEach((b) => b.addEventListener("click", () => setTab(b.dataset.tab)));

  if (token) showApp();
  else showLogin();
})();
