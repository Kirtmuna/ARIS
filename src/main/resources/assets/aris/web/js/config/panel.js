import { state } from '../state.js';
import { escapeHtml } from '../util.js';
import { renderList, deleteFolder } from './tree.js';
import { draw } from './map.js';
import {
saveSection, deleteSection, saveRoute, deleteRoute,
saveSignal, saveSwitchPoint
} from './edit.js';

export function initSectionEdit(sectionId) {
    const cfg = (state.lineConfig && state.lineConfig.sections)
    ? state.lineConfig.sections[sectionId] : null;
    if (!cfg) { state.pendingSectionEdit = null; return; }
    const railsCopy = (cfg.rails || []).map(r => [r[0], r[1], r[2]]);
    state.pendingSectionEdit = {
        sectionId,
        startSignal: cfg.startSignal || '',
        endSignal: cfg.endSignal || '',
        rails: railsCopy
    };
}

export function initRouteEdit(routeId) {
    const cfg = (state.lineConfig && state.lineConfig.routes)
    ? state.lineConfig.routes[routeId] : null;
    if (!cfg) { state.pendingRouteEdit = null; return; }
    const sections = (cfg.sections || []).slice();
    const nPoint = (cfg.route && cfg.route.nPoint) ? cfg.route.nPoint.slice() : [];
    const rPoint = (cfg.route && cfg.route.rPoint) ? cfg.route.rPoint.slice() : [];
    state.pendingRouteEdit = { routeId, sections, nPoint, rPoint };
}

export function renderPanelTop() {
    const ph = document.getElementById('panelTopPlaceholder');
    const content = document.getElementById('panelTopContent');

    if (!state.selected.id) {
        ph.style.display = '';
        content.style.display = 'none';
        return;
    }
    ph.style.display = 'none';
    content.style.display = '';

    if (state.selected.list === 'signals') { renderSignalPanel(content); return; }
    if (state.selected.list === 'switches') { renderSwitchPanel(content); return; }

    const t = state.treeState[state.selected.list];
    const isFolder = t && !!t.folders[state.selected.id];

    if (isFolder) {
        const name = t.folders[state.selected.id].name || state.selected.id;
        content.innerHTML = `
            <h3>フォルダ</h3>
            <div class="field"><label>名前</label><div class="value">${escapeHtml(name)}</div></div>
            <div class="field"><label>ID</label><div class="value">${escapeHtml(state.selected.id)}</div></div>
            <div class="dangerZone"><button id="btnDeleteItem">このフォルダを削除</button></div>
        `;
        document.getElementById('btnDeleteItem').addEventListener('click', () => {
            if (!confirm('このフォルダを削除しますか？')) return;
            deleteFolder(state.selected.list, state.selected.id);
        });
    } else if (state.selected.list === 'sections') {
        renderSectionPanel(content, state.selected.id);
    } else if (state.selected.list === 'routes') {
        renderRoutePanel(content, state.selected.id);
    } else {
        content.innerHTML = `<h3>不明なリスト: ${escapeHtml(state.selected.list || '')}</h3>`;
    }
}

function renderSignalPanel(content) {
    const key = state.selected.id;
    const parts = key.split(',');
    const x = parts[0], y = parts[1], z = parts[2];

    let currentType = '';
    const lc = state.lineConfig;
    if (lc && lc.signals && lc.signals[key]) currentType = lc.signals[key].type || '';

    const types = [
        { v: '',   l: '(未登録)' },
        { v: '2A', l: '2灯式A' }, { v: '2B', l: '2灯式B' },
        { v: '3',  l: '3灯式' },
        { v: '3A', l: '3灯式A' }, { v: '3B', l: '3灯式B' },
        { v: '4',  l: '4灯式' },
        { v: '4A', l: '4灯式A' }, { v: '4B', l: '4灯式B' },
        { v: '5A', l: '5灯式A' }, { v: '5B', l: '5灯式B' },
        { v: '6',  l: '6灯式' }
    ];
    const options = types.map(t =>
    `<option value="${t.v}" ${t.v === currentType ? 'selected' : ''}>${t.l}</option>`
    ).join('');

    content.innerHTML = `
        <h3>信号</h3>
        <div class="field"><label>座標</label><div class="value">${x}, ${y}, ${z}</div></div>
        <div class="field">
            <label>灯式</label>
            <select id="signalTypeSelect">${options}</select>
        </div>
        <div class="field"><button id="btnSignalSave">保存</button></div>
    `;

    document.getElementById('btnSignalSave').addEventListener('click', () => {
        const sel = document.getElementById('signalTypeSelect');
        saveSignal(key, sel.value);
    });
}

