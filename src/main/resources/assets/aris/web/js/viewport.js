import { state } from './state.js';

export function worldToScreen(x, z) {
    const c = state.canvas;
    return [
        (x - state.camera.x) * state.camera.scale + c.clientWidth / 2,
        (z - state.camera.y) * state.camera.scale + c.clientHeight / 2
    ];
}

export function resizeCanvas() {
    const c = state.canvas;
    const dpr = window.devicePixelRatio || 1;
    c.width = c.clientWidth * dpr;
    c.height = c.clientHeight * dpr;
    state.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}
