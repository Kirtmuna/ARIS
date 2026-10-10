import { state } from './state.js';
import { syncItemsFromServer, renderList } from './tree.js';
import { draw } from './map.js';

export async function fetchMap() {
    try {
        const r = await fetch('/api/map');
        const data = await r.json();
        state.rails = data.rails || [];
        state.railKeyToGroup = {};
        for (const r of state.rails) {
            state.railKeyToGroup[r.key] = r.groupKey || r.key;
        }
        state.signals = data.signals || [];
        state.sectionsData = data.sections || [];
        state.routesData = data.routes || [];

        state.switches = [];
        const cacheSwitches = data.switches || [];
        for (const sw of cacheSwitches) {
            if (!sw || !sw.key || !sw.pos) continue;
            state.switches.push({ key: sw.key, pos: sw.pos, pointCount: sw.pointCount });
        }

        state.points = data.points || [];
        state.lineConfig = data.lineConfig || null;
        syncItemsFromServer();
        renderList('sections');
        renderList('routes');
        draw();
    } catch (e) { console.error(e); }
}

export async function pollVersion() {
    try {
        const r = await fetch('/api/map/version');
        const data = await r.json();
        if (data.version !== state.currentVersion) {
            state.currentVersion = data.version;
            await fetchMap();
        }
    } catch (e) {}
    setTimeout(pollVersion, 3000);
}
