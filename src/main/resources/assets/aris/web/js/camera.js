export function setupCamera(canvas, camera, drawFn) {
    let dragging = false;
    let lastMouse = { x: 0, y: 0 };

    canvas.addEventListener('mousedown', e => {
        dragging = true;
        lastMouse = { x: e.clientX, y: e.clientY };
    });
    window.addEventListener('mouseup', () => { dragging = false; });
    window.addEventListener('mousemove', e => {
        if (!dragging) return;
        const dx = e.clientX - lastMouse.x;
        const dy = e.clientY - lastMouse.y;
        camera.x -= dx / camera.scale;
        camera.y -= dy / camera.scale;
        lastMouse = { x: e.clientX, y: e.clientY };
        drawFn();
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
        drawFn();
    }, { passive: false });
}
