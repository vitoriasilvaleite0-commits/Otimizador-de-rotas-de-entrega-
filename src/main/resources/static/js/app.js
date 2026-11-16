'use strict';

/* ================= Estado e utilitários ================= */
const state = {
  fixed: null, graph: null, ids: [], matrix: [], graphId: null, labels: new Map(), names: new Map(), types: new Map(),
  selectedOrigin: null, selected: null, editMode: null, originMarker: null, customerMarker: null,
  serviceAreaLayer: null, markers: new Map(), edgeLayers: [], routeLayers: [],
  steps: [], step: -1, timer: null, path: [], routeKeys: new Set(), segments: []
};
const $ = id => document.getElementById(id);
const num = v => v == null ? Infinity : Number(v);
const fmt = v => { const n = num(v); return Number.isFinite(n) ? n.toLocaleString('pt-BR', {minimumFractionDigits: 1, maximumFractionDigits: 2}) : '∞'; };
const esc = (value = '') => String(value).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const label = id => state.labels.get(id) || id;
const nameOf = id => state.names.get(id) || id;
let toastTimer = null;
function toast(message) {
  const el = $('toast'); el.textContent = message; el.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { el.hidden = true; }, 5000);
}
async function check(response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || 'Não foi possível concluir a operação.');
  return body;
}

const ICON_HOME = '<svg viewBox="0 0 12 12" aria-hidden="true"><path d="M2 5.5 6 2l4 3.5V10H7.5V7.5h-3V10H2z" fill="currentColor"/></svg>';
const ICON_PIN = '<svg viewBox="0 0 12 12" aria-hidden="true"><path d="M6 1.2a3.3 3.3 0 0 0-3.3 3.3C2.7 7 6 10.8 6 10.8S9.3 7 9.3 4.5A3.3 3.3 0 0 0 6 1.2zm0 4.6a1.3 1.3 0 1 1 0-2.6 1.3 1.3 0 0 1 0 2.6z" fill="currentColor"/></svg>';

/* ================= Mapa ================= */
const map = L.map('map', {zoomControl: false, zoomSnap: 0.25}).setView([-23.6, -46.655], 12);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(map);
map.createPane('serviceArea');
map.getPane('serviceArea').style.zIndex = '350';
map.getPane('serviceArea').style.pointerEvents = 'none';
$('zoomIn').onclick = () => map.zoomIn();
$('zoomOut').onclick = () => map.zoomOut();

const narrow = () => matchMedia('(max-width: 960px)').matches;
function fitPadding() {
  if (narrow()) return {padding: [28, 28]};
  return {paddingTopLeft: [$('right').previousElementSibling.offsetWidth + 56, 48], paddingBottomRight: [$('right').offsetWidth + 56, 72]};
}
function fitPoints(points, maxZoom = 14) {
  map.invalidateSize();
  if (points.length) map.fitBounds(points.map(p => [p.latitude, p.longitude]), {...fitPadding(), maxZoom});
}

function pinIcon(kind, text, editing = false) {
  return L.divIcon({
    className: '', iconSize: [26, 26], iconAnchor: [13, 13],
    html: `<div class="pin ${kind}${editing ? ' editing' : ''}"><span class="disc">${kind === 'origin' ? ICON_HOME : ICON_PIN}</span><span class="lbl">${esc(text)}</span></div>`
  });
}
function vertexIcon(id) {
  return L.divIcon({className: '', iconSize: [14, 14], iconAnchor: [7, 7],
    html: `<div class="vtx dim"><span class="halo"></span><span class="dot"></span><span class="lbl">${esc(label(id))}</span></div>`});
}

function indexGraph(graph) {
  state.labels.clear(); state.names.clear(); state.types.clear();
  let n = 0;
  for (const p of graph.points) {
    state.names.set(p.id, p.name); state.types.set(p.id, p.type);
    if (p.type === 'STORE') state.labels.set(p.id, 'O');
    else if (p.type === 'CUSTOMER') state.labels.set(p.id, 'C');
    else { n += 1; const m = /^P0*(\d+)$/.exec(p.id); state.labels.set(p.id, m ? 'P' + m[1] : 'P' + n); }
  }
}

function clearGraphLayers() {
  [...state.markers.values()].forEach(m => map.removeLayer(m));
  state.edgeLayers.forEach(l => map.removeLayer(l));
  state.routeLayers.forEach(l => map.removeLayer(l));
  state.markers.clear(); state.edgeLayers = []; state.routeLayers = [];
}

