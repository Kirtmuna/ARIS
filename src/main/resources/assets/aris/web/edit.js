import { state } from './state.js';
import { renderPanelTop, initSectionEdit, initRouteEdit } from './panel.js';
import { renderList } from './tree.js';
import { draw } from './map.js';
import { fetchMap } from './api.js';

export function selectItem(listKey, id) {
    state.selected = { list: listKey, id };
    if (listKey === 'sections') {
        initSectionEdit(id);
        state.pendingRouteEdit = null;
    } else if (listKey === 'routes') {
        initRouteEdit(id);
        state.pendingSectionEdit = null;
    } else {
        state.pendingSectionEdit = null;
        state.pendingRouteEdit = null;
    }
    renderList(listKey);
    renderPanelTop();
    draw();
}

export function selectFolder(listKey, id) {
    state.selected = { list: listKey, id };
    renderList(listKey);
    renderPanelTop();
}

export function selectSignal(key) {
    state.selected = { list: 'signals', id: key };
    state.pendingSectionEdit = null;
    state.pendingRouteEdit = null;
    renderPanelTop();
    draw();
}

export function selectPoint(switchId, pointKey) {
    state.selected = { list: 'switches', id: switchId + '.' + pointKey };
    state.pendingSectionEdit = null;
    state.pendingRouteEdit = null;

    let nSig = '', rSig = '';
    const lc = state.lineConfig;
    if (lc && lc.switches && lc.switches[switchId]
    && lc.switches[switchId].points
    && lc.switches[switchId].points[pointKey]) {
        const p = lc.switches[switchId].points[pointKey];
        nSig = p.nSignal || '';
        rSig = p.rSignal || '';
    }
    state.pendingPointEdit = { switchId, pointKey, nSignal: nSig, rSignal: rSig };
    state.pickMode = null;
    renderPanelTop();
    draw();
}

export function onPickedSignal(signalKey) {
    const pm = state.pickMode;
    if (!pm) return;
    if (pm.purpose === 'nSignal' && state.pendingPointEdit) {
        state.pendingPointEdit.nSignal = signalKey;
    } else if (pm.purpose === 'rSignal' && state.pendingPointEdit) {
        state.pendingPointEdit.rSignal = signalKey;
    } else if (pm.purpose === 'startSignal' && state.pendingSectionEdit) {
        state.pendingSectionEdit.startSignal = signalKey;
    } else if (pm.purpose === 'endSignal' && state.pendingSectionEdit) {
        state.pendingSectionEdit.endSignal = signalKey;
    }
    state.pickMode = null;
    renderPanelTop();
    draw();
}

export function onPickedPoint(switchId, pointKey) {
    const pm = state.pickMode;
    if (!pm) return;
    const key = switchId + '.' + pointKey;

    if (pm.target === 'signalOrPoint' && state.pendingSectionEdit) {
        if (pm.purpose === 'endSignal') state.pendingSectionEdit.endSignal = key;
        else if (pm.purpose === 'startSignal') state.pendingSectionEdit.startSignal = key;
        state.pickMode = null;
        renderPanelTop();
        draw();
        return;
    }
    if (pm.target === 'pointOnly' && state.pendingRouteEdit) {
        if (pm.purpose === 'nPoint') {
            if (!state.pendingRouteEdit.nPoint.includes(key)) state.pendingRouteEdit.nPoint.push(key);
        } else if (pm.purpose === 'rPoint') {
            if (!state.pendingRouteEdit.rPoint.includes(key)) state.pendingRouteEdit.rPoint.push(key);
        }
        renderPanelTop();
        draw();
    }
}

export function onPickedRail(railKey) {
    if (!state.pendingSectionEdit) return;
    const parts = railKey.split(',');
    if (parts.length !== 3) return;
    const x = parseInt(parts[0], 10);
    const y = parseInt(parts[1], 10);
    const z = parseInt(parts[2], 10);
    if (isNaN(x) || isNaN(y) || isNaN(z)) return;

    const rails = state.pendingSectionEdit.rails;
    let idx = -1;
    for (let i = 0; i < rails.length; i++) {
        if (rails[i][0] === x && rails[i][1] === y && rails[i][2] === z) { idx = i; break; }
    }
    if (idx >= 0) rails.splice(idx, 1);
    else rails.push([x, y, z]);
    renderPanelTop();
    draw();
}

