'use strict';

const state = {
  fixed: null, current: null, graphId: null, selectedOrigin: null, selected: null,
  editMode: null, originMarker: null, customerMarker: null, serviceAreaLayer: null,
  markers: new Map(), edgeLayers: [], routeLayers: [], steps: [], step: -1, timer: null
};
const $ = id => document.getElementById(id);
const fmt = number => Number.isFinite(number)
  ? number.toLocaleString('pt-BR', {maximumFractionDigits: 2}) : '∞';
const toast = message => {
  const element = $('toast'); element.textContent = message; element.style.display = 'block';
  setTimeout(() => element.style.display = 'none', 4500);
};
const map = L.map('map', {zoomControl: true}).setView([-23.615, -46.65], 13);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);
map.createPane('serviceArea');
map.getPane('serviceArea').style.zIndex = '350';
map.getPane('serviceArea').style.pointerEvents = 'none';
const colors = {STORE: '#a3d52a', INTERMEDIATE: '#12634f', CUSTOMER: '#f17842'};

function icon(type) {
  const size = type === 'STORE' ? 20 : 15;
  return L.divIcon({className: '', html: `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${colors[type]};border:3px solid white;box-shadow:0 2px 7px #243b35aa"></div>`, iconSize: [20, 20], iconAnchor: [10, 10]});
}

function drawGraph(graph, route = [], routeGeometry = []) {
  [...state.markers.values()].forEach(marker => map.removeLayer(marker));
  state.edgeLayers.forEach(layer => map.removeLayer(layer));
  state.routeLayers.forEach(layer => map.removeLayer(layer));
  state.markers.clear(); state.edgeLayers = []; state.routeLayers = [];
  const points = new Map(graph.points.map(point => [point.id, point]));
  graph.edges.forEach(edge => {
    const from = points.get(edge.from), to = points.get(edge.to); if (!from || !to) return;
    const layer = L.polyline([[from.latitude, from.longitude], [to.latitude, to.longitude]], {color: '#799087', weight: 1.5, opacity: .42, dashArray: '5 5'}).addTo(map);
    layer._edge = `${edge.from}>${edge.to}`; state.edgeLayers.push(layer);
  });
  graph.points.forEach(point => {
    const marker = L.marker([point.latitude, point.longitude], {icon: icon(point.type), keyboard: true, title: point.name})
      .bindPopup(`<b>${escapeHtml(point.name)}</b><br>${escapeHtml(point.address)}`).addTo(map);
    state.markers.set(point.id, marker);
  });
  if (routeGeometry?.length >= 2) {
    state.routeLayers.push(L.polyline(routeGeometry, {color: '#f17842', weight: 6, opacity: .9}).addTo(map));
    return;
  }
  route.forEach(edge => {
    const from = points.get(edge.from), to = points.get(edge.to); if (!from || !to) return;
    state.routeLayers.push(L.polyline([[from.latitude, from.longitude], [to.latitude, to.longitude]], {color: '#f17842', weight: 4, opacity: .65, dashArray: '10 10'})
      .bindTooltip('Ligação esquemática: geometria viária indisponível').addTo(map));
  });
}