function drawGraph(graph, segments = [], routeGeometry = []) {
  clearGraphLayers();
  const points = new Map(graph.points.map(p => [p.id, p]));
  const drawn = new Set();
  for (const e of graph.edges) {
    const a = points.get(e.from), b = points.get(e.to); if (!a || !b) continue;
    if (drawn.has(e.to + '>' + e.from)) continue; drawn.add(e.from + '>' + e.to);
    const layer = L.polyline([[a.latitude, a.longitude], [b.latitude, b.longitude]], {className: 'edge', interactive: false}).addTo(map);
    layer._k = e.from + '>' + e.to; layer._r = e.to + '>' + e.from; state.edgeLayers.push(layer);
  }
  if (routeGeometry?.length >= 2) {
    state.routeLayers.push(L.polyline(routeGeometry, {className: 'route-casing', interactive: false}).addTo(map));
    state.routeLayers.push(L.polyline(routeGeometry, {className: 'route', interactive: false}).addTo(map));
  } else if (segments.length) {
    for (const s of segments) {
      const a = points.get(s.from), b = points.get(s.to); if (!a || !b) continue;
      const coords = [[a.latitude, a.longitude], [b.latitude, b.longitude]];
      state.routeLayers.push(L.polyline(coords, {className: 'route-casing', interactive: false}).addTo(map));
      state.routeLayers.push(L.polyline(coords, {className: 'route schematic'}).bindTooltip('Ligação esquemática: sem traçado das ruas').addTo(map));
    }
  }
  for (const p of graph.points) {
    const icon = p.type === 'STORE' ? pinIcon('origin', 'Loja') : p.type === 'CUSTOMER' ? pinIcon('client', 'Cliente') : vertexIcon(p.id);
    const marker = L.marker([p.latitude, p.longitude], {icon, keyboard: true, title: `${label(p.id)} · ${p.name}`, zIndexOffset: p.type === 'INTERMEDIATE' ? 0 : 500})
      .bindPopup(`<b>${esc(label(p.id))} · ${esc(p.name)}</b><br>${esc(p.address || '')}`).addTo(map);
    state.markers.set(p.id, marker);
  }
}

function drawServiceArea(points) {
  if (state.serviceAreaLayer) map.removeLayer(state.serviceAreaLayer);
  state.serviceAreaLayer = null;
  if (!points?.length) return;
  state.serviceAreaLayer = L.polygon(points.map(p => [p.latitude, p.longitude]), {pane: 'serviceArea', className: 'area', interactive: false}).addTo(map);
}

/* ================= Matriz e ligações ================= */
function renderMatrix() {
  const table = $('matrixTable'); table.querySelectorAll('thead, tbody').forEach(n => n.remove());
  const ids = state.ids;
  const head = '<thead><tr><th scope="col"><span class="sr">De / para</span></th>' + ids.map(i => `<th scope="col" title="${esc(nameOf(i))}">${esc(label(i))}</th>`).join('') + '</tr></thead>';
  const body = '<tbody>' + ids.map((a, r) => `<tr><th scope="row" title="${esc(nameOf(a))}">${esc(label(a))}</th>` + ids.map((b, c) => {
    const v = state.matrix[r]?.[c], self = a === b, missing = !Number.isFinite(num(v));
    const cls = self ? 'self' : missing ? 'inf' : state.routeKeys.has(a + '>' + b) ? 'on-route' : '';
    return `<td class="${cls}" data-r="${r}" data-c="${c}">${self ? '0' : missing ? '∞' : fmt(v)}</td>`;
  }).join('') + '</tr>').join('') + '</tbody>';
  table.insertAdjacentHTML('beforeend', head + body);
  $('matrixEmpty').hidden = true; $('matrixWrap').hidden = false;
  $('cellRead').textContent = 'Passe o cursor sobre um valor para ler a ligação.';
}
$('matrixTable').addEventListener('pointerover', e => {
  const t = $('matrixTable'); t.querySelectorAll('.col-hover').forEach(x => x.classList.remove('col-hover'));
  const td = e.target.closest('td'); if (!td) return;
  t.querySelectorAll(`td[data-c="${td.dataset.c}"]`).forEach(x => x.classList.add('col-hover'));
  const a = state.ids[td.dataset.r], b = state.ids[td.dataset.c], v = state.matrix[td.dataset.r]?.[td.dataset.c];
  const oneWay = Number.isFinite(num(v)) && !Number.isFinite(num(state.matrix[td.dataset.c]?.[td.dataset.r]));
  $('cellRead').innerHTML = a === b ? `<b>${esc(label(a))}</b> para ele mesmo: 0 km.`
    : !Number.isFinite(num(v)) ? `Não há ligação direta de <b>${esc(label(a))}</b> para <b>${esc(label(b))}</b>.`
    : `<b>${esc(label(a))} → ${esc(label(b))}</b>: ${fmt(v)} km, de ${esc(nameOf(a))} até ${esc(nameOf(b))}${oneWay ? ' (mão única)' : ''}${state.routeKeys.has(a + '>' + b) ? '. Faz parte da rota.' : '.'}`;
});