export async function saveSection() {
    if (!state.pendingSectionEdit) return;
    const { sectionId, startSignal, endSignal, rails } = state.pendingSectionEdit;

    const idInput = document.getElementById('sectionIdInput');
    const newId = idInput ? idInput.value.trim() : sectionId;
    let currentId = sectionId;

    if (newId && newId !== sectionId) {
        try {
            const rr = await fetch('/api/section/rename', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ oldId: sectionId, newId })
            });
            const rres = await rr.json();
            if (!rres.ok) { alert('名前変更失敗: ' + (rres.error || '')); return; }
            currentId = newId;
        } catch (e) { alert('通信エラー: ' + e); return; }
    }

    try {
        const r = await fetch('/api/section/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                sectionId: currentId,
                startSignal: startSignal || null,
                endSignal: endSignal || null,
                rails: rails
            })
        });
        const res = await r.json();
        if (!res.ok) { alert('保存失敗: ' + (res.error || '')); return; }
        if (currentId !== sectionId) {
            state.selected.id = currentId;
            state.pendingSectionEdit.sectionId = currentId;
        }
        state.currentVersion = -1;
        fetchMap();
    } catch (e) { alert('通信エラー: ' + e); }
}

export async function deleteSection(sectionId) {
    try {
        const r = await fetch('/api/section/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sectionId })
        });
        const res = await r.json();
        if (!res.ok) { alert('削除失敗: ' + (res.error || '')); return; }
        state.selected = { list: null, id: null };
        state.pendingSectionEdit = null;
        state.currentVersion = -1;
        await fetchMap();
        renderPanelTop();
        draw();
    } catch (e) { alert('通信エラー: ' + e); }
}

export async function addSection() {
    const name = prompt('区間ID');
    if (name == null) return;
    const sectionId = name.trim();
    if (!sectionId) return;
    try {
        const r = await fetch('/api/section/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sectionId })
        });
        const res = await r.json();
        if (!res.ok) { alert('作成失敗: ' + (res.error || '')); return; }
        state.currentVersion = -1;
        await fetchMap();
        selectItem('sections', sectionId);
    } catch (e) { alert('通信エラー: ' + e); }
}

export async function saveRoute() {
    if (!state.pendingRouteEdit) return;
    const { routeId, sections, nPoint, rPoint } = state.pendingRouteEdit;

    const idInput = document.getElementById('routeIdInput');
    const newId = idInput ? idInput.value.trim() : routeId;
    let currentId = routeId;

    if (newId && newId !== routeId) {
        try {
            const rr = await fetch('/api/route/rename', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ oldId: routeId, newId })
            });
            const rres = await rr.json();
            if (!rres.ok) { alert('名前変更失敗: ' + (rres.error || '')); return; }
            currentId = newId;
        } catch (e) { alert('通信エラー: ' + e); return; }
    }

    try {
        const r = await fetch('/api/route/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ routeId: currentId, sections, nPoint, rPoint })
        });
        const res = await r.json();
        if (!res.ok) { alert('保存失敗: ' + (res.error || '')); return; }
        if (currentId !== routeId) {
            state.selected.id = currentId;
            state.pendingRouteEdit.routeId = currentId;
        }
        state.currentVersion = -1;
        fetchMap();
    } catch (e) { alert('通信エラー: ' + e); }
}

export async function deleteRoute(routeId) {
    try {
        const r = await fetch('/api/route/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ routeId })
        });
        const res = await r.json();
        if (!res.ok) { alert('削除失敗: ' + (res.error || '')); return; }
        state.selected = { list: null, id: null };
        state.pendingRouteEdit = null;
        state.currentVersion = -1;
        await fetchMap();
        renderPanelTop();
        draw();
    } catch (e) { alert('通信エラー: ' + e); }
}

export async function addRoute() {
    const name = prompt('進路ID');
    if (name == null) return;
    const routeId = name.trim();
    if (!routeId) return;
    try {
        const r = await fetch('/api/route/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ routeId })
        });
        const res = await r.json();
        if (!res.ok) { alert('作成失敗: ' + (res.error || '')); return; }
        state.currentVersion = -1;
        await fetchMap();
        selectItem('routes', routeId);
    } catch (e) { alert('通信エラー: ' + e); }
}

export async function saveSignal(key, type) {
    try {
        const r = await fetch('/api/signal/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key, type })
        });
        const res = await r.json();
        if (!res.ok) { alert('保存失敗: ' + (res.error || '')); return; }
        state.currentVersion = -1;
        fetchMap();
    } catch (e) { alert('通信エラー: ' + e); }
}

export async function saveSwitchPoint() {
    if (!state.pendingPointEdit) return;
    const { switchId, pointKey, nSignal, rSignal } = state.pendingPointEdit;
    try {
        const r = await fetch('/api/switch/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                switchId, pointKey,
                nSignal: nSignal || null,
                rSignal: rSignal || null
            })
        });
        const res = await r.json();
        if (!res.ok) { alert('保存失敗: ' + (res.error || '')); return; }
        state.currentVersion = -1;
        fetchMap();
    } catch (e) { alert('通信エラー: ' + e); }
}
