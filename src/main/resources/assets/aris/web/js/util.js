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
