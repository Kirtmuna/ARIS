import { state, STORAGE_KEY } from '../state.js';
import { selectItem, selectFolder } from './edit.js';
import { renderPanelTop } from './panel.js';

let dragSrc = null;
let folderCounter = 0;

export function newFolderId() {
    return 'f_' + Date.now() + '_' + (folderCounter++);
}

export function loadTreeState() {
    try {
        const s = localStorage.getItem(STORAGE_KEY);
        if (!s) return;
        const p = JSON.parse(s);
        if (p.sections) state.treeState.sections = p.sections;
        if (p.routes)   state.treeState.routes   = p.routes;
        for (const k of ['sections', 'routes']) {
            const t = state.treeState[k];
            if (!t.folders) t.folders = {};
            if (!t.assignments) t.assignments = {};
            if (!t.order) t.order = [];
        }
    } catch (e) { console.warn('tree load failed', e); }
}

export function saveTreeState() {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state.treeState));
    } catch (e) { console.warn('tree save failed', e); }
}

export function syncItemsFromServer() {
    syncList('sections', state.sectionsData);
    syncList('routes',   state.routesData);
    saveTreeState();
}

function syncList(listKey, serverItems) {
    const t = state.treeState[listKey];
    const set = new Set(serverItems);

    for (const id of Object.keys(t.assignments)) {
        if (!set.has(id)) {
            delete t.assignments[id];
            t.order = t.order.filter(x => x !== id);
        }
    }
    for (const id of serverItems) {
        if (!t.order.includes(id)) {
            t.order.push(id);
            t.assignments[id] = null;
        }
    }
}

export function renderList(listKey) {
    const t = state.treeState[listKey];
    const ulId = listKey === 'sections' ? 'sectionList' : 'routeList';
    const ul = document.getElementById(ulId);
    ul.innerHTML = '';

    const flat = [];
    function walk(parentId, depth) {
        for (const id of t.order) {
            const isFolder = !!t.folders[id];
            const itemParent = isFolder
            ? (t.folders[id].parent || null)
            : (t.assignments[id] || null);
            if (itemParent !== parentId) continue;

            if (isFolder) {
                const expanded = t.folders[id].expanded !== false;
                flat.push({ type: 'folder', id, depth, expanded });
                if (expanded) walk(id, depth + 1);
            } else {
                flat.push({ type: 'item', id, depth });
            }
        }
    }
    walk(null, 0);

    for (const row of flat) {
        const li = document.createElement('li');
        li.dataset.type = row.type;
        li.dataset.id = row.id;
        li.dataset.list = listKey;
        li.style.paddingLeft = (8 + row.depth * 16) + 'px';
        li.draggable = true;

        if (row.type === 'folder') {
            li.classList.add('folder');
            if (state.selected.list === listKey && state.selected.id === row.id) {
                li.classList.add('selected');
            }
            const arrow = document.createElement('span');
            arrow.className = 'arrow';
            arrow.textContent = row.expanded ? '▼' : '▶';
            arrow.addEventListener('click', (e) => {
                e.stopPropagation();
                t.folders[row.id].expanded = !row.expanded;
                saveTreeState();
                renderList(listKey);
            });
            li.appendChild(arrow);

            const nameSpan = document.createElement('span');
            nameSpan.textContent = ' ' + (t.folders[row.id].name || row.id);
            li.appendChild(nameSpan);

            li.addEventListener('click', () => selectFolder(listKey, row.id));

            li.addEventListener('dblclick', (e) => {
                e.stopPropagation();
                const cur = t.folders[row.id].name || row.id;
                const v = prompt('フォルダ名', cur);
                if (v == null) return;
                const trimmed = v.trim();
                if (!trimmed) return;
                t.folders[row.id].name = trimmed;
                saveTreeState();
                renderList(listKey);
                if (state.selected.list === listKey && state.selected.id === row.id) renderPanelTop();
            });
        } else {
            li.classList.add('item');
            if (state.selected.list === listKey && state.selected.id === row.id) {
                li.classList.add('selected');
            }
            li.textContent = row.id;
            li.addEventListener('click', () => selectItem(listKey, row.id));
        }

        attachDragHandlers(li, listKey);
        ul.appendChild(li);
    }
}

