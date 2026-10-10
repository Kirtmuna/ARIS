import { state } from './state.js';

/**
 * 指定canvas+cameraで、ワールド座標を画面座標に変換する。
 */
export function worldToScreenOn(canvas, camera, x, z) {
    return [
        (x - camera.x) * camera.scale + canvas.clientWidth / 2,
        (z - camera.y) * camera.scale + canvas.clientHeight / 2
    ];
}
/** 設定ページ用（state.canvas / state.camera） */
export function worldToScreen(x, z) {
    return worldToScreenOn(state.canvas, state.camera, x, z);
}

export function resizeCanvas(canvas, ctx) {
    const dpr = window.devicePixelRatio || 1;
    canvas.width = canvas.clientWidth * dpr;
    canvas.height = canvas.clientHeight * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}
