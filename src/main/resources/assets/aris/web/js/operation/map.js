import { state } from '../state.js';
import { worldToScreenOn } from '../viewport.js';
import { aspectColor, computePointPositions } from '../util.js';

export function initOperation(canvas) {
    state.opCanvas = canvas;
    state.opCtx = canvas.getContext('2d');
}

export function drawOperation() {
    const c = state.opCanvas;
    const ctx = state.opCtx;
    if (!c || !ctx) return;

    const w = c.clientWidth, h = c.clientHeight;
    ctx.fillStyle = '#050505';
    ctx.fillRect(0, 0, w, h);

    const w2s = (x, z) => worldToScreenOn(c, state.opCamera, x, z);
    
    const occupiedRailKeys = new Set();
    if (state.lineConfig && state.lineConfig.sections) {
        for (const [secId, secCfg] of Object.entries(state.lineConfig.sections)) {
            if (!state.opSectionOccupied[secId]) continue;
            for (const r of (secCfg.rails || [])) {
                occupiedRailKeys.add(r[0] + ',' + r[1] + ',' + r[2]);
            }
        }
    }

    const switchRailKeys = new Set(
        state.switches.map(sw => sw.pos[0] + ',' + sw.pos[1] + ',' + sw.pos[2])
    );
    // レール
    ctx.lineCap = 'round';
    for (const r of state.rails) {
        const isSwitch = switchRailKeys.has(r.key);
        const isOccupied = occupiedRailKeys.has(r.key);
        if (isOccupied) {
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 4;
        } else if (isSwitch) {
            ctx.strokeStyle = '#804020';
            ctx.lineWidth = 1.5;
        } else {
            ctx.strokeStyle = '#404040';
            ctx.lineWidth = 1.5;
        }
        for (const poly of r.polylines) {
            ctx.beginPath();
            for (let i = 0; i < poly.length; i++) {
                const p = poly[i];
                const [sx, sy] = w2s(p[0], p[2]);
                if (i === 0) ctx.moveTo(sx, sy); else ctx.lineTo(sx, sy);
            }
            ctx.stroke();
        }
    }
    // 信号
    const sizeRatio = Math.min(1, state.opCamera.scale / 1.5);
    const signalRadius = 9 * sizeRatio;
    const signalStackGap = 14 * sizeRatio;

    const sigGroups = new Map();
    for (const s of state.signals) {
        const key = Math.round(s.pos[0]) + ',' + Math.round(s.pos[2]);
        if (!sigGroups.has(key)) sigGroups.set(key, []);
        sigGroups.get(key).push(s);
    }

    for (const list of sigGroups.values()) {
        list.sort((a, b) => a.pos[1] - b.pos[1]);
        for (let i = 0; i < list.length; i++) {
            const s = list[i];
            const [sx, syBase] = w2s(s.pos[0], s.pos[2]);
            const sy = syBase - i * signalStackGap;

            const skey = Math.floor(s.pos[0]) + ',' + Math.floor(s.pos[1]) + ',' + Math.floor(s.pos[2]);
            const aspect = (skey in state.opSignalAspects)
            ? state.opSignalAspects[skey] : -1;

            // 外側の黒縁
            ctx.beginPath();
            ctx.arc(sx, sy, signalRadius + 1.5, 0, Math.PI * 2);
            ctx.fillStyle = '#000';
            ctx.fill();

            // 本体
            ctx.beginPath();
            ctx.arc(sx, sy, signalRadius, 0, Math.PI * 2);
            ctx.fillStyle = aspectColor(aspect);
            ctx.fill();

            // ハイライト
            ctx.beginPath();
            ctx.arc(sx - signalRadius * 0.3, sy - signalRadius * 0.3, signalRadius * 0.35, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255,255,255,0.35)';
            ctx.fill();
        }
    }
    // ポイント
    const movablePoints = state.points.filter(pt => {
        const key = pt.switchId + '.' + pt.pointKey;
        return key in state.opPointPositions;
    });

    const opPoints = computePointPositions(movablePoints, w2s);
    for (const item of opPoints) {
        const pt = item.pt;
        const key = pt.switchId + '.' + pt.pointKey;
        const pos = state.opPointPositions[key];

        let color;
        if (pos === 'NORMAL')       color = '#4488ff';
        else if (pos === 'REVERSE') color = '#bb44ff';
        else                        color = '#666666';

        const r = 4 * sizeRatio;
        ctx.fillStyle = color;
        ctx.fillRect(item.sx - r, item.sy - r, r * 2, r * 2);
        ctx.strokeStyle = '#000';
        ctx.lineWidth = 1;
        ctx.strokeRect(item.sx - r, item.sy - r, r * 2, r * 2);
    }
}