function attachDragHandlers(li, listKey) {
    li.addEventListener('dragstart', e => {
        dragSrc = { list: listKey, type: li.dataset.type, id: li.dataset.id };
        li.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', li.dataset.id);
    });
    li.addEventListener('dragend', () => {
        li.classList.remove('dragging');
        document.querySelectorAll('.drag-over-before, .drag-over-into, .drag-over-after').forEach(el => {
            el.classList.remove('drag-over-before', 'drag-over-into', 'drag-over-after');
        });
    });
    li.addEventListener('dragover', e => {
        if (!dragSrc || dragSrc.list !== listKey) return;
        if (dragSrc.id === li.dataset.id) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';

        const rect = li.getBoundingClientRect();
        const rel = (e.clientY - rect.top) / rect.height;
        const isFolder = li.dataset.type === 'folder';

        li.classList.remove('drag-over-before', 'drag-over-into', 'drag-over-after');

        if (isFolder && rel > 0.25 && rel < 0.75) {
            li.classList.add('drag-over-into');
        } else if (rel <= 0.5) {
            li.classList.add('drag-over-before');
        } else {
            li.classList.add('drag-over-after');
        }
    });
    li.addEventListener('dragleave', () => {
        li.classList.remove('drag-over-before', 'drag-over-into', 'drag-over-after');
    });
    li.addEventListener('drop', e => {
        e.preventDefault();
        li.classList.remove('drag-over-before', 'drag-over-into', 'drag-over-after');
        if (!dragSrc || dragSrc.list !== listKey) return;
        if (dragSrc.id === li.dataset.id) return;

        const t = state.treeState[listKey];
        const srcId = dragSrc.id;
        const tgtId = li.dataset.id;
        const tgtType = li.dataset.type;

        if (dragSrc.type === 'folder' && isAncestor(t, srcId, tgtId)) return;

        const rect = li.getBoundingClientRect();
        const rel = (e.clientY - rect.top) / rect.height;

        if (tgtType === 'folder' && rel > 0.25 && rel < 0.75) {
            moveIntoFolder(t, srcId, tgtId);
        } else {
            const tgtParent = tgtType === 'folder'
            ? (t.folders[tgtId].parent || null)
            : (t.assignments[tgtId] || null);

            if (rel <= 0.25) {
                moveAsSiblingBefore(t, srcId, tgtId, tgtParent);
            } else {
                moveAsSiblingAfter(t, srcId, tgtId, tgtParent);
            }
        }
        saveTreeState();
        renderList(listKey);
    });
}

function isAncestor(t, ancestorId, nodeId) {
    let cur = t.assignments[nodeId] ?? (t.folders[nodeId] ? t.folders[nodeId].parent : null);
    while (cur) {
        if (cur === ancestorId) return true;
        cur = t.folders[cur] ? (t.folders[cur].parent || null) : null;
    }
    return false;
}

function moveIntoFolder(t, srcId, folderId) {
    t.order = t.order.filter(x => x !== srcId);
    if (t.folders[srcId]) t.folders[srcId].parent = folderId;
    else t.assignments[srcId] = folderId;
    const idx = t.order.indexOf(folderId);
    if (idx >= 0) t.order.splice(idx + 1, 0, srcId);
    else t.order.push(srcId);
}

function moveAsSiblingBefore(t, srcId, tgtId, parentId) {
    t.order = t.order.filter(x => x !== srcId);
    if (t.folders[srcId]) t.folders[srcId].parent = parentId;
    else t.assignments[srcId] = parentId;
    const idx = t.order.indexOf(tgtId);
    if (idx >= 0) t.order.splice(idx, 0, srcId);
    else t.order.push(srcId);
}

function moveAsSiblingAfter(t, srcId, tgtId, parentId) {
    t.order = t.order.filter(x => x !== srcId);
    if (t.folders[srcId]) t.folders[srcId].parent = parentId;
    else t.assignments[srcId] = parentId;
    const idx = t.order.indexOf(tgtId);
    if (idx >= 0) t.order.splice(idx + 1, 0, srcId);
    else t.order.push(srcId);
}

export function deleteFolder(listKey, folderId) {
    const t = state.treeState[listKey];
    const parentId = t.folders[folderId] ? (t.folders[folderId].parent || null) : null;
    for (const id of t.order) {
        if (t.folders[id] && t.folders[id].parent === folderId) t.folders[id].parent = parentId;
        if (t.assignments[id] === folderId) t.assignments[id] = parentId;
    }
    delete t.folders[folderId];
    t.order = t.order.filter(x => x !== folderId);
    if (state.selected.id === folderId) {
        state.selected = { list: null, id: null };
        renderPanelTop();
    }
    saveTreeState();
    renderList(listKey);
}