function vbadgeClass(id) { const t = state.types.get(id); return t === 'STORE' ? 'origin' : t === 'CUSTOMER' ? 'client' : ''; }
function renderEdges() {
  const graph = state.graph, outs = new Map(), has = new Set(graph.edges.map(e => e.from + '>' + e.to));
  for (const e of graph.edges) { if (!outs.has(e.from)) outs.set(e.from, []); outs.get(e.from).push(e); }
  $('edgeList').innerHTML = graph.points.map(p => {
    const list = outs.get(p.id) || [];
    const chips = list.map(e => `<span class="edge-chip num${has.has(e.to + '>' + e.from) ? '' : ' oneway'}" title="${esc(nameOf(e.to))}">${esc(label(e.to))} <em>${fmt(e.distanceKm)}</em></span>`).join('') || '<span class="edge-chip"><em>sem saídas</em></span>';
    return `<div class="row"><span class="vbadge ${vbadgeClass(p.id)}" title="${esc(p.name)}">${esc(label(p.id))}</span><span class="grow"><span class="outs">${chips}</span></span></div>`;
  }).join('');
  $('graphInfo').textContent = `${graph.points.length} pontos · ${graph.edges.length} ligações`;
  $('edgesEmpty').hidden = true; $('edgeList').hidden = false;
}

/* ================= Carregamento e grafo ================= */
async function load() {
  try {
    const [graph, status] = await Promise.all([fetch('/api/graph').then(check), fetch('/api/status').then(check)]);
    // O mapa abre limpo: o grafo de demonstração fica guardado e só aparece se o usuário usar os exemplos.
    state.fixed = graph;
    $('apiStatus').textContent = status.dynamicGraphEnabled
      ? (status.demoMode ? 'Geração dinâmica ativa, com fallback local' : 'Geração híbrida ativa, com fallback local')
      : 'Modo fixo, com fallback local';
  } catch (error) { $('apiStatus').textContent = 'Serviços indisponíveis'; toast(error.message); }
}

function applyGraph(data, dynamic) {
  const fixedFallback = data.graphSource === 'GRAPH_JSON_FALLBACK';
  state.graph = data.graph; state.ids = data.vertexOrder; state.matrix = data.matrix;
  state.graphId = dynamic && !fixedFallback ? data.graphId : null;
  state.path = []; state.routeKeys = new Set(); state.segments = [];
  indexGraph(data.graph);
  drawServiceArea(data.serviceArea); drawGraph(data.graph); fitPoints(data.graph.points, 13);
  renderMatrix(); renderEdges();
  resetSimulation(); clearDestination();
}

function renderResults(container, results, onSelect) {
  const box = $(container); box.replaceChildren();
  if (!results.length) { const p = document.createElement('p'); p.textContent = 'Nenhum endereço encontrado. Tente com número e bairro.'; box.append(p); return; }
  results.forEach(result => {
    const button = document.createElement('button'); button.type = 'button';
    const [first, ...rest] = result.displayName.split(', ');
    button.innerHTML = `<b>${esc(first)}</b>${esc(rest.join(', '))}`;
    button.addEventListener('click', () => { box.replaceChildren(); onSelect(result); });
    box.append(button);
  });
}
function searching(container) { const p = document.createElement('p'); p.textContent = 'Buscando…'; $(container).replaceChildren(p); }

$('originSearchForm').addEventListener('submit', async event => {
  event.preventDefault(); searching('originResults'); $('searchResults').replaceChildren();
  try {
    const results = await fetch('/api/geocoding/search?scope=origin&query=' + encodeURIComponent($('originAddress').value)).then(check);
    renderResults('originResults', results, r => selectOrigin(r, true));
  } catch (error) { $('originResults').replaceChildren(); toast(error.message); }
});
$('defaultOriginButton').addEventListener('click', () => {
  const store = state.fixed.graph.points.find(p => p.type === 'STORE') || state.fixed.graph.points[0];
  selectOrigin({displayName: store.address, latitude: store.latitude, longitude: store.longitude, name: store.name}, true, true);
});
function selectOrigin(result, pan, isStore = false) {
  invalidateArea('A loja mudou. Gere a área de entrega de novo.');
  state.selectedOrigin = {name: result.name || 'Origem escolhida', address: result.displayName, latitude: result.latitude, longitude: result.longitude};
  state.editMode = 'origin';
  $('originAddress').value = result.displayName;
  if (state.originMarker) map.removeLayer(state.originMarker);
  state.originMarker = L.marker([result.latitude, result.longitude], {icon: pinIcon('origin', 'Loja', true), zIndexOffset: 900}).addTo(map);
  validateCustomRadius();
  const status = $('graphGenerationStatus'); status.className = 'hint';
  status.textContent = 'Escolha o raio e gere a área de entrega para liberar o cliente.';
  setHint('Clique no mapa se quiser ajustar o ponto exato da loja.');
  if (pan) map.setView([result.latitude, result.longitude], Math.max(map.getZoom(), 14));
}
function setHint(text) { $('mapHint').textContent = text; }

