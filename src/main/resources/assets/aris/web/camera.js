import { state } from './state.js';
import { draw } from './map.js';

export function setupCamera() {
    const c = state.canvas;

    c.addEventListener('mousedown', e => {
        state.dragging = true;
        state.lastMouse = { x: e.clientX, y: e.clientY };
    });
    window.addEventListener('mouseup', () => { state.dragging = false; });
    window.addEventListener('mousemove', e => {
        if (!state.dragging) return;
        const dx = e.clientX - state.lastMouse.x;
        const dy = e.clientY - state.lastMouse.y;
        state.camera.x -= dx / state.camera.scale;
        state.camera.y -= dy / state.camera.scale;
        state.lastMouse = { x: e.clientX, y: e.clientY };
        draw();
    });
    c.addEventListener('wheel', e => {
        e.preventDefault();
        const rect = c.getBoundingClientRect();
        const mx = e.clientX - rect.left;
        const my = e.clientY - rect.top;
        const cx = c.clientWidth / 2;
        const cy = c.clientHeight / 2;
        const wx = (mx - cx) / state.camera.scale + state.camera.x;
        const wz = (my - cy) / state.camera.scale + state.camera.y;
        const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
        state.camera.scale *= factor;
        if (state.camera.scale < 0.2) state.camera.scale = 0.2;
        if (state.camera.scale > 20) state.camera.scale = 20;
        state.camera.x = wx - (mx - cx) / state.camera.scale;
        state.camera.y = wz - (my - cy) / state.camera.scale;
        draw();
    }, { passive: false });
}
