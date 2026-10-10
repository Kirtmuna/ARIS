import { state } from '../state.js';
import { fetchState } from '../api.js';

let listEl = null;

export function initRouteList(el) {
    listEl = el;
}

export function renderRouteList() {
    if (!listEl) return;
    listEl.innerHTML = '';

    const routes = state.routesData || [];
    if (routes.length === 0) {
        const empty = document.createElement('li');
        empty.className = 'empty';
        empty.textContent = '(進路なし)';
        listEl.appendChild(empty);
        return;
    }

    for (const routeId of routes) {
        const st = state.opRouteStates[routeId] || 'IDLE';

        const li = document.createElement('li');

        const name = document.createElement('span');
        name.className = 'routeName';
        name.textContent = routeId;
        li.appendChild(name);

        const btn = document.createElement('button');
        if (st === 'IDLE') {
            btn.className = 'idle';
            btn.textContent = '予約';
            btn.addEventListener('click', () => sendAction(routeId, 'request'));
        } else {
            btn.className = (st === 'OCCUPIED') ? 'occupied' : 'set';
            btn.textContent = '解放';
            btn.addEventListener('click', () => sendAction(routeId, 'release'));
        }
        li.appendChild(btn);

        listEl.appendChild(li);
    }
}

async function sendAction(routeId, action) {
    try {
        const r = await fetch('/api/route/' + action, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ routeId })
        });
        const res = await r.json();
        if (!res.ok) {
            alert((action === 'request' ? '予約' : '解放') + '失敗: ' + (res.error || ''));
        }
    } catch (e) {
        alert('通信エラー: ' + e);
    }
    await fetchState();
}