function escapeHtml(value = '') { const node = document.createElement('div'); node.textContent = value; return node.innerHTML; }
function drawServiceArea(points) {
  if (state.serviceAreaLayer) map.removeLayer(state.serviceAreaLayer);
  if (!points?.length) return;
  state.serviceAreaLayer = L.polygon(points.map(point => [point.latitude, point.longitude]), {pane: 'serviceArea', color: '#12634f', weight: 2, opacity: .55, fillColor: '#c8f55a', fillOpacity: .09, interactive: false}).addTo(map);
}
function renderMatrix(ids, matrix, points) {
  const byId = new Map(points.map(point => [point.id, point]));
  $('vertexLegend').replaceChildren(...ids.map(id => {
    const chip = document.createElement('span'); chip.textContent = `${id}: ${byId.get(id)?.name || id}`; return chip;
  }));
  const table = $('matrixTable'); table.replaceChildren();
  const head = document.createElement('tr'); head.append(document.createElement('th'));
  ids.forEach(id => { const th = document.createElement('th'); th.textContent = id; head.append(th); });
  const thead = document.createElement('thead'); thead.append(head); table.append(thead);
  const body = document.createElement('tbody');
  matrix.forEach((row, index) => {
    const tr = document.createElement('tr'), th = document.createElement('th'); th.textContent = ids[index]; tr.append(th);
    row.forEach(value => { const td = document.createElement('td'); td.textContent = value == null ? '∞' : fmt(value); if (value == null) td.className = 'infinity'; tr.append(td); });
    body.append(tr);
  });
  table.append(body);
}
function fitGraphPoints(points, maxZoom = 13) { if (points.length) map.fitBounds(points.map(point => [point.latitude, point.longitude]), {padding: [24, 24], maxZoom}); }
async function check(response) { const body = await response.json().catch(() => ({})); if (!response.ok) throw new Error(body.message || 'Não foi possível concluir a operação.'); return body; }

async function load() {
  try {
    const [graph, status] = await Promise.all([fetch('/api/graph').then(check), fetch('/api/status').then(check)]);
    state.fixed = graph; applyGraph(graph, false);
    const store = graph.graph.points[0]; selectOrigin({displayName: store.address, latitude: store.latitude, longitude: store.longitude, name: store.name}, false);
    $('apiStatus').textContent = status.dynamicGraphEnabled
      ? (status.demoMode ? 'Geração dinâmica • pontos novos com fallback local' : 'Geração híbrida disponível • fallback pronto')
      : 'Modo fixo • fallback pronto';
  } catch (error) { toast(error.message); }
}

function applyGraph(data, dynamic) {
  const fixedFallback = data.graphSource === 'GRAPH_JSON_FALLBACK';
  state.current = data.graph; state.graphId = dynamic && !fixedFallback ? data.graphId : null;
  drawServiceArea(data.serviceArea); drawGraph(data.graph); fitGraphPoints(data.graph.points);
  renderMatrix(data.vertexOrder, data.matrix, data.graph.points);
  $('demoButton').disabled = dynamic && !fixedFallback;
  resetSimulation(); clearDestination();
}

function renderSearchResults(container, results, onSelect) {
  const box = $(container); box.replaceChildren();
  results.forEach(result => {
    const button = document.createElement('button'); button.type = 'button'; button.textContent = result.displayName;
    button.addEventListener('click', () => onSelect(result)); box.append(button);
  });
}