function renderSectionPanel(content, sectionId) {
    if (!state.pendingSectionEdit || state.pendingSectionEdit.sectionId !== sectionId) {
        initSectionEdit(sectionId);
    }
    if (!state.pendingSectionEdit) {
        content.innerHTML = `<h3>区間</h3><div class="field">区間が見つかりません: ${escapeHtml(sectionId)}</div>`;
        return;
    }
    const { startSignal, endSignal, rails } = state.pendingSectionEdit;

    const railItems = rails.map((r, i) => {
        const k = r[0] + ',' + r[1] + ',' + r[2];
        return `<div class="listRow">
            <span class="idx">${i + 1}</span>
            <span class="mono">${escapeHtml(k)}</span>
            <button class="railRemove danger" data-idx="${i}">×</button>
        </div>`;
    }).join('');
    const railBlock = rails.length > 0 ? railItems : '<div class="listRow empty">(レール未設定)</div>';

    const pickStart = state.pickMode && state.pickMode.purpose === 'startSignal';
    const pickEnd = state.pickMode && state.pickMode.purpose === 'endSignal';
    const pickRail = state.pickMode && state.pickMode.target === 'rail';

    content.innerHTML = `
        <h3>区間</h3>
        <div class="field">
            <label>ID</label>
            <input type="text" id="sectionIdInput" value="${escapeHtml(sectionId)}">
        </div>

        <div class="field">
            <label>開始信号 (startSignal)</label>
            <div class="value">${startSignal ? escapeHtml(startSignal) : '(未設定)'}</div>
            <div class="buttonRow">
                <button id="btnPickStart" class="${pickStart ? 'active' : ''}">
                    ${pickStart ? '選択中...' : '選択 (信号)'}
                </button>
                <button id="btnClearStart" class="danger" ${startSignal ? '' : 'disabled'}>削除</button>
            </div>
        </div>

        <div class="field">
            <label>終端 (endSignal)</label>
            <div class="value">${endSignal ? escapeHtml(endSignal) : '(未設定)'}</div>
            <div class="buttonRow">
                <button id="btnPickEnd" class="${pickEnd ? 'active' : ''}">
                    ${pickEnd ? '選択中...' : '選択 (信号/分岐)'}
                </button>
                <button id="btnClearEnd" class="danger" ${endSignal ? '' : 'disabled'}>削除</button>
            </div>
        </div>

        <div class="field">
            <label>レール (${rails.length}本)</label>
            <div class="buttonRow">
                <button id="btnPickRail" class="${pickRail ? 'active' : ''}">
                    ${pickRail ? '追加/削除モード中' : 'レール追加/削除モード'}
                </button>
            </div>
            <div class="listBoxReadonly">${railBlock}</div>
        </div>

        <div class="field"><button id="btnSectionSave">保存</button></div>
        <div class="dangerZone"><button id="btnSectionDelete">この区間を削除</button></div>
    `;

    document.getElementById('btnPickStart').addEventListener('click', () => {
        if (state.pickMode && state.pickMode.purpose === 'startSignal') {
            state.pickMode = null;
        } else {
            state.pickMode = { target: 'signal', purpose: 'startSignal', sectionId };
        }
        renderPanelTop();
        draw();
    });
    document.getElementById('btnPickEnd').addEventListener('click', () => {
        if (state.pickMode && state.pickMode.purpose === 'endSignal') {
            state.pickMode = null;
        } else {
            state.pickMode = { target: 'signalOrPoint', purpose: 'endSignal', sectionId };
        }
        renderPanelTop();
        draw();
    });
    document.getElementById('btnClearStart').addEventListener('click', () => {
        state.pendingSectionEdit.startSignal = '';
        if (state.pickMode && state.pickMode.purpose === 'startSignal') state.pickMode = null;
        renderPanelTop();
        draw();
    });
    document.getElementById('btnClearEnd').addEventListener('click', () => {
        state.pendingSectionEdit.endSignal = '';
        if (state.pickMode && state.pickMode.purpose === 'endSignal') state.pickMode = null;
        renderPanelTop();
        draw();
    });
    document.getElementById('btnPickRail').addEventListener('click', () => {
        if (state.pickMode && state.pickMode.target === 'rail') {
            state.pickMode = null;
        } else {
            state.pickMode = { target: 'rail', purpose: 'sectionRail', sectionId };
        }
        renderPanelTop();
        draw();
    });
    content.querySelectorAll('.railRemove').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const idx = parseInt(btn.dataset.idx, 10);
            if (isNaN(idx)) return;
            if (idx >= 0 && idx < state.pendingSectionEdit.rails.length) {
                state.pendingSectionEdit.rails.splice(idx, 1);
                renderPanelTop();
                draw();
            }
        });
    });
    document.getElementById('btnSectionSave').addEventListener('click', saveSection);
    document.getElementById('btnSectionDelete').addEventListener('click', () => {
        if (!confirm('区間 "' + sectionId + '" を削除しますか？')) return;
        deleteSection(sectionId);
    });
}

