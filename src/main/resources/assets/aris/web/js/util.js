export function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({
        '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
    }[c]));
}

export function aspectColor(aspect) {
    switch (aspect) {
        case 0: return '#ff3030';
        case 1: return '#ffaa00';
        case 2: return '#ffdd00';
        case 3: return '#aaff44';
        case 4: return '#44dd44';
        case 5: return '#2288ff';
        default: return '#666';
    }
}

export function distToSegmentSq(px, py, x1, y1, x2, y2) {
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

export function computePointPositions(points, worldToScreen) {
    const spacing = 14;
    const raw = points.map(pt => {
        const [sx, sy] = worldToScreen(pt.pos[0], pt.pos[2]);
        return { pt, sx, sy };
    });
    const n = raw.length;
    if (n === 0) return [];

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
            const rad = ((item.pt.yaw ?? 0)) * Math.PI / 180;
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
        cx /= group.length; cy /= group.length;
        const mid = (group.length - 1) / 2;
        for (let i = 0; i < group.length; i++) {
            const offset = (i - mid) * spacing;
            result.push({ pt: group[i].pt, sx: cx + dirX * offset, sy: cy + dirY * offset });
        }
    }
    return result;
}
