(() => {
    const canvas = document.getElementById('map');
    const ctx = canvas.getContext('2d');

    let camera = { x: 0, y: 0, scale: 1.5 };
    let rails = [];
    let signals = [];
    let dragging = false;
    let lastMouse = { x: 0, y: 0 };

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
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2;
        ctx.lineCap = 'round';
        for (const poly of rails) {
            ctx.beginPath();
            for (let i = 0; i < poly.length; i++) {
                const [x, y, z] = poly[i];
                const [sx, sy] = worldToScreen(x, z);
                if (i === 0) ctx.moveTo(sx, sy); else ctx.lineTo(sx, sy);
            }
            ctx.stroke();
        }

        // 信号
        const groups = new Map();
        for (const s of signals) {
            const key = Math.round(s.pos[0]) + ',' + Math.round(s.pos[2]);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(s);
        }
        for (const list of groups.values()) {
            list.sort((a, b) => a.pos[1] - b.pos[1]);
            for (let i = 0; i < list.length; i++) {
                const s = list[i];
                const [sx, syBase] = worldToScreen(s.pos[0], s.pos[2]);
                const sy = syBase - i * 10;
                ctx.fillStyle = aspectColor(s.aspect);
                ctx.beginPath();
                ctx.arc(sx, sy, 5, 0, Math.PI * 2);
                ctx.fill();
                ctx.strokeStyle = 'rgba(0,0,0,0.6)';
                ctx.lineWidth = 1;
                ctx.stroke();
            }
        }
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
            signals = data.signals || [];
            draw();
        } catch (e) { console.error(e); }
    }

    document.getElementById('btnRefresh').addEventListener('click', fetchMap);

    window.addEventListener('resize', resize);
    resize();
    pollVersion();
})();
