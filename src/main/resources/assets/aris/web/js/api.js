import { state } from './state.js';
import { syncItemsFromServer, renderList, loadTreeState } from './config/tree.js';
import { renderPanelTop } from './config/panel.js';
import { draw } from './config/map.js';
import { drawOperation } from './operation/map.js';
import { renderRouteList } from './operation/route_list.js';

export async function fetchMap() {
    try {
        const r = await fetch('/api/map');
        const data = await r.json();
        
        const newWorld = data.worldName || '_default';
        if (newWorld !== state.worldName) {
            state.worldName = newWorld;
            loadTreeState();
        }

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
        renderPanelTop();
        draw();
        drawOperation();
        renderRouteList();
    } catch (e) { console.error(e); }
}

export async function fetchState() {
    try {
        const r = await fetch('/api/state');
        const data = await r.json();
        state.opRouteStates = {};
        if (data.routes) {
            for (const [id, v] of Object.entries(data.routes)) {
                state.opRouteStates[id] = v.status;
            }
        }
        state.opSectionOccupied = {};
        if (data.sections) {
            for (const [id, v] of Object.entries(data.sections)) {
                state.opSectionOccupied[id] = !!v.occupied;
            }
        }
        state.opSignalAspects = {};
        if (data.signals) {
            for (const [id, v] of Object.entries(data.signals)) {
                state.opSignalAspects[id] = v.aspect;
            }
        }
        state.opPointPositions = {};
        if (data.points) {
            for (const [id, v] of Object.entries(data.points)) {
                state.opPointPositions[id] = v.position;
            }
        }
        drawOperation();
        renderRouteList();
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

export async function pollState() {
    try {
        await fetchState();
    } catch (e) {}
    setTimeout(pollState, 2000);
}
