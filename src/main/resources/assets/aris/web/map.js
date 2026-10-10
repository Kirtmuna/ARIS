import { state } from './state.js';
import { worldToScreen } from './viewport.js';
import { aspectColor, distToSegmentSq } from './util.js';

export function draw() {
    const c = state.canvas;
    const ctx = state.ctx;
    const w = c.clientWidth, h = c.clientHeight;
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, w, h);

    const switchRailKeys = new Set(
        state.switches.map(sw => sw.pos[0] + ',' + sw.pos[1] + ',' + sw.pos[2])
    );

    const selectedSectionRailKeys = new Set();
    if (state.selected.list === 'sections' && state.pendingSectionEdit) {
        const groupKeys = new Set();
        for (const sr of state.pendingSectionEdit.rails) {
            const k = sr[0] + ',' + sr[1] + ',' + sr[2];
            selectedSectionRailKeys.add(k);
            const gk = state.railKeyToGroup[k];
            if (gk) groupKeys.add(gk);
        }
        for (const r of state.rails) {
            const gk = state.railKeyToGroup[r.key];
            if (gk && groupKeys.has(gk)) selectedSectionRailKeys.add(r.key);
        }
    }

    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    for (const r of state.rails) {
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

    const sizeRatio = Math.min(1, state.camera.scale / 1.5);
    const signalRadius = 5 * sizeRatio;
    const signalStackGap = 10 * sizeRatio;
    const signalOutline = Math.max(1, 1 * sizeRatio);

    const groups = new Map();
    for (const s of state.signals) {
        const key = Math.round(s.pos[0]) + ',' + Math.round(s.pos[2]);
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key).push(s);
    }
    const selectedSignalKey = (state.selected.list === 'signals') ? state.selected.id : null;

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

    const selPointKey = (state.selected.list === 'switches' && state.pendingPointEdit)
    ? (state.pendingPointEdit.switchId + '.' + state.pendingPointEdit.pointKey) : null;

    computePointPositions();
    for (const item of state.computedPointPositions) {
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

export function computePointPositions() {
    const sizeRatio = Math.min(1, state.camera.scale / 1.5);
    const signalRadius = 5 * sizeRatio;
    const spacing = signalRadius * 2.4 + 6;

    const raw = state.points.map(pt => {
        const [sx, sy] = worldToScreen(pt.pos[0], pt.pos[2]);
        return { pt, sx, sy };
    });

    const n = raw.length;
    if (n === 0) { state.computedPointPositions = []; return; }

    const parent = Array.from({ length: n }, (_, i) => i);
    const find = i => { while (parent[i] !== i) { parent[i] = parent[parent[i]]; i = parent[i]; } return i; };
    const union = (i, j) => { parent[find(i)] = find(j); };

    for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
            const dx = raw[i].sx - raw[j].sx;
            const dy = raw[i].sy - raw[j].sy;
            if (dx * dx + dy * dy < spacing * spacing) union(i, j);
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
        if (group.length === 1) { result.push(group[0]); continue; }

        let sumDx = 0, sumDy = 0;
        for (const item of group) {
            const yawDeg = (item.pt.yaw != null) ? item.pt.yaw : 0;
            const rad = yawDeg * Math.PI / 180;
            sumDx += -Math.sin(rad);
            sumDy +=  Math.cos(rad);
        }
        const mag = Math.sqrt(sumDx * sumDx + sumDy * sumDy);
        let dirX, dirY;
        if (mag > 1e-3) { dirX = sumDx / mag; dirY = sumDy / mag; }
        else { dirX = 1; dirY = 0; }

        group.sort((a, b) => (a.sx * dirX + a.sy * dirY) - (b.sx * dirX + b.sy * dirY));

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
    state.computedPointPositions = result;
}

export function pickSignal(mx, my) {
    const sizeRatio = Math.min(1, state.camera.scale / 1.5);
    const hitRadius = 8 * sizeRatio + 4;
    const gap = 10 * sizeRatio;

    const groups = new Map();
    for (const s of state.signals) {
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
            if (d2 < bestDist) { bestDist = d2; best = s; }
        }
    }

    if (!best) return null;
    const k = Math.floor(best.pos[0]) + ',' + Math.floor(best.pos[1]) + ',' + Math.floor(best.pos[2]);
    return { key: k, signal: best };
}

export function pickPoint(mx, my) {
    const sizeRatio = Math.min(1, state.camera.scale / 1.5);
    const hitRadius = 8 * sizeRatio + 4;

    computePointPositions();

    let best = null;
    let bestDist = hitRadius * hitRadius;
    for (const item of state.computedPointPositions) {
        const dx = mx - item.sx;
        const dy = my - item.sy;
        const d2 = dx * dx + dy * dy;
        if (d2 < bestDist) { bestDist = d2; best = item.pt; }
    }
    if (!best) return null;
    return { switchId: best.switchId, pointKey: best.pointKey };
}

export function pickRail(mx, my) {
    const sizeRatio = Math.min(1, state.camera.scale / 1.5);
    const hitRadius = 12 * sizeRatio + 4;
    let best = null;
    let bestDist = hitRadius * hitRadius;

    for (const r of state.rails) {
        for (const poly of r.polylines) {
            for (let i = 0; i < poly.length - 1; i++) {
                const [sx1, sy1] = worldToScreen(poly[i][0], poly[i][2]);
                const [sx2, sy2] = worldToScreen(poly[i+1][0], poly[i+1][2]);
                const d2 = distToSegmentSq(mx, my, sx1, sy1, sx2, sy2);
                if (d2 < bestDist) { bestDist = d2; best = r; }
            }
        }
    }
    return best ? best.key : null;
}
