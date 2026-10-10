import { state } from './state.js';
import { resizeCanvas } from './viewport.js';
import { setupCamera } from './camera.js';
import { draw, pickSignal, pickPoint, pickRail } from './map.js';
import { loadTreeState, renderList, saveTreeState, newFolderId } from './tree.js';
import { renderPanelTop } from './panel.js';
import {
selectSignal, selectPoint, onPickedSignal, onPickedPoint, onPickedRail,
addSection, addRoute
} from './edit.js';
import { fetchMap, pollVersion } from './api.js';

state.canvas = document.getElementById('map');
state.ctx = state.canvas.getContext('2d');

// クリック選択
state.canvas.addEventListener('mousedown', e => {
    state.clickStart = { x: e.clientX, y: e.clientY };
});
state.canvas.addEventListener('mouseup', e => {
    if (!state.clickStart) return;
    const dx = e.clientX - state.clickStart.x;
    const dy = e.clientY - state.clickStart.y;
    state.clickStart = null;
    if (dx * dx + dy * dy > 25) return;

    const rect = state.canvas.getBoundingClientRect();
    const mx = e.clientX - rect.left;
    const my = e.clientY - rect.top;

    if (state.pickMode) {
        if (state.pickMode.target === 'rail') {
            const railKey = pickRail(mx, my);
            if (railKey) onPickedRail(railKey);
            else { state.pickMode = null; renderPanelTop(); }
            draw();
            return;
        }
        if (state.pickMode.target === 'pointOnly') {
            const hitP = pickPoint(mx, my);
            if (hitP) onPickedPoint(hitP.switchId, hitP.pointKey);
            else { state.pickMode = null; renderPanelTop(); }
            draw();
            return;
        }
        const hitS = pickSignal(mx, my);
        if (hitS) { onPickedSignal(hitS.key); draw(); return; }
        if (state.pickMode.target === 'signalOrPoint') {
            const hitP = pickPoint(mx, my);
            if (hitP) { onPickedPoint(hitP.switchId, hitP.pointKey); draw(); return; }
        }
        state.pickMode = null;
        renderPanelTop();
        draw();
        return;
    }

    const hitS = pickSignal(mx, my);
    if (hitS) { selectSignal(hitS.key); return; }
    const hitP = pickPoint(mx, my);
    if (hitP) { selectPoint(hitP.switchId, hitP.pointKey); return; }
    state.selected = { list: null, id: null };
    state.pendingPointEdit = null;
    renderPanelTop();
    draw();
});

// フォルダUI
document.querySelectorAll('.btnFolder').forEach(btn => {
    btn.addEventListener('click', () => {
        const listKey = btn.dataset.list;
        const box = document.getElementById('folderCreate-' + listKey);
        const visible = box.style.display !== 'none';
        box.style.display = visible ? 'none' : '';
        if (!visible) box.querySelector('input').focus();
    });
});

document.querySelectorAll('.btnFolderCreate').forEach(btn => {
    btn.addEventListener('click', () => {
        const listKey = btn.dataset.list;
        const box = document.getElementById('folderCreate-' + listKey);
        const input = box.querySelector('input');
        const name = input.value.trim();
        if (!name) return;

        const id = newFolderId();
        const t = state.treeState[listKey];
        t.folders[id] = { name, parent: null, expanded: true };
        t.order.push(id);
        saveTreeState();
        input.value = '';
        box.style.display = 'none';
        renderList(listKey);
    });
});

document.querySelectorAll('.folderCreate input').forEach(inp => {
    inp.addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            const listKey = inp.parentElement.id.replace('folderCreate-', '');
            document.querySelector(`.btnFolderCreate[data-list="${listKey}"]`).click();
        }
    });
});

document.querySelectorAll('.btnAdd').forEach(btn => {
    btn.addEventListener('click', () => {
        const listKey = btn.dataset.list;
        if (listKey === 'sections') addSection();
        else if (listKey === 'routes') addRoute();
    });
});

document.getElementById('btnRefresh').addEventListener('click', fetchMap);

// 初期化
loadTreeState();
renderPanelTop();
window.addEventListener('resize', () => { resizeCanvas(); draw(); });
resizeCanvas();
setupCamera();
fetchMap().then(() => pollVersion());