function renderRoutePanel(content, routeId) {
    if (!state.pendingRouteEdit || state.pendingRouteEdit.routeId !== routeId) {
        initRouteEdit(routeId);
    }
    if (!state.pendingRouteEdit) {
        content.innerHTML = `<h3>進路</h3><div class="field">進路が見つかりません: ${escapeHtml(routeId)}</div>`;
        return;
    }
    const { sections, nPoint, rPoint } = state.pendingRouteEdit;

    const secItems = sections.map((s, i) =>
    `<div class="listRow">
        <span class="idx">${i + 1}</span>
        <span class="mono">${escapeHtml(s)}</span>
        <button class="routeRemove danger" data-kind="section" data-idx="${i}">×</button>
    </div>`
    ).join('');
    const secBlock = sections.length > 0 ? secItems : '<div class="listRow empty">(未設定)</div>';

    const availableSections = state.sectionsData.filter(s => !sections.includes(s));
    const sectionOptions = availableSections.map(s =>
    `<option value="${escapeHtml(s)}">${escapeHtml(s)}</option>`
    ).join('');
    const canAddSection = availableSections.length > 0;

    const nItems = nPoint.map((p, i) =>
    `<div class="listRow">
        <span class="idx">${i + 1}</span>
        <span class="mono">${escapeHtml(p)}</span>
        <button class="routeRemove danger" data-kind="nPoint" data-idx="${i}">×</button>
    </div>`
    ).join('');
    const nBlock = nPoint.length > 0 ? nItems : '<div class="listRow empty">(未設定)</div>';

    const rItems = rPoint.map((p, i) =>
    `<div class="listRow">
        <span class="idx">${i + 1}</span>
        <span class="mono">${escapeHtml(p)}</span>
        <button class="routeRemove danger" data-kind="rPoint" data-idx="${i}">×</button>
    </div>`
    ).join('');
    const rBlock = rPoint.length > 0 ? rItems : '<div class="listRow empty">(未設定)</div>';

    const pickN = state.pickMode && state.pickMode.purpose === 'nPoint';
    const pickR = state.pickMode && state.pickMode.purpose === 'rPoint';

    content.innerHTML = `
        <h3>進路</h3>
        <div class="field">
            <label>ID</label>
            <input type="text" id="routeIdInput" value="${escapeHtml(routeId)}">
        </div>

        <div class="field">
            <label>区間 (${sections.length}個)</label>
            <div class="listBoxReadonly">${secBlock}</div>
            <div class="buttonRow">
                <select id="sectionAddSelect" ${canAddSection ? '' : 'disabled'}>
                    ${canAddSection ? sectionOptions : '<option>(追加可能な区間なし)</option>'}
                </select>
                <button id="btnAddSectionToRoute" ${canAddSection ? '' : 'disabled'}>追加</button>
            </div>
        </div>

        <div class="field">
            <label>N ポイント</label>
            <div class="listBoxReadonly">${nBlock}</div>
            <div class="buttonRow">
                <button id="btnPickNPoint" class="${pickN ? 'active' : ''}">
                    ${pickN ? '選択中...' : '追加'}
                </button>
            </div>
        </div>

        <div class="field">
            <label>R ポイント</label>
            <div class="listBoxReadonly">${rBlock}</div>
            <div class="buttonRow">
                <button id="btnPickRPoint" class="${pickR ? 'active' : ''}">
                    ${pickR ? '選択中...' : '追加'}
                </button>
            </div>
        </div>

        <div class="field"><button id="btnRouteSave">保存</button></div>
        <div class="dangerZone"><button id="btnRouteDelete">この進路を削除</button></div>
    `;

    document.getElementById('btnAddSectionToRoute').addEventListener('click', () => {
        const sel = document.getElementById('sectionAddSelect');
        if (!sel || sel.disabled) return;
        const v = sel.value;
        if (!v) return;
        if (!state.pendingRouteEdit.sections.includes(v)) {
            state.pendingRouteEdit.sections.push(v);
        }
        renderPanelTop();
        draw();
    });

    document.getElementById('btnPickNPoint').addEventListener('click', () => {
        if (state.pickMode && state.pickMode.purpose === 'nPoint') {
            state.pickMode = null;
        } else {
            state.pickMode = { target: 'pointOnly', purpose: 'nPoint', routeId };
        }
        renderPanelTop();
        draw();
    });
    document.getElementById('btnPickRPoint').addEventListener('click', () => {
        if (state.pickMode && state.pickMode.purpose === 'rPoint') {
            state.pickMode = null;
        } else {
            state.pickMode = { target: 'pointOnly', purpose: 'rPoint', routeId };
        }
        renderPanelTop();
        draw();
    });

    content.querySelectorAll('.routeRemove').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const kind = btn.dataset.kind;
            const idx = parseInt(btn.dataset.idx, 10);
            if (isNaN(idx)) return;
            if (kind === 'section' && idx >= 0 && idx < state.pendingRouteEdit.sections.length) {
                state.pendingRouteEdit.sections.splice(idx, 1);
            } else if (kind === 'nPoint' && idx >= 0 && idx < state.pendingRouteEdit.nPoint.length) {
                state.pendingRouteEdit.nPoint.splice(idx, 1);
            } else if (kind === 'rPoint' && idx >= 0 && idx < state.pendingRouteEdit.rPoint.length) {
                state.pendingRouteEdit.rPoint.splice(idx, 1);
            }
            renderPanelTop();
            draw();
        });
    });

    document.getElementById('btnRouteSave').addEventListener('click', saveRoute);
    document.getElementById('btnRouteDelete').addEventListener('click', () => {
        if (!confirm('進路 "' + routeId + '" を削除しますか？')) return;
        deleteRoute(routeId);
    });
}

