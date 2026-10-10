export const state = {
    canvas: null,
    ctx: null,
    camera: { x: 0, y: 0, scale: 1.5 },
    rails: [],
    signals: [],
    switches: [],
    points: [],
    sectionsData: [],
    routesData: [],
    lineConfig: null,
    railKeyToGroup: {},
    pickMode: null,
    pendingPointEdit: null,    // { switchId, pointKey, nSignal, rSignal }
    pendingSectionEdit: null,  // { sectionId, startSignal, endSignal, rails }
    pendingRouteEdit: null,    // { routeId, sections, nPoint, rPoint }
    selected: { list: null, id: null },
    dragging: false,
    lastMouse: { x: 0, y: 0 },
    clickStart: null,
    computedPointPositions: [],
    currentVersion: -1,
    treeState: {
        sections: { folders: {}, assignments: {}, order: [] },
        routes:   { folders: {}, assignments: {}, order: [] }
    }
};

export const STORAGE_KEY = 'aris.tree.v1';