$('originSearchForm').addEventListener('submit', async event => {
  event.preventDefault(); $('originResults').textContent = 'Buscando…';
  try {
    const results = await fetch('/api/geocoding/search?scope=origin&query=' + encodeURIComponent($('originAddress').value)).then(check);
    renderSearchResults('originResults', results, result => selectOrigin(result, true));
  } catch (error) { $('originResults').textContent = ''; toast(error.message); }
});
$('defaultOriginButton').addEventListener('click', () => {
  const store = state.fixed.graph.points[0];
  selectOrigin({displayName: store.address, latitude: store.latitude, longitude: store.longitude, name: store.name}, true);
});
function selectOrigin(result, pan) {
  state.selectedOrigin = {name: result.name || 'Origem escolhida', address: result.displayName, latitude: result.latitude, longitude: result.longitude};
  state.editMode = 'origin'; $('selectedOriginAddress').textContent = state.selectedOrigin.address;
  $('selectedOriginCoords').textContent = `${result.latitude.toFixed(6)}, ${result.longitude.toFixed(6)}`;
  if (state.originMarker) map.removeLayer(state.originMarker);
  state.originMarker = L.marker([result.latitude, result.longitude], {icon: icon('STORE')}).addTo(map);
  if (pan) map.panTo([result.latitude, result.longitude]);
}
$('generateGraphButton').addEventListener('click', async () => {
  if (!state.selectedOrigin) return;
  const button = $('generateGraphButton'), radiusKm = Number($('graphRadius').value);
  button.disabled = true; button.textContent = 'Gerando matriz da região…';
  $('graphGenerationStatus').textContent = 'Selecionando os pontos verificados mais próximos e montando a topologia…';
  try {
    const data = await fetch('/api/graphs/generate', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({...state.selectedOrigin, radiusKm, forceRefresh: false})}).then(check);
    if (state.originMarker) { map.removeLayer(state.originMarker); state.originMarker = null; }
    applyGraph(data, true); state.editMode = null;
    const intermediates = data.graph.points.filter(point => point.type === 'INTERMEDIATE').length;
    const source = data.graphSource === 'OSRM_RADIAL_DYNAMIC'
      ? 'com pontos viários novos distribuídos em dois anéis e pesos OSRM'
      : data.graphSource?.startsWith('LOCAL_')
      ? 'com pontos locais verificados e pesos acadêmicos de fallback'
      : data.graphSource === 'GRAPH_JSON_FALLBACK'
      ? 'usando o graph.json de demonstração porque o Overpass não respondeu'
      : data.fallbackUsed ? 'com topologia Overpass e pesos aproximados de fallback'
        : 'com topologia Overpass e pesos OSRM';
    $('graphGenerationStatus').textContent = `Grafo ${data.graphId} gerado com ${intermediates} pontos, ${data.graph.edges.length} ligações e ${source}.`;
  } catch (error) { $('graphGenerationStatus').textContent = 'A geração não foi concluída; o grafo anterior foi preservado.'; toast(error.message); }
  finally { button.disabled = false; button.textContent = 'Gerar grafo da região'; }
});

$('searchForm').addEventListener('submit', async event => {
  event.preventDefault(); $('searchResults').textContent = 'Buscando…';
  const scope = state.graphId ? 'dynamic' : 'service';
  try {
    const results = await fetch(`/api/geocoding/search?scope=${scope}&query=` + encodeURIComponent($('address').value)).then(check);
    renderSearchResults('searchResults', results, selectCustomer);
  } catch (error) { $('searchResults').textContent = ''; toast(error.message); }
});
$('demoButton').addEventListener('click', () => {
  if (state.graphId) { toast('O cliente de demonstração pertence ao grafo fixo. Pesquise um destino na região gerada.'); return; }
  const demo = state.fixed.demoCustomer; selectCustomer({displayName: demo.address, latitude: demo.latitude, longitude: demo.longitude, fallback: true});
});
function selectCustomer(result) {
  state.selected = {name: result.fallback ? 'Cliente de demonstração' : 'Cliente', address: result.displayName, latitude: result.latitude, longitude: result.longitude};
  state.editMode = 'customer'; $('selectedAddress').textContent = state.selected.address;
  $('selectedCoords').textContent = `${result.latitude.toFixed(6)}, ${result.longitude.toFixed(6)}`; $('selection').classList.remove('hidden');
  if (state.customerMarker) map.removeLayer(state.customerMarker);
  state.customerMarker = L.marker([result.latitude, result.longitude], {icon: icon('CUSTOMER')}).addTo(map); map.panTo([result.latitude, result.longitude]);
}
map.on('click', event => {
  if (state.editMode === 'origin' && state.selectedOrigin && state.originMarker) {
    state.selectedOrigin.latitude = event.latlng.lat; state.selectedOrigin.longitude = event.latlng.lng;
    state.selectedOrigin.address = `${state.selectedOrigin.address} (posição ajustada no mapa)`;
    state.originMarker.setLatLng(event.latlng); $('selectedOriginCoords').textContent = `${event.latlng.lat.toFixed(6)}, ${event.latlng.lng.toFixed(6)}`;
  } else if (state.editMode === 'customer' && state.selected && state.customerMarker) {
    state.selected.latitude = event.latlng.lat; state.selected.longitude = event.latlng.lng;
    state.customerMarker.setLatLng(event.latlng); $('selectedCoords').textContent = `${event.latlng.lat.toFixed(6)}, ${event.latlng.lng.toFixed(6)}`;
  }
});
$('confirmButton').addEventListener('click', async () => {
  if (!state.selected) return; const button = $('confirmButton'); button.disabled = true; button.textContent = 'Calculando pesos e Dijkstra…';
  try {
    const data = await fetch('/api/routes/optimize', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({...state.selected, graphId: state.graphId})}).then(check);
    showOptimization(data);
  } catch (error) { toast(error.message); }
  finally { button.disabled = false; button.textContent = 'Confirmar e calcular rota'; }
});