/* ===== Etapas: o cliente só libera depois que a área de entrega existe ===== */
function setClientLocked(locked) {
  $('clientSection').classList.toggle('step-locked', locked);
  $('address').disabled = locked; $('searchButton').disabled = locked; $('demoButton').disabled = locked;
  $('address').placeholder = locked ? 'Disponível depois de gerar a área' : 'Buscar endereço do cliente';
}
// Loja ou raio mudaram depois de gerar: a área antiga não vale mais, então apaga grafo, cliente e rota.
function invalidateArea(reason) {
  if (!state.areaReady) return;
  state.areaReady = false; state.graph = null; state.graphId = null; state.ids = []; state.matrix = [];
  state.path = []; state.routeKeys = new Set(); state.segments = [];
  clearGraphLayers(); drawServiceArea(null);
  resetSimulation(); clearDestination(); setClientLocked(true);
  $('graphInfo').textContent = '';
  $('matrixEmpty').hidden = false; $('matrixWrap').hidden = true;
  $('edgesEmpty').hidden = false; $('edgeList').hidden = true;
  const status = $('graphGenerationStatus'); status.className = 'hint'; status.textContent = reason;
}

const RADIUS_MIN = 3, RADIUS_MAX = 20;
const customSelected = () => $('radius').querySelector('[aria-pressed="true"]').dataset.km === 'custom';
// Valida enquanto digita: mostra o problema embaixo do campo e trava o botão, em vez de avisar só no clique.
function validateCustomRadius() {
  const raw = String($('customRadius').value).trim(), value = Number(raw.replace(',', '.'));
  const ok = !customSelected() || (raw !== '' && Number.isFinite(value) && value >= RADIUS_MIN && value <= RADIUS_MAX);
  $('customRadius').parentElement.classList.toggle('invalid', !ok);
  const help = $('customRadiusHelp');
  help.classList.toggle('err', !ok);
  help.textContent = ok ? `Entre ${RADIUS_MIN} e ${RADIUS_MAX} km.`
    : raw === '' || !Number.isFinite(value) ? `Digite um número entre ${RADIUS_MIN} e ${RADIUS_MAX}.`
    : value > RADIUS_MAX ? `O raio máximo é ${RADIUS_MAX} km.` : `O raio mínimo é ${RADIUS_MIN} km.`;
  $('generateGraphButton').disabled = !state.selectedOrigin || !ok;
  return ok;
}
function selectedRadius() {
  if (!customSelected()) return Number($('radius').querySelector('[aria-pressed="true"]').dataset.km);
  return validateCustomRadius() ? Number(String($('customRadius').value).replace(',', '.')) : null;
}
$('customRadius').addEventListener('input', () => {
  if (validateCustomRadius()) invalidateArea('O raio mudou. Gere a área de entrega de novo.');
});
$('generateGraphButton').addEventListener('click', async () => {
  if (!state.selectedOrigin) return;
  const radiusKm = selectedRadius();
  if (radiusKm === null) { toast('Digite um raio entre 3 e 20 km.'); $('customRadius').focus(); return; }
  const button = $('generateGraphButton'), status = $('graphGenerationStatus');
  button.disabled = true; button.innerHTML = '<span class="spinner" aria-hidden="true"></span>Gerando a área…';
  status.className = 'hint'; status.textContent = 'Escolhendo pontos nas ruas da região e calculando as distâncias…';
  try {
    const data = await fetch('/api/graphs/generate', {method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({...state.selectedOrigin, radiusKm, forceRefresh: false})}).then(check);
    if (state.originMarker) { map.removeLayer(state.originMarker); state.originMarker = null; }
    applyGraph(data, true); state.editMode = null;
    state.areaReady = true; setClientLocked(false);
    const intermediates = data.graph.points.filter(p => p.type === 'INTERMEDIATE').length;
    const source = data.graphSource === 'OSRM_RADIAL_DYNAMIC' ? 'pontos novos nas ruas e pesos do OSRM'
      : data.graphSource?.startsWith('LOCAL_') ? 'pontos locais verificados e pesos aproximados'
      : data.graphSource === 'GRAPH_JSON_FALLBACK' ? 'o grafo de demonstração, porque o serviço de mapas não respondeu'
      : data.fallbackUsed ? 'ruas do OpenStreetMap e pesos aproximados' : 'ruas do OpenStreetMap e pesos do OSRM';
    status.className = 'hint ok'; status.textContent = `Área pronta: ${intermediates} pontos, ${data.graph.edges.length} ligações, com ${source}.`;
    setHint('Loja definida. Agora escolha o cliente dentro da área.');
    $('address').focus();
  } catch (error) {
    status.className = 'hint err'; status.textContent = 'A área não foi gerada. Tente de novo ou mude o raio.'; toast(error.message);
  } finally { button.textContent = 'Gerar área de entrega'; validateCustomRadius(); }
});

