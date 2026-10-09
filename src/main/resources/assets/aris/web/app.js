(() => {
    const canvas = document.getElementById('map');
    const ctx = canvas.getContext('2d');

    let camera = { x: 0, y: 0, scale: 1.5 };
    let rails = [];
    let signals = [];
    let switches = [];
    let points = [];
    let sectionsData = [];
    let routesData = [];
    let dragging = false;
    let lastMouse = { x: 0, y: 0 };
    let lineConfigsCache = {};
    let railKeyToGroup = {};  // railKey -> groupKey
    let pickMode = null;  // { purpose: 'nSignal'|'rSignal', switchId, pointKey }
    let pendingPointEdit = null;  // { switchId, pointKey, nSignal, rSignal, lineId }
    let pendingSectionEdit = null;  // { sectionId, lineId, startSignal, endSignal }

    function resize() {
        const dpr = window.devicePixelRatio || 1;
        canvas.width = canvas.clientWidth * dpr;
        canvas.height = canvas.clientHeight * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        draw();
    }

    function worldToScreen(x, z) {
        return [
            (x - camera.x) * camera.scale + canvas.clientWidth / 2,
            (z - camera.y) * camera.scale + canvas.clientHeight / 2
        ];
    }

    function aspectColor(aspect) {
        switch (aspect) {
            case 0: return '#ff3030'; // 停止: 赤
            case 1: return '#ffaa00'; // 警戒: 橙
            case 2: return '#ffdd00'; // 注意: 黄
            case 3: return '#aaff44'; // 減速: 黄緑
            case 4: return '#44dd44'; // 進行: 緑
            case 5: return '#2288ff'; // 高速進行: 青
            default: return '#666';   // 未設定
        }
    }

    function draw() {
        const w = canvas.clientWidth, h = canvas.clientHeight;
        ctx.fillStyle = '#000';
        ctx.fillRect(0, 0, w, h);

        // レール
        const switchRailKeys = new Set(
            switches.map(sw => sw.pos[0] + ',' + sw.pos[1] + ',' + sw.pos[2])
        );
        
        const selectedSectionRailKeys = new Set();
        if (selected.list === 'sections' && pendingSectionEdit) {
            const groupKeys = new Set();
            for (const sr of pendingSectionEdit.rails) {
                const k = sr[0] + ',' + sr[1] + ',' + sr[2];
                selectedSectionRailKeys.add(k);
                const gk = railKeyToGroup[k];
                if (gk) groupKeys.add(gk);
            }
            for (const r of rails) {
                const gk = railKeyToGroup[r.key];
                if (gk && groupKeys.has(gk)) {
                    selectedSectionRailKeys.add(r.key);
                }
            }
        }

        ctx.lineWidth = 2;
        ctx.lineCap = 'round';
        for (const r of rails) {
            const isSelected = selectedSectionRailKeys.has(r.key);
            const isSwitch = switchRailKeys.has(r.key);
            if (isSelected) {
                ctx.strokeStyle = '#ffdd00';
                ctx.lineWidth = 4;
            } else {
                ctx.strokeStyle = isSwitch ? '#ff8800' : '#fff';
                ctx.lineWidth = 2;
            }
            for (const poly of r.polylines) {
                ctx.beginPath();
                for (let i = 0; i < poly.length; i++) {
                    const p = poly[i];
                    const [sx, sy] = worldToScreen(p[0], p[2]);
                    if (i === 0) ctx.moveTo(sx, sy); else ctx.lineTo(sx, sy);
                }
                ctx.stroke();
            }
        }

        // 信号
        const sizeRatio = Math.min(1, camera.scale / 1.5);
        const signalRadius = 5 * sizeRatio;
        const signalStackGap = 10 * sizeRatio;
        const signalOutline = Math.max(1, 1 * sizeRatio);

        const groups = new Map();
        for (const s of signals) {
            const key = Math.round(s.pos[0]) + ',' + Math.round(s.pos[2]);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(s);
        }
        const selectedSignalKey = (selected.list === 'signals') ? selected.id : null;

        for (const list of groups.values()) {
            list.sort((a, b) => a.pos[1] - b.pos[1]);
            for (let i = 0; i < list.length; i++) {
                const s = list[i];
                const [sx, syBase] = worldToScreen(s.pos[0], s.pos[2]);
                const sy = syBase - i * signalStackGap;

                const skey = Math.floor(s.pos[0]) + ',' + Math.floor(s.pos[1]) + ',' + Math.floor(s.pos[2]);
                const isSelected = (skey === selectedSignalKey);
                
                ctx.fillStyle = aspectColor(s.aspect);
                ctx.beginPath();
                ctx.arc(sx, sy, signalRadius, 0, Math.PI * 2);
                ctx.fill();
                
                ctx.strokeStyle = 'rgba(0,0,0,0.6)';
                ctx.lineWidth = signalOutline;
                ctx.stroke();
                
                if (isSelected) {
                    ctx.beginPath();
                    ctx.arc(sx, sy, signalRadius + signalOutline + 3, 0, Math.PI * 2);
                    ctx.strokeStyle = '#44ccff';
                    ctx.lineWidth = 2;
                    ctx.stroke();
                }
            }
        }
        const selPointKey = (selected.list === 'switches' && pendingPointEdit)
        ? (pendingPointEdit.switchId + '.' + pendingPointEdit.pointKey) : null;

        computePointPositions();
        for (const item of computedPointPositions) {
            const pt = item.pt;
            const r = signalRadius * 1.0;
            const isSel = ((pt.switchId + '.' + pt.pointKey) === selPointKey);
            ctx.fillStyle = isSel ? '#ffaa00' : '#ff3030';
            ctx.fillRect(item.sx - r, item.sy - r, r * 2, r * 2);
            ctx.strokeStyle = 'rgba(0,0,0,0.6)';
            ctx.lineWidth = signalOutline;
            ctx.strokeRect(item.sx - r, item.sy - r, r * 2, r * 2);
        }
    }

    let clickStart = null;
    canvas.addEventListener('mousedown', e => {
        clickStart = { x: e.clientX, y: e.clientY };
    });
    canvas.addEventListener('mouseup', e => {
        if (!clickStart) return;
        const dx = e.clientX - clickStart.x;
        const dy = e.clientY - clickStart.y;
        clickStart = null;
        if (dx * dx + dy * dy > 25) return;

        const rect = canvas.getBoundingClientRect();
        const mx = e.clientX - rect.left;
        const my = e.clientY - rect.top;

        if (pickMode) {
            if (pickMode.target === 'rail') {
                const railKey = pickRail(mx, my);
                if (railKey) {
                    onPickedRail(railKey);
                } else {
                    pickMode = null;
                    renderPanelTop();
                }
                draw();
                return;
            }
            const hitS = pickSignal(mx, my);
            if (hitS) {
                onPickedSignal(hitS.key);
                draw();
                return;
            }
            if (pickMode.target === 'signalOrPoint') {
                const hitP = pickPoint(mx, my);
                if (hitP) {
                    onPickedPoint(hitP.switchId, hitP.pointKey);
                    draw();
                    return;
                }
            }
            pickMode = null;
            renderPanelTop();
            draw();
            return;
        }

        const hitS = pickSignal(mx, my);
        if (hitS) {
            selectSignal(hitS.key);
            return;
        }
        const hitP = pickPoint(mx, my);
        if (hitP) {
            selectPoint(hitP.switchId, hitP.pointKey);
            return;
        }
        selected = { list: null, id: null };
        pendingPointEdit = null;
        renderPanelTop();
        draw();
    });

    function pickSignal(mx, my) {
        const sizeRatio = Math.min(1, camera.scale / 1.5);
        const hitRadius = 8 * sizeRatio + 4;
        const gap = 10 * sizeRatio;

        const groups = new Map();
        for (const s of signals) {
            const key = Math.round(s.pos[0]) + ',' + Math.round(s.pos[2]);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(s);
        }

        let best = null;
        let bestDist = hitRadius * hitRadius;

        for (const list of groups.values()) {
            list.sort((a, b) => a.pos[1] - b.pos[1]);
            for (let i = 0; i < list.length; i++) {
                const s = list[i];
                const [sx, syBase] = worldToScreen(s.pos[0], s.pos[2]);
                const sy = syBase - i * gap;
                const dx = mx - sx;
                const dy = my - sy;
                const d2 = dx * dx + dy * dy;
                if (d2 < bestDist) {
                    bestDist = d2;
                    best = s;
                }
            }
        }

        if (!best) return null;
        const k = Math.floor(best.pos[0]) + ',' + Math.floor(best.pos[1]) + ',' + Math.floor(best.pos[2]);
        return { key: k, signal: best };
    }
    let computedPointPositions = [];
    function computePointPositions() {
        const sizeRatio = Math.min(1, camera.scale / 1.5);
        const signalRadius = 5 * sizeRatio;
        const spacing = signalRadius * 2.4 + 6;

        const raw = points.map(pt => {
            const [sx, sy] = worldToScreen(pt.pos[0], pt.pos[2]);
            return { pt, sx, sy };
        });

        const n = raw.length;
        if (n === 0) { computedPointPositions = []; return; }

        const parent = Array.from({length: n}, (_, i) => i);
        const find = i => { while (parent[i] !== i) { parent[i] = parent[parent[i]]; i = parent[i]; } return i; };
        const union = (i, j) => { parent[find(i)] = find(j); };

        for (let i = 0; i < n; i++) {
            for (let j = i + 1; j < n; j++) {
                const dx = raw[i].sx - raw[j].sx;
                const dy = raw[i].sy - raw[j].sy;
                if (dx * dx + dy * dy < spacing * spacing) {
                    union(i, j);
                }
            }
        }

        const groups = new Map();
        for (let i = 0; i < n; i++) {
            const r = find(i);
            if (!groups.has(r)) groups.set(r, []);
            groups.get(r).push(raw[i]);
        }

        const result = [];
        for (const group of groups.values()) {
            if (group.length === 1) {
                result.push(group[0]);
                continue;
            }

            let sumDx = 0, sumDy = 0;
            for (const item of group) {
                const yawDeg = (item.pt.yaw != null) ? item.pt.yaw : 0;
                const rad = yawDeg * Math.PI / 180;
                sumDx += -Math.sin(rad);
                sumDy +=  Math.cos(rad);
            }
            const mag = Math.sqrt(sumDx * sumDx + sumDy * sumDy);
            let dirX, dirY;
            if (mag > 1e-3) {
                dirX = sumDx / mag;
                dirY = sumDy / mag;
            } else {
                dirX = 1; dirY = 0;
            }

            group.sort((a, b) =>
            (a.sx * dirX + a.sy * dirY) - (b.sx * dirX + b.sy * dirY));

            let cx = 0, cy = 0;
            for (const item of group) { cx += item.sx; cy += item.sy; }
            cx /= group.length;
            cy /= group.length;

            const mid = (group.length - 1) / 2;
            for (let i = 0; i < group.length; i++) {
                const offset = (i - mid) * spacing;
                result.push({
                    pt: group[i].pt,
                    sx: cx + dirX * offset,
                    sy: cy + dirY * offset
                });
            }
        }
        computedPointPositions = result;
    }
    function pickPoint(mx, my) {
        const sizeRatio = Math.min(1, camera.scale / 1.5);
        const hitRadius = 8 * sizeRatio + 4;

        computePointPositions();

        let best = null;
        let bestDist = hitRadius * hitRadius;

        for (const item of computedPointPositions) {
            const dx = mx - item.sx;
            const dy = my - item.sy;
            const d2 = dx * dx + dy * dy;
            if (d2 < bestDist) {
                bestDist = d2;
                best = item.pt;
            }
        }
        if (!best) return null;
        return { switchId: best.switchId, pointKey: best.pointKey };
    }
    
    function pickRail(mx, my) {
        const sizeRatio = Math.min(1, camera.scale / 1.5);
        const hitRadius = 12 * sizeRatio + 4;
        let best = null;
        let bestDist = hitRadius * hitRadius;

        for (const r of rails) {
            for (const poly of r.polylines) {
                for (let i = 0; i < poly.length - 1; i++) {
                    const [sx1, sy1] = worldToScreen(poly[i][0], poly[i][2]);
                    const [sx2, sy2] = worldToScreen(poly[i+1][0], poly[i+1][2]);
                    const d2 = distToSegmentSq(mx, my, sx1, sy1, sx2, sy2);
                    if (d2 < bestDist) {
                        bestDist = d2;
                        best = r;
                    }
                }
            }
        }
        return best ? best.key : null;
    }

    function distToSegmentSq(px, py, x1, y1, x2, y2) {
        const dx = x2 - x1, dy = y2 - y1;
        const len2 = dx * dx + dy * dy;
        if (len2 < 1e-9) {
            const ex = px - x1, ey = py - y1;
            return ex * ex + ey * ey;
        }
        let t = ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        const ex = px - (x1 + t * dx);
        const ey = py - (y1 + t * dy);
        return ex * ex + ey * ey;
    }

    function onPickedSignal(signalKey) {
        if (!pickMode) return;
        if (pickMode.purpose === 'nSignal' && pendingPointEdit) {
            pendingPointEdit.nSignal = signalKey;
        } else if (pickMode.purpose === 'rSignal' && pendingPointEdit) {
            pendingPointEdit.rSignal = signalKey;
        } else if (pickMode.purpose === 'startSignal' && pendingSectionEdit) {
            pendingSectionEdit.startSignal = signalKey;
        } else if (pickMode.purpose === 'endSignal' && pendingSectionEdit) {
            pendingSectionEdit.endSignal = signalKey;
        }
        pickMode = null;
        renderPanelTop();
        draw();
    }

    function onPickedPoint(switchId, pointKey) {
        if (!pickMode) return;
        if (pickMode.target !== 'signalOrPoint') return;
        const key = switchId + '.' + pointKey;
        if (pickMode.purpose === 'endSignal' && pendingSectionEdit) {
            pendingSectionEdit.endSignal = key;
        } else if (pickMode.purpose === 'startSignal' && pendingSectionEdit) {
            pendingSectionEdit.startSignal = key;
        }
        pickMode = null;
        renderPanelTop();
        draw();
    }
    function onPickedRail(railKey) {
        if (!pendingSectionEdit) return;
        const parts = railKey.split(',');
        if (parts.length !== 3) return;
        const x = parseInt(parts[0], 10);
        const y = parseInt(parts[1], 10);
        const z = parseInt(parts[2], 10);
        if (isNaN(x) || isNaN(y) || isNaN(z)) return;

        const rails = pendingSectionEdit.rails;
        let idx = -1;
        for (let i = 0; i < rails.length; i++) {
            if (rails[i][0] === x && rails[i][1] === y && rails[i][2] === z) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            rails.splice(idx, 1);
        } else {
            rails.push([x, y, z]);
        }
        renderPanelTop();
        draw();
    }
    // パン/ズーム
    canvas.addEventListener('mousedown', e => {
        dragging = true; lastMouse = { x: e.clientX, y: e.clientY };
    });
    window.addEventListener('mouseup', () => dragging = false);
    window.addEventListener('mousemove', e => {
        if (!dragging) return;
        const dx = e.clientX - lastMouse.x;
        const dy = e.clientY - lastMouse.y;
        camera.x -= dx / camera.scale;
        camera.y -= dy / camera.scale;
        lastMouse = { x: e.clientX, y: e.clientY };
        draw();
    });
    canvas.addEventListener('wheel', e => {
        e.preventDefault();
        const rect = canvas.getBoundingClientRect();
        const mx = e.clientX - rect.left;
        const my = e.clientY - rect.top;
        const cx = canvas.clientWidth / 2;
        const cy = canvas.clientHeight / 2;

        const wx = (mx - cx) / camera.scale + camera.x;
        const wz = (my - cy) / camera.scale + camera.y;

        const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
        camera.scale *= factor;
        if (camera.scale < 0.2) camera.scale = 0.2;
        if (camera.scale > 20) camera.scale = 20;

        camera.x = wx - (mx - cx) / camera.scale;
        camera.y = wz - (my - cy) / camera.scale;
        draw();
    }, { passive: false });

    const STORAGE_KEY = 'aris.tree.v1';

    let treeState = {
        sections: { folders: {}, assignments: {}, order: [] },
        routes:   { folders: {}, assignments: {}, order: [] }
    };
    let selected = { list: null, id: null };

    function loadTreeState() {
        try {
            const s = localStorage.getItem(STORAGE_KEY);
            if (!s) return;
            const p = JSON.parse(s);
            if (p.sections) treeState.sections = p.sections;
            if (p.routes)   treeState.routes   = p.routes;
            for (const k of ['sections', 'routes']) {
                const t = treeState[k];
                if (!t.folders) t.folders = {};
                if (!t.assignments) t.assignments = {};
                if (!t.order) t.order = [];
            }
        } catch (e) { console.warn('tree load failed', e); }
    }

    function saveTreeState() {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(treeState));
        } catch (e) { console.warn('tree save failed', e); }
    }

    function syncItemsFromServer() {
        syncList('sections', sectionsData);
        syncList('routes',   routesData);
        saveTreeState();
    }

    function syncList(listKey, serverItems) {
        const t = treeState[listKey];
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

    function renderList(listKey) {
        const t = treeState[listKey];
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
                if (selected.list === listKey && selected.id === row.id) {
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
                    if (selected.list === listKey && selected.id === row.id) renderPanelTop();
                });
            } else {
                li.classList.add('item');
                if (selected.list === listKey && selected.id === row.id) {
                    li.classList.add('selected');
                }
                li.textContent = row.id;
                li.addEventListener('click', () => selectItem(listKey, row.id));
            }

            attachDragHandlers(li, listKey);
            ul.appendChild(li);
        }
    }

    let dragSrc = null; // { list, type, id }

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

            const t = treeState[listKey];
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
        if (t.folders[srcId]) {
            t.folders[srcId].parent = folderId;
        } else {
            t.assignments[srcId] = folderId;
        }
        const idx = t.order.indexOf(folderId);
        if (idx >= 0) {
            t.order.splice(idx + 1, 0, srcId);
        } else {
            t.order.push(srcId);
        }
    }

    function moveAsSiblingBefore(t, srcId, tgtId, parentId) {
        t.order = t.order.filter(x => x !== srcId);
        if (t.folders[srcId]) {
            t.folders[srcId].parent = parentId;
        } else {
            t.assignments[srcId] = parentId;
        }
        const idx = t.order.indexOf(tgtId);
        if (idx >= 0) {
            t.order.splice(idx, 0, srcId);
        } else {
            t.order.push(srcId);
        }
    }

    function moveAsSiblingAfter(t, srcId, tgtId, parentId) {
        t.order = t.order.filter(x => x !== srcId);
        if (t.folders[srcId]) {
            t.folders[srcId].parent = parentId;
        } else {
            t.assignments[srcId] = parentId;
        }
        const idx = t.order.indexOf(tgtId);
        if (idx >= 0) {
            t.order.splice(idx + 1, 0, srcId);
        } else {
            t.order.push(srcId);
        }
    }

    function selectItem(listKey, id) {
        selected = { list: listKey, id };
        if (listKey === 'sections') {
            initSectionEdit(id);
        } else {
            pendingSectionEdit = null;
        }
        renderList(listKey);
        renderPanelTop();
        draw();
    }

    function selectFolder(listKey, id) {
        selected = { list: listKey, id };
        renderList(listKey);
        renderPanelTop();
    }

    function selectSignal(key) {
        selected = { list: 'signals', id: key };
        pendingSectionEdit = null;
        renderPanelTop();
        draw();
    }

    function selectPoint(switchId, pointKey) {
        selected = { list: 'switches', id: switchId + '.' + pointKey };
        pendingSectionEdit = null;

        let nSig = '';
        let rSig = '';
        let lineId = null;
        for (const lid of Object.keys(lineConfigsCache)) {
            const cfg = lineConfigsCache[lid];
            if (cfg && cfg.switches && cfg.switches[switchId]
            && cfg.switches[switchId].points
            && cfg.switches[switchId].points[pointKey]) {
                const p = cfg.switches[switchId].points[pointKey];
                nSig = p.nSignal || '';
                rSig = p.rSignal || '';
                lineId = lid;
                break;
            }
        }
        if (!lineId) {
            const ids = Object.keys(lineConfigsCache);
            if (ids.length > 0) lineId = ids[0];
        }

        pendingPointEdit = { switchId, pointKey, nSignal: nSig, rSignal: rSig, lineId };
        pickMode = null;
        renderPanelTop();
        draw();
    }

    function initSectionEdit(sectionId) {
        let cfg = null;
        let lineId = null;
        for (const lid of Object.keys(lineConfigsCache)) {
            const c = lineConfigsCache[lid];
            if (c && c.sections && c.sections[sectionId]) {
                cfg = c.sections[sectionId];
                lineId = lid;
                break;
            }
        }
        if (!cfg) {
            pendingSectionEdit = null;
            return;
        }
        const railsCopy = (cfg.rails || []).map(r => [r[0], r[1], r[2]]);
        pendingSectionEdit = {
            sectionId,
            lineId,
            startSignal: cfg.startSignal || '',
            endSignal: cfg.endSignal || '',
            rails: railsCopy
        };
    }

    function renderPanelTop() {
        const ph = document.getElementById('panelTopPlaceholder');
        const content = document.getElementById('panelTopContent');

        if (!selected.id) {
            ph.style.display = '';
            content.style.display = 'none';
            return;
        }
        ph.style.display = 'none';
        content.style.display = '';

        // 信号
        if (selected.list === 'signals') {
            renderSignalPanel(content);
            return;
        }
        // 分岐ポイント
        if (selected.list === 'switches') {
            renderSwitchPanel(content);
            return;
        }

        const t = treeState[selected.list];
        const isFolder = !!t.folders[selected.id];

        if (isFolder) {
            const name = t.folders[selected.id].name || selected.id;
            content.innerHTML = `
                <h3>フォルダ</h3>
                <div class="field"><label>名前</label><div class="value">${escapeHtml(name)}</div></div>
                <div class="field"><label>ID</label><div class="value">${escapeHtml(selected.id)}</div></div>
                <div class="dangerZone"><button id="btnDeleteItem">このフォルダを削除</button></div>
            `;
            document.getElementById('btnDeleteItem').addEventListener('click', () => {
                if (!confirm('このフォルダを削除しますか？')) return;
                deleteFolder(selected.list, selected.id);
            });
        } else if (selected.list === 'sections') {
            renderSectionPanel(content, selected.id);
        } else {
            content.innerHTML = `
                <h3>進路</h3>
                <div class="field"><label>ID</label><div class="value">${escapeHtml(selected.id)}</div></div>
                <div class="field"><label>編集</label><div class="value" style="color:#888;">(未実装)</div></div>
                <div class="dangerZone"><button id="btnDeleteItem" disabled>この項目を削除 (未実装)</button></div>
            `;
        }
    }

    function renderSignalPanel(content) {
        const key = selected.id;
        const parts = key.split(',');
        const x = parts[0], y = parts[1], z = parts[2];

        let currentType = '';
        let foundLineId = null;
        for (const lineId of Object.keys(lineConfigsCache)) {
            const cfg = lineConfigsCache[lineId];
            if (cfg && cfg.signals && cfg.signals[key]) {
                currentType = cfg.signals[key].type || '';
                foundLineId = lineId;
                break;
            }
        }

        const types = [
            { v: '',   l: '(未登録)' },
            { v: '2A', l: '2灯式A' },
            { v: '2B', l: '2灯式B' },
            { v: '3',  l: '3灯式' },
            { v: '3A', l: '3灯式A' },
            { v: '3B', l: '3灯式B' },
            { v: '4',  l: '4灯式' },
            { v: '4A', l: '4灯式A' },
            { v: '4B', l: '4灯式B' },
            { v: '5A', l: '5灯式A' },
            { v: '5B', l: '5灯式B' },
            { v: '6',  l: '6灯式' }
        ];
        const options = types.map(t =>
        `<option value="${t.v}" ${t.v === currentType ? 'selected' : ''}>${t.l}</option>`
        ).join('');

        content.innerHTML = `
            <h3>信号</h3>
            <div class="field"><label>座標</label><div class="value">${x}, ${y}, ${z}</div></div>
            <div class="field">
                <label>灯式</label>
                <select id="signalTypeSelect">${options}</select>
            </div>
            <div class="field">
                <button id="btnSignalSave">保存</button>
            </div>
        `;

        document.getElementById('btnSignalSave').addEventListener('click', () => {
            const sel = document.getElementById('signalTypeSelect');
            saveSignal(key, sel.value);
        });
    }

    function renderSectionPanel(content, sectionId) {
        if (!pendingSectionEdit || pendingSectionEdit.sectionId !== sectionId) {
            initSectionEdit(sectionId);
        }
        if (!pendingSectionEdit) {
            content.innerHTML = `<h3>区間</h3><div class="field">区間が見つかりません: ${escapeHtml(sectionId)}</div>`;
            return;
        }
        const { startSignal, endSignal, rails } = pendingSectionEdit;
        
        const railItems = rails.map((r, i) => {
            const k = r[0] + ',' + r[1] + ',' + r[2];
            return `<div class="listRow">
                <span class="idx">${i + 1}</span>
                <span class="mono">${escapeHtml(k)}</span>
                <button class="railRemove danger" data-idx="${i}">×</button>
            </div>`;
        }).join('');
        const railBlock = rails.length > 0
        ? railItems
        : '<div class="listRow empty">(レール未設定)</div>';

        const pickStart = pickMode && pickMode.purpose === 'startSignal';
        const pickEnd = pickMode && pickMode.purpose === 'endSignal';
        const pickRail = pickMode && pickMode.target === 'rail';

        content.innerHTML = `
            <h3>区間</h3>
            <div class="field"><label>ID</label><div class="value">${escapeHtml(sectionId)}</div></div>

            <div class="field">
                <label>開始信号 (startSignal)</label>
                <div class="value">${startSignal ? escapeHtml(startSignal) : '(未設定)'}</div>
                <div class="buttonRow">
                    <button id="btnPickStart" class="${pickStart ? 'active' : ''}">
                        ${pickStart ? '選択中...' : '選択 (信号)'}
                    </button>
                    <button id="btnClearStart" class="danger" ${startSignal ? '' : 'disabled'}>削除</button>
                </div>
            </div>

            <div class="field">
                <label>終端 (endSignal)</label>
                <div class="value">${endSignal ? escapeHtml(endSignal) : '(未設定)'}</div>
                <div class="buttonRow">
                    <button id="btnPickEnd" class="${pickEnd ? 'active' : ''}">
                        ${pickEnd ? '選択中...' : '選択 (信号/分岐)'}
                    </button>
                    <button id="btnClearEnd" class="danger" ${endSignal ? '' : 'disabled'}>削除</button>
                </div>
            </div>

            <div class="field">
                <label>レール (${rails.length}本)</label>
                <div class="buttonRow">
                    <button id="btnPickRail" class="${pickRail ? 'active' : ''}">
                        ${pickRail ? '追加/削除モード中' : 'レール追加/削除モード'}
                    </button>
                </div>
                <div class="listBoxReadonly">${railBlock}</div>
            </div>

            <div class="field">
                <button id="btnSectionSave">保存</button>
            </div>
        `;

        document.getElementById('btnPickStart').addEventListener('click', () => {
            if (pickMode && pickMode.purpose === 'startSignal') {
                pickMode = null;
            } else {
                pickMode = {
                    target: 'signal',
                    purpose: 'startSignal',
                    sectionId,
                    lineId: pendingSectionEdit.lineId
                };
            }
            renderPanelTop();
            draw();
        });
        document.getElementById('btnPickEnd').addEventListener('click', () => {
            if (pickMode && pickMode.purpose === 'endSignal') {
                pickMode = null;
            } else {
                pickMode = {
                    target: 'signalOrPoint',
                    purpose: 'endSignal',
                    sectionId,
                    lineId: pendingSectionEdit.lineId
                };
            }
            renderPanelTop();
            draw();
        });
        document.getElementById('btnClearStart').addEventListener('click', () => {
            pendingSectionEdit.startSignal = '';
            if (pickMode && pickMode.purpose === 'startSignal') pickMode = null;
            renderPanelTop();
            draw();
        });
        document.getElementById('btnClearEnd').addEventListener('click', () => {
            pendingSectionEdit.endSignal = '';
            if (pickMode && pickMode.purpose === 'endSignal') pickMode = null;
            renderPanelTop();
            draw();
        });
        document.getElementById('btnPickRail').addEventListener('click', () => {
            if (pickMode && pickMode.target === 'rail') {
                pickMode = null;
            } else {
                pickMode = { target: 'rail', purpose: 'sectionRail', sectionId };
            }
            renderPanelTop();
            draw();
        });
        content.querySelectorAll('.railRemove').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const idx = parseInt(btn.dataset.idx, 10);
                if (isNaN(idx)) return;
                if (idx >= 0 && idx < pendingSectionEdit.rails.length) {
                    pendingSectionEdit.rails.splice(idx, 1);
                    renderPanelTop();
                    draw();
                }
            });
        });
        document.getElementById('btnSectionSave').addEventListener('click', saveSection);
    }

    async function saveSection() {
        if (!pendingSectionEdit) return;
        const { sectionId, lineId, startSignal, endSignal, rails } = pendingSectionEdit;
        if (!lineId) {
            alert('路線が見つかりません');
            return;
        }
        try {
            const r = await fetch('/api/section/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    lineId, sectionId,
                    startSignal: startSignal || null,
                    endSignal: endSignal || null,
                    rails: rails
                })
            });
            const res = await r.json();
            if (!res.ok) {
                alert('保存失敗: ' + (res.error || ''));
                return;
            }
            currentVersion = -1;
            fetchMap();
        } catch (e) {
            alert('通信エラー: ' + e);
        }
    }
    function formatSignalRef(ref) {
        if (!ref) return '(未設定)';
        const dotIdx = ref.lastIndexOf('.');
        if (dotIdx > 0 && /^P\d+$/.test(ref.substring(dotIdx + 1))) {
            return ref + ' [分岐]';
        }
        return ref + ' [信号]';
    }

    async function saveSignal(key, type) {
        let targetLineId = null;
        for (const lineId of Object.keys(lineConfigsCache)) {
            const cfg = lineConfigsCache[lineId];
            if (cfg && cfg.signals && cfg.signals[key]) {
                targetLineId = lineId;
                break;
            }
        }
        if (!targetLineId) {
            const ids = Object.keys(lineConfigsCache);
            if (ids.length === 0) {
                alert('路線がありません');
                return;
            }
            targetLineId = ids[0];
        }

        try {
            const r = await fetch('/api/signal/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ lineId: targetLineId, key, type })
            });
            const res = await r.json();
            if (!res.ok) {
                alert('保存失敗: ' + (res.error || ''));
                return;
            }
            currentVersion = -1;
            fetchMap();
        } catch (e) {
            alert('通信エラー: ' + e);
        }
    }

    function renderSwitchPanel(content) {
        if (!pendingPointEdit) {
            content.innerHTML = `<h3>分岐</h3><div class="field">ポイントが選択されていません</div>`;
            return;
        }
        const { switchId, pointKey, nSignal, rSignal } = pendingPointEdit;

        const pickN = pickMode && pickMode.purpose === 'nSignal';
        const pickR = pickMode && pickMode.purpose === 'rSignal';

        content.innerHTML = `
            <h3>分岐ポイント</h3>
            <div class="field"><label>スイッチ</label><div class="value">${escapeHtml(switchId)}</div></div>
            <div class="field"><label>ポイント</label><div class="value">${escapeHtml(pointKey)}</div></div>
            <div class="field">
                <label>N信号 (NORMAL時)</label>
                <div class="value">${nSignal ? escapeHtml(nSignal) : '(未設定)'}</div>
                <div class="buttonRow">
                    <button id="btnPickN" class="${pickN ? 'active' : ''}">
                        ${pickN ? '選択中...' : '選択'}
                    </button>
                    <button id="btnClearN" class="danger" ${nSignal ? '' : 'disabled'}>削除</button>
                </div>
            </div>
            <div class="field">
                <label>R信号 (REVERSE時)</label>
                <div class="value">${rSignal ? escapeHtml(rSignal) : '(未設定)'}</div>
                <div class="buttonRow">
                    <button id="btnPickR" class="${pickR ? 'active' : ''}">
                        ${pickR ? '選択中...' : '選択'}
                    </button>
                    <button id="btnClearR" class="danger" ${rSignal ? '' : 'disabled'}>削除</button>
                </div>
            </div>
            <div class="field">
                <button id="btnSwitchSave">保存</button>
            </div>
        `;

        document.getElementById('btnPickN').addEventListener('click', () => {
            if (pickMode && pickMode.purpose === 'nSignal') {
                pickMode = null;
            } else {
                pickMode = { purpose: 'nSignal', switchId, pointKey };
            }
            renderPanelTop();
            draw();
        });
        document.getElementById('btnPickR').addEventListener('click', () => {
            if (pickMode && pickMode.purpose === 'rSignal') {
                pickMode = null;
            } else {
                pickMode = { purpose: 'rSignal', switchId, pointKey };
            }
            renderPanelTop();
            draw();
        });
        document.getElementById('btnClearN').addEventListener('click', () => {
            pendingPointEdit.nSignal = '';
            if (pickMode && pickMode.purpose === 'nSignal') pickMode = null;
            renderPanelTop();
            draw();
        });
        document.getElementById('btnClearR').addEventListener('click', () => {
            pendingPointEdit.rSignal = '';
            if (pickMode && pickMode.purpose === 'rSignal') pickMode = null;
            renderPanelTop();
            draw();
        });
        document.getElementById('btnSwitchSave').addEventListener('click', saveSwitchPoint);
    }

    async function saveSwitchPoint() {
        if (!pendingPointEdit) return;
        const { switchId, pointKey, nSignal, rSignal, lineId } = pendingPointEdit;
        if (!lineId) {
            alert('路線が見つかりません');
            return;
        }

        try {
            const r = await fetch('/api/switch/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    lineId, switchId, pointKey,
                    nSignal: nSignal || null,
                    rSignal: rSignal || null
                })
            });
            const res = await r.json();
            if (!res.ok) {
                alert('保存失敗: ' + (res.error || ''));
                return;
            }
            currentVersion = -1;
            fetchMap();
        } catch (e) {
            alert('通信エラー: ' + e);
        }
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({
            '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
        }[c]));
    }

    function deleteFolder(listKey, folderId) {
        const t = treeState[listKey];
        const parentId = t.folders[folderId] ? (t.folders[folderId].parent || null) : null;
        for (const id of t.order) {
            if (t.folders[id] && t.folders[id].parent === folderId) {
                t.folders[id].parent = parentId;
            }
            if (t.assignments[id] === folderId) {
                t.assignments[id] = parentId;
            }
        }
        delete t.folders[folderId];
        t.order = t.order.filter(x => x !== folderId);
        if (selected.id === folderId) {
            selected = { list: null, id: null };
            renderPanelTop();
        }
        saveTreeState();
        renderList(listKey);
    }

    let folderCounter = 0;
    function newFolderId() {
        return 'f_' + Date.now() + '_' + (folderCounter++);
    }

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
            const t = treeState[listKey];
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
            alert('Add 機能は未実装です。\n(今後、区間/進路を新規作成するエンドポイントを追加予定)');
        });
    });

    let currentVersion = -1;

    async function pollVersion() {
        try {
            const r = await fetch('/api/map/version');
            const data = await r.json();
            if (data.version !== currentVersion) {
                currentVersion = data.version;
                await fetchMap();
            }
        } catch (e) {}
        setTimeout(pollVersion, 3000);
    }

    async function fetchMap() {
        try {
            const r = await fetch('/api/map');
            const data = await r.json();
            rails = data.rails || [];
            railKeyToGroup = {};
            for (const r of rails) {
                railKeyToGroup[r.key] = r.groupKey || r.key;
            }
            signals = data.signals || [];
            sectionsData = data.sections || [];
            routesData = data.routes || [];

            switches = [];
            const cacheSwitches = data.switches || [];
            for (const sw of cacheSwitches) {
                if (!sw || !sw.key || !sw.pos) continue;
                switches.push({ key: sw.key, pos: sw.pos, pointCount: sw.pointCount });
            }

            points = data.points || [];
            lineConfigsCache = data.lineConfigs || {};
            syncItemsFromServer();
            renderList('sections');
            renderList('routes');
            draw();
        } catch (e) { console.error(e); }
    }

    document.getElementById('btnRefresh').addEventListener('click', fetchMap);

    loadTreeState();
    renderPanelTop();
    window.addEventListener('resize', resize);
    resize();
    fetchMap().then(() => pollVersion());
})();