function showOptimization(data) {
  state.current = data.graph; state.steps = data.dijkstra.steps; state.step = -1;
  state.editMode = null;
  if (state.customerMarker) { map.removeLayer(state.customerMarker); state.customerMarker = null; }
  drawGraph(data.graph, data.segments, data.routeGeometry);
  const ids = data.graph.points.map(point => point.id), pathIds = new Set(data.dijkstra.path);
  fitGraphPoints(data.graph.points.filter(point => pathIds.has(point.id)), 14); renderMatrix(ids, data.matrix, data.graph.points);
  $('routePath').textContent = data.dijkstra.path.join(' → ');
  const missingGeometry = !data.routeGeometry || data.routeGeometry.length < 2;
  const weightText = data.fallbackUsed ? 'Pesos de fallback foram usados em parte do grafo.' : 'Pesos rodoviários obtidos pelo OSRM.';
  const detourText = Number.isFinite(data.detourRatio) && Number.isFinite(data.directReferenceDistanceKm)
    ? ` Validação: ${fmt(data.directReferenceDistanceKm)} km na referência direta e ${fmt(data.routeDistanceKm)} km pela sequência do grafo (${fmt((data.detourRatio - 1) * 100)}% de desvio).`
    : '';
  const rejectedCount = data.rejectedTransitPoints?.length || 0;
  const rejectedText = rejectedCount
    ? ` ${rejectedCount} ${rejectedCount === 1 ? 'ponto intermediário inadequado foi removido' : 'pontos intermediários inadequados foram removidos'} e o Dijkstra foi recalculado.`
    : '';
  const directSafetyText = data.directSafetyConnectionUsed
    ? ' As alternativas intermediárias causavam desvio desnecessário; o Dijkstra utilizou a ligação direta de segurança.'
    : '';
  $('routeMeta').textContent = missingGeometry
    ? `${weightText} Trechos tracejados são ligações esquemáticas, não trajetos sobre as ruas.${rejectedText}${directSafetyText}${detourText}`
    : `${weightText} A geometria foi calculada continuamente na ordem escolhida pelo Dijkstra.${rejectedText}${directSafetyText}${detourText}`;
  $('distanceBadge').textContent = `${fmt(data.dijkstra.totalDistanceKm)} km`; $('resultCard').classList.remove('hidden');
  ['startBtn', 'nextBtn', 'autoBtn', 'resetBtn'].forEach(id => $(id).disabled = false);
  $('simulationMessage').textContent = 'Rota pronta. Inicie para acompanhar como o algoritmo chegou à resposta.';
  $('resultCard').scrollIntoView({behavior: 'smooth', block: 'center'});
}