$('searchForm').addEventListener('submit', async event => {
  event.preventDefault(); searching('searchResults'); $('originResults').replaceChildren();
  const scope = state.graphId ? 'dynamic' : 'service';
  try {
    const results = await fetch(`/api/geocoding/search?scope=${scope}&query=` + encodeURIComponent($('address').value)).then(check);
    renderResults('searchResults', results, selectCustomer);
  } catch (error) { $('searchResults').replaceChildren(); toast(error.message); }
});
// Cliente de exemplo dentro da área gerada: o ponto mais distante da loja, para a rota passar por vários pontos.
$('demoButton').addEventListener('click', () => {
  if (!state.areaReady || !state.graph) return;
  const store = state.graph.points.find(p => p.type === 'STORE');
  const far = state.graph.points.filter(p => p.type === 'INTERMEDIATE')
    .sort((a, b) => map.distance([b.latitude, b.longitude], [store.latitude, store.longitude]) - map.distance([a.latitude, a.longitude], [store.latitude, store.longitude]))[0];
  if (!far) return;
  // o "address" dos pontos gerados é técnico ("Ponto viário gerado pelo OSRM…"); o nome da rua é o que faz sentido mostrar
  selectCustomer({displayName: far.name || far.address, latitude: far.latitude, longitude: far.longitude, fallback: true});
});
function selectCustomer(result) {
  state.selected = {name: result.fallback ? 'Cliente de demonstração' : 'Cliente', address: result.displayName, latitude: result.latitude, longitude: result.longitude};
  state.editMode = 'customer'; $('address').value = result.displayName;
  if (state.customerMarker) map.removeLayer(state.customerMarker);
  state.customerMarker = L.marker([result.latitude, result.longitude], {icon: pinIcon('client', 'Cliente', true), zIndexOffset: 900}).addTo(map);
  map.panTo([result.latitude, result.longitude]);
  $('confirmButton').disabled = false;
  setHint('Clique no mapa para ajustar o ponto de entrega.');
}
map.on('click', event => {
  const {lat, lng} = event.latlng;
  if (state.editMode === 'origin' && state.selectedOrigin) {
    state.selectedOrigin.latitude = lat; state.selectedOrigin.longitude = lng;
    if (!/ajustada no mapa/.test(state.selectedOrigin.address)) state.selectedOrigin.address += ' (posição ajustada no mapa)';
    if (!state.originMarker) state.originMarker = L.marker(event.latlng, {icon: pinIcon('origin', 'Nova partida', true), zIndexOffset: 900}).addTo(map);
    else state.originMarker.setLatLng(event.latlng);
    setHint(`Partida ajustada para ${lat.toFixed(5)}, ${lng.toFixed(5)}. Gere o grafo para usar essa posição.`);
  } else if (state.editMode === 'customer' && state.selected && state.customerMarker) {
    state.selected.latitude = lat; state.selected.longitude = lng; state.customerMarker.setLatLng(event.latlng);
    setHint(`Entrega ajustada para ${lat.toFixed(5)}, ${lng.toFixed(5)}.`);
  }
});

$('confirmButton').addEventListener('click', async () => {
  if (!state.selected) return;
  if (!state.areaReady) { toast('Gere a área de entrega antes de calcular a rota.'); return; }
  const button = $('confirmButton'); button.disabled = true; button.innerHTML = '<span class="spinner" aria-hidden="true"></span>Calculando…';
  try {
    const data = await fetch('/api/routes/optimize', {method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({...state.selected, graphId: state.graphId})}).then(check);
    showOptimization(data);
  } catch (error) { toast(error.message); }
  finally { button.disabled = !state.selected; button.textContent = 'Calcular rota'; }
});