function renderSwitchPanel(content) {
    if (!state.pendingPointEdit) {
        content.innerHTML = `<h3>分岐</h3><div class="field">ポイントが選択されていません</div>`;
        return;
    }
    const { switchId, pointKey, nSignal, rSignal } = state.pendingPointEdit;

    const pickN = state.pickMode && state.pickMode.purpose === 'nSignal';
    const pickR = state.pickMode && state.pickMode.purpose === 'rSignal';

    content.innerHTML = `
        <h3>分岐ポイント</h3>
        <div class="field"><label>スイッチ</label><div class="value">${escapeHtml(switchId)}</div></div>
        <div class="field"><label>ポイント</label><div class="value">${escapeHtml(pointKey)}</div></div>
        <div class="field">
            <label>N信号 (NORMAL時)</label>
            <div class="value">${nSignal ? escapeHtml(nSignal) : '(未設定)'}</div>
            <div class="buttonRow">
                <button id="btnPickN" class="${pickN ? 'active' : ''}">
                    ${pickN ? '選択中...' : '選択'}
                </button>
                <button id="btnClearN" class="danger" ${nSignal ? '' : 'disabled'}>削除</button>
            </div>
        </div>
        <div class="field">
            <label>R信号 (REVERSE時)</label>
            <div class="value">${rSignal ? escapeHtml(rSignal) : '(未設定)'}</div>
            <div class="buttonRow">
                <button id="btnPickR" class="${pickR ? 'active' : ''}">
                    ${pickR ? '選択中...' : '選択'}
                </button>
                <button id="btnClearR" class="danger" ${rSignal ? '' : 'disabled'}>削除</button>
            </div>
        </div>
        <div class="field"><button id="btnSwitchSave">保存</button></div>
    `;

    document.getElementById('btnPickN').addEventListener('click', () => {
        if (state.pickMode && state.pickMode.purpose === 'nSignal') state.pickMode = null;
        else state.pickMode = { purpose: 'nSignal', switchId, pointKey };
        renderPanelTop();
        draw();
    });
    document.getElementById('btnPickR').addEventListener('click', () => {
        if (state.pickMode && state.pickMode.purpose === 'rSignal') state.pickMode = null;
        else state.pickMode = { purpose: 'rSignal', switchId, pointKey };
        renderPanelTop();
        draw();
    });
    document.getElementById('btnClearN').addEventListener('click', () => {
        state.pendingPointEdit.nSignal = '';
        if (state.pickMode && state.pickMode.purpose === 'nSignal') state.pickMode = null;
        renderPanelTop();
        draw();
    });
    document.getElementById('btnClearR').addEventListener('click', () => {
        state.pendingPointEdit.rSignal = '';
        if (state.pickMode && state.pickMode.purpose === 'rSignal') state.pickMode = null;
        renderPanelTop();
        draw();
    });
    document.getElementById('btnSwitchSave').addEventListener('click', saveSwitchPoint);
}