function renderStep(index) {
  state.step = Math.max(0, Math.min(index, state.steps.length - 1)); const step = state.steps[state.step];
  $('stepNumber').textContent = `${state.step + 1} / ${state.steps.length}`; $('currentVertex').textContent = step.selectedVertex;
  $('visitedVertices').textContent = step.visited.join(', ') || '—'; const updated = step.analyzedNeighbors.find(item => item.updated);
  $('simulationMessage').textContent = updated ? `A distância de ${updated.neighbor} era ${fmt(updated.oldDistance)}. Passando por ${step.selectedVertex}, o novo valor é ${fmt(step.distances[step.selectedVertex])} + ${fmt(updated.edgeWeight)} = ${fmt(updated.newDistance)} km.` : step.message;
  $('relaxations').replaceChildren(...step.analyzedNeighbors.map(item => { const element = document.createElement('div'); element.className = 'relax ' + (item.updated ? 'updated' : ''); element.textContent = `${step.selectedVertex} → ${item.neighbor}: ${fmt(item.candidateDistance)} km — ${item.reason}`; return element; }));
  $('distanceState').replaceChildren(...Object.entries(step.distances).map(([id, value]) => { const element = document.createElement('span'); element.textContent = `${id}: ${fmt(value)}${step.predecessors[id] ? ` ← ${step.predecessors[id]}` : ''}`; return element; }));
  state.markers.forEach((marker, id) => { const element = marker.getElement(); if (element) element.style.opacity = step.visited.includes(id) ? '1' : '.45'; });
  const current = state.markers.get(step.selectedVertex)?.getElement(); if (current) current.style.filter = 'drop-shadow(0 0 8px #f17842)';
  state.edgeLayers.forEach(layer => { const active = step.analyzedNeighbors.some(item => layer._edge === `${step.selectedVertex}>${item.neighbor}`); layer.setStyle({color: active ? '#3975d1' : '#799087', weight: active ? 4 : 1.5}); });
  $('prevBtn').disabled = state.step === 0; $('nextBtn').disabled = state.step === state.steps.length - 1;
  if (state.step === state.steps.length - 1) $('simulationMessage').textContent += ` Caminho final: ${$('routePath').textContent}.`;
}
function resetSimulation() {
  clearInterval(state.timer); state.steps = []; state.step = -1; $('resultCard').classList.add('hidden');
  ['startBtn', 'prevBtn', 'nextBtn', 'autoBtn', 'pauseBtn', 'resetBtn'].forEach(id => $(id).disabled = true);
  $('stepNumber').textContent = $('currentVertex').textContent = $('visitedVertices').textContent = '—';
  $('distanceState').textContent = 'Nenhuma execução.'; $('relaxations').replaceChildren();
  $('simulationMessage').textContent = 'Calcule uma rota para liberar a simulação.';
}
function clearDestination() {
  state.selected = null; state.editMode = null; $('selection').classList.add('hidden'); $('searchResults').replaceChildren();
  if (state.customerMarker) { map.removeLayer(state.customerMarker); state.customerMarker = null; }
}
$('startBtn').onclick = () => renderStep(0); $('nextBtn').onclick = () => renderStep(state.step + 1); $('prevBtn').onclick = () => renderStep(state.step - 1);
$('autoBtn').onclick = () => { clearInterval(state.timer); if (state.step < 0) renderStep(0); state.timer = setInterval(() => { if (state.step >= state.steps.length - 1) { clearInterval(state.timer); return; } renderStep(state.step + 1); }, 1400); $('pauseBtn').disabled = false; };
$('pauseBtn').onclick = () => { clearInterval(state.timer); $('pauseBtn').disabled = true; };
$('resetBtn').onclick = () => { clearInterval(state.timer); state.step = -1; $('stepNumber').textContent = $('currentVertex').textContent = $('visitedVertices').textContent = '—'; $('distanceState').textContent = 'Nenhuma execução.'; $('relaxations').replaceChildren(); $('simulationMessage').textContent = 'Simulação reiniciada. Clique em iniciar.'; state.markers.forEach(marker => { const element = marker.getElement(); if (element) { element.style.opacity = '1'; element.style.filter = ''; } }); };

load();