function showOptimization(data) {
  state.graph = data.graph; state.ids = data.graph.points.map(p => p.id); state.matrix = data.matrix;
  state.steps = data.dijkstra.steps; state.path = data.dijkstra.path; state.segments = data.segments || [];
  state.routeKeys = new Set(state.path.slice(1).map((v, i) => state.path[i] + '>' + v));
  state.editMode = null;
  if (state.customerMarker) { map.removeLayer(state.customerMarker); state.customerMarker = null; }
  indexGraph(data.graph);
  drawGraph(data.graph, data.segments, data.routeGeometry);
  const onPath = new Set(state.path);
  fitPoints(data.graph.points.filter(p => onPath.has(p.id)), 15);
  renderMatrix(); renderEdges(); renderRoute(data);
  $('simEmpty').hidden = true; $('sim').hidden = false;
  stop(); go(state.steps.length - 1);
  setHint('Rota calculada. Use o painel da direita para ver cada passo.');
}

function renderRoute(data) {
  const missingGeometry = !data.routeGeometry || data.routeGeometry.length < 2;
  const parts = [data.fallbackUsed ? 'Parte dos pesos é aproximada.' : 'Pesos rodoviários do OSRM.'];
  if (Number.isFinite(data.detourRatio) && Number.isFinite(data.directReferenceDistanceKm))
    parts.push(`Referência direta: ${fmt(data.directReferenceDistanceKm)} km (${fmt((data.detourRatio - 1) * 100)}% de desvio).`);
  const rejected = data.rejectedTransitPoints?.length || 0;
  if (rejected) parts.push(`${rejected} ${rejected === 1 ? 'ponto intermediário foi descartado' : 'pontos intermediários foram descartados'} por desvio.`);
  if (data.directSafetyConnectionUsed) parts.push('Os desvios pelos pontos intermediários eram longos demais, então a rota usa a ligação direta.');
  if (missingGeometry) parts.push('Trechos tracejados são ligações esquemáticas.');
  $('distanceBadge').innerHTML = `${fmt(data.dijkstra.totalDistanceKm)}<span>km</span>`;
  $('routeMeta').textContent = parts.join(' ');
  const segment = (a, b) => state.segments.find(s => s.from === a && s.to === b)?.distanceKm ?? num(state.matrix[state.ids.indexOf(a)]?.[state.ids.indexOf(b)]);
  let acc = 0;
  $('stops').innerHTML = state.path.map((id, i) => {
    if (i) acc += segment(state.path[i - 1], id);
    const t = state.types.get(id);
    const icon = t === 'STORE' ? `<span class="badge-icon origin">${ICON_HOME}</span>` : t === 'CUSTOMER' ? `<span class="badge-icon client">${ICON_PIN}</span>` : '<span class="stop-dot" aria-hidden="true"></span>';
    const sub = t === 'STORE' ? 'Saída da loja' : t === 'CUSTOMER' ? 'Entrega' : label(id);
    return `<li>${icon}<span><b title="${esc(nameOf(id))}">${esc(nameOf(id))}</b><small>${esc(sub)}</small></span><span class="km num">${i ? fmt(acc) + ' km' : 'início'}</span></li>`;
  }).join('');
  $('resultCard').hidden = false;
}

function clearDestination() {
  state.selected = null; if (state.editMode === 'customer') state.editMode = null;
  $('searchResults').replaceChildren(); $('address').value = ''; $('confirmButton').disabled = true;
  if (state.customerMarker) { map.removeLayer(state.customerMarker); state.customerMarker = null; }
}

/* ================= Simulação do Dijkstra ================= */
function stepClass(id, s) {
  const t = state.types.get(id);
  if (t === 'STORE') return 'origin';
  if (id === s.selectedVertex) return 'current';
  if (s.visited.includes(id)) return 'visited';
  return t === 'CUSTOMER' ? 'client' : '';
}
function render() {
  const s = state.steps[state.step], n = state.steps.length, last = state.step === n - 1;
  const u = s.selectedVertex, du = num(s.distances[u]);
  $('stepTitle').innerHTML = `Passo ${state.step + 1} <span>de ${n}</span>`;
  $('trackFill').style.width = `${((state.step + 1) / n) * 100}%`;
  const tr = $('track'); tr.setAttribute('aria-valuemax', n); tr.setAttribute('aria-valuenow', state.step + 1); tr.setAttribute('aria-valuetext', `Passo ${state.step + 1} de ${n}`);
  $('prev').disabled = $('restart').disabled = state.step === 0; $('next').disabled = last;
  const b = $('curBadge'); b.textContent = label(u); b.title = nameOf(u);
  const ups = s.analyzedNeighbors.filter(r => r.updated).length;
  if (state.step === 0) {
    $('curTitle').textContent = 'Começando pela loja';
    $('curText').textContent = 'A loja começa com distância 0 e todos os outros pontos com ∞. O algoritmo testa cada ligação que sai dela e anota as distâncias.';
  } else if (last && state.types.get(u) === 'CUSTOMER') {
    $('curTitle').textContent = `Chegou ao cliente: ${fmt(du)} km`;
    $('curText').textContent = `O cliente foi o ponto mais perto ainda aberto, então nenhum caminho mais curto pode aparecer. Rota final: ${state.path.map(label).join(' → ')}.`;
  } else {
    $('curTitle').textContent = `Visitando ${label(u)} · ${nameOf(u)}`;
    $('curText').textContent = `${label(u)} é o ponto aberto mais perto da loja, a ${fmt(du)} km. Essa distância agora é definitiva. ` +
      (s.analyzedNeighbors.length ? (ups ? `Testando as ligações que saem dele, ${ups === 1 ? '1 distância melhorou' : ups + ' distâncias melhoraram'}.` : 'Nenhuma ligação que sai dele encurta outro caminho.') : 'Ele não tem ligações para pontos ainda abertos.');
  }
  $('relaxTitle').textContent = `Ligações que saem de ${label(u)}`;
  $('relax').innerHTML = s.analyzedNeighbors.length ? s.analyzedNeighbors.map(r => `
    <div class="row">
      <span class="vbadge ${stepClass(r.neighbor, s)}">${esc(label(r.neighbor))}</span>
      <span class="grow"><span class="nm">${esc(nameOf(r.neighbor))}</span>
        <span class="calc num">${fmt(du)} + ${fmt(r.edgeWeight)} = ${fmt(r.candidateDistance)} km · antes ${fmt(r.oldDistance)}</span></span>
      <span class="pill ${r.updated ? 'up' : 'keep'}" title="${esc(r.reason || '')}">${r.updated ? 'Atualizada' : 'Mantida'}</span>
    </div>`).join('') : '<p class="empty">Nenhuma ligação para pontos ainda abertos.</p>';
  const ids = state.ids;
  const known = ids.filter(i => Number.isFinite(num(s.distances[i]))).sort((a, c) => num(s.distances[a]) - num(s.distances[c]));
  const unknown = ids.filter(i => !Number.isFinite(num(s.distances[i])));
  $('distLegend').textContent = `${s.visited.length} definitivas`;
  $('dist').innerHTML = known.map(i => `
    <div class="row">
      <span class="vbadge ${stepClass(i, s)}">${esc(label(i))}</span>
      <span class="grow name">${esc(nameOf(i))}</span>
      <span class="d num">${fmt(s.distances[i])} km</span>
      <span class="via">${s.predecessors[i] ? 'via ' + esc(label(s.predecessors[i])) : '—'}</span>
    </div>`).join('') + (unknown.length ? `<div class="row"><span class="grow name inf">${unknown.map(label).join(', ')}: ainda sem caminho (∞)</span></div>` : '');
  // mapa
  state.markers.forEach((marker, id) => {
    const el = marker.getElement()?.querySelector('.vtx'); if (!el) return;
    const queued = Number.isFinite(num(s.distances[id])) && !s.visited.includes(id);
    el.className = 'vtx ' + (id === u ? 'current' : s.visited.includes(id) ? 'visited' : queued ? 'queued' : 'dim');
  });
  const active = new Set(s.analyzedNeighbors.map(r => u + '>' + r.neighbor));
  const tree = new Set(Object.entries(s.predecessors).filter(([, p]) => p).map(([v, p]) => p + '>' + v));
  for (const layer of state.edgeLayers) {
    const el = layer.getElement(); if (!el) continue;
    const isActive = active.has(layer._k) || active.has(layer._r);
    el.classList.toggle('active', isActive);
    el.classList.toggle('tree', !isActive && (tree.has(layer._k) || tree.has(layer._r)));
  }
  $('map').classList.toggle('route-dim', !last);
}
function go(i) { if (!state.steps.length) return; state.step = Math.max(0, Math.min(state.steps.length - 1, i)); render(); }
function stop() { clearInterval(state.timer); state.timer = null; $('playIcon').innerHTML = '<path d="M4 2.5v9l7.5-4.5z" fill="currentColor"/>'; $('play').setAttribute('aria-label', 'Reproduzir'); }
function play() {
  if (!state.steps.length) return;
  if (state.timer) return stop();
  if (state.step === state.steps.length - 1) go(0);
  $('playIcon').innerHTML = '<path d="M4 2.5h2v9H4zM8 2.5h2v9H8z" fill="currentColor"/>'; $('play').setAttribute('aria-label', 'Pausar');
  state.timer = setInterval(() => { if (state.step >= state.steps.length - 1) return stop(); go(state.step + 1); }, 1300);
}
function resetSimulation() {
  stop(); state.steps = []; state.step = -1;
  $('sim').hidden = true; $('simEmpty').hidden = false; $('resultCard').hidden = true;
  $('map').classList.remove('route-dim');
}
$('next').onclick = () => { stop(); go(state.step + 1); };
$('prev').onclick = () => { stop(); go(state.step - 1); };
$('restart').onclick = () => { stop(); go(0); };
$('play').onclick = play;
$('track').addEventListener('pointerdown', e => { stop(); const r = e.currentTarget.getBoundingClientRect(); go(Math.floor(((e.clientX - r.left) / r.width) * state.steps.length)); });
$('track').addEventListener('keydown', e => { if (e.key === 'ArrowRight') { stop(); go(state.step + 1); e.preventDefault(); } if (e.key === 'ArrowLeft') { stop(); go(state.step - 1); e.preventDefault(); } });
document.addEventListener('keydown', e => {
  if (e.target.closest('input, .track, .leaflet-container') || $('sim').hidden) return;
  if (e.key === 'ArrowRight') { stop(); go(state.step + 1); }
  if (e.key === 'ArrowLeft') { stop(); go(state.step - 1); }
});
$('toSim').onclick = () => { $('tab-sim').click(); stop(); go(0); $('tab-sim').focus(); };

/* ================= Controles segmentados ================= */
function segmented(root, attr, onPick) {
  const thumb = root.querySelector('.thumb'), btns = [...root.querySelectorAll('button')];
  const place = () => { const on = btns.find(b => b.getAttribute(attr) === 'true'); if (!on || !on.offsetWidth) return; thumb.style.width = on.offsetWidth + 'px'; thumb.style.transform = `translateX(${on.offsetLeft - 4}px)`; };
  btns.forEach(b => b.addEventListener('click', () => { btns.forEach(x => x.setAttribute(attr, x === b ? 'true' : 'false')); place(); onPick?.(b, btns.indexOf(b)); }));
  new ResizeObserver(place).observe(root); place();
}
segmented($('radius'), 'aria-pressed', b => {
  const custom = b.dataset.km === 'custom';
  $('customRadiusRow').hidden = !custom; $('customRadiusHelp').hidden = !custom;
  validateCustomRadius();
  invalidateArea('O raio mudou. Gere a área de entrega de novo.');
  if (custom) { $('customRadius').focus(); $('customRadius').select(); }
});
segmented($('tabs'), 'aria-selected', (b, i) => {
  ['view-sim', 'view-matrix', 'view-edges'].forEach((v, j) => { $(v).hidden = j !== i; });
  $('right').classList.toggle('wide', i === 1);
});

/* ================= Tema claro / escuro ================= */
const SUN = '<circle cx="8" cy="8" r="3.2" fill="currentColor"/><path d="M8 1.2v1.6M8 13.2v1.6M1.2 8h1.6M13.2 8h1.6M3.2 3.2l1.1 1.1M11.7 11.7l1.1 1.1M3.2 12.8l1.1-1.1M11.7 4.3l1.1-1.1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>';
const MOON = '<path d="M13.5 9.6A5.6 5.6 0 0 1 6.4 2.5a5.6 5.6 0 1 0 7.1 7.1z" fill="currentColor"/>';
const isDark = () => document.documentElement.dataset.theme ? document.documentElement.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
function paintThemeBtn() {
  const dark = isDark();
  $('themeIcon').innerHTML = dark ? SUN : MOON;
  $('themeBtn').setAttribute('aria-label', dark ? 'Mudar para o tema claro' : 'Mudar para o tema escuro');
}
$('themeBtn').onclick = () => {
  const next = isDark() ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  try { localStorage.setItem('routify-theme', next); } catch (e) { /* sem armazenamento: vale só nesta visita */ }
  paintThemeBtn();
};
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', paintThemeBtn);
paintThemeBtn();

/* ================= Tela de início ================= */
function startApp() {
  const splash = $('splash');
  if (splash.classList.contains('leaving')) return;
  $('app').inert = false;
  splash.classList.add('leaving');
  const done = () => { splash.hidden = true; $('originAddress').focus({ preventScroll: true }); };
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) done();
  else splash.addEventListener('transitionend', done, { once: true });
}
$('splashStart').onclick = startApp;
$('splash').addEventListener('keydown', e => { if (e.key === 'Escape') startApp(); });
$('splashStart').focus();

window.addEventListener('resize', () => map.invalidateSize());
load();
