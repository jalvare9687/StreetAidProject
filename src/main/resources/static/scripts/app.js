
let map          = null;
let markers      = [];
let allResources = [];
let currentFilter = 'all';
let selectedId    = null;
let sidebarOpen   = true;
let liveInterval  = null;
let currentZip    = null;

// ── KNOWN ATLANTA ZIP SUGGESTIONS ────────────────────────────
const ZIP_SUGGESTIONS = [
  { zip: '30314', label: '30314 — Vine City / English Ave' },
  { zip: '30310', label: '30310 — West End' },
  { zip: '30318', label: '30318 — Bankhead / Grove Park' },
  { zip: '30315', label: '30315 — Summerhill / Mechanicsville' },
  { zip: '30316', label: '30316 — East Atlanta Village' },
  { zip: '30303', label: '30303 — Downtown Atlanta' },
  { zip: '30312', label: '30312 — Old Fourth Ward' },
  { zip: '30306', label: '30306 — Virginia Highland' },
];

// ── TYPE CONFIG ───────────────────────────────────────────────
const TYPE_CONFIG = {
  PANTRY:   { label: 'Food Pantry',      color: '#EF9F27', icon: 'fa-utensils'    },
  HOT_MEAL: { label: 'Hot Meals',        color: '#EF9F27', icon: 'fa-bowl-food'   },
  SNAP:     { label: 'SNAP / EBT Store', color: '#378ADD', icon: 'fa-credit-card' },
  SHELTER:  { label: 'Shelter',          color: '#7F77DD', icon: 'fa-house'       },
  WATER:    { label: 'Drinking Water',   color: '#1D9E75', icon: 'fa-droplet'     },
  FRIDGE:   { label: 'Community Fridge', color: '#EF9F27', icon: 'fa-utensils'    },
  GARDEN:   { label: 'Community Garden', color: '#1D9E75', icon: 'fa-leaf'        },
  COOLING:  { label: 'Cooling Center',   color: '#D85A30', icon: 'fa-snowflake'   },
  WARMING:  { label: 'Warming Center',   color: '#D85A30', icon: 'fa-fire'        },
  RESOURCE: { label: 'Resource',         color: '#888888', icon: 'fa-circle-info' },
};

function typeConfig(type) {
  return TYPE_CONFIG[type] || TYPE_CONFIG.RESOURCE;
}

// ── GOOGLE MAPS INIT ──────────────────────────────────────────
function initMap() {
  map = new google.maps.Map(document.getElementById('map'), {
    center: { lat: 33.7490, lng: -84.3880 },
    zoom: 13,
    styles: mapStyle(),
    disableDefaultUI: true,
    zoomControl: true,
    zoomControlOptions: {
      position: google.maps.ControlPosition.RIGHT_BOTTOM
    }
  });
}

// ── SEARCH INPUT HANDLER ──────────────────────────────────────
function onZipInput() {
  const val = document.getElementById('searchInput').value.trim();
  const box = document.getElementById('suggestions');

  if (val.length === 0) { box.style.display = 'none'; return; }

  const matches = ZIP_SUGGESTIONS.filter(s =>
    s.zip.startsWith(val) || s.label.toLowerCase().includes(val)
  );

  if (!matches.length) { box.style.display = 'none'; return; }

  box.innerHTML = matches.map(s =>
    `<div class="sug-item" onclick="applySuggestion('${s.zip}', '${s.label}')">
      <span>📍</span>${s.label}
    </div>`
  ).join('');
  box.style.display = 'block';
}

function onZipKey(e) {
  if (e.key === 'Enter') doLookup();
  if (e.key === 'Escape') document.getElementById('suggestions').style.display = 'none';
}

function applySuggestion(zip, label) {
  document.getElementById('searchInput').value = zip;
  document.getElementById('suggestions').style.display = 'none';
  doLookup();
}

// ── MAIN LOOKUP ───────────────────────────────────────────────
async function doLookup() {
  document.getElementById('suggestions').style.display = 'none';
  const zip = document.getElementById('searchInput').value.trim();

  if (!/^\d{5}$/.test(zip)) {
    shakeInput();
    return;
  }

  currentZip = zip;
  showLoading(true);
  clearMarkers();

  try {
    const res = await fetch(`/api/lookup?zip=${zip}`);
    if (!res.ok) throw new Error('Server error');
    const data = await res.json();
    renderAll(data);
  } catch (err) {
    console.error(err);
    alert('Something went wrong. Is your Spring Boot server running?');
  } finally {
    showLoading(false);
  }
}

// ── RENDER EVERYTHING ─────────────────────────────────────────
function renderAll(data) {
  allResources = data.resources || [];

  // Show env strip
  document.getElementById('envStrip').style.display = 'grid';
  renderEnvStrip(data);

  // Update map center
  if (map) {
    map.setCenter({ lat: data.latitude, lng: data.longitude });
    map.setZoom(14);
  }

  // Update you-badge and area label
  document.getElementById('youLbl').textContent = data.neighborhood;
  document.getElementById('sbArea').textContent = 'Near ' + data.neighborhood;

  // Render resources
  renderResources();

  // Show live feed section and start polling
  document.getElementById('liveSection').style.display = 'block';
  startLivePolling(data.zipCode);
}

// ── ENV STRIP ─────────────────────────────────────────────────
function renderEnvStrip(data) {
  const env = data.environment || {};

  // AQI
  const aqiClass = env.aqiValue <= 50 ? 'env-good'
    : env.aqiValue <= 100 ? 'env-mod' : 'env-bad';
  document.getElementById('aqiVal').textContent  = `${env.aqiValue} — ${env.aqiCategory}`;
  document.getElementById('aqiVal').className    = `env-val ${aqiClass}`;
  document.getElementById('aqiDesc').textContent = env.safeToWalkOutdoors
    ? 'Safe to walk today' : 'Consider transit today';
  document.getElementById('aqiDesc').className   = `env-desc ${aqiClass}`;

  // Water
  const wqiClass = env.wqiStatus === 'SAFE' ? 'env-good'
    : env.wqiStatus === 'MODERATE' ? 'env-mod' : 'env-bad';
  document.getElementById('wqiVal').textContent  = env.wqiStatus === 'SAFE'
    ? 'No violations' : `${env.waterViolationCount} violation(s)`;
  document.getElementById('wqiVal').className    = `env-val ${wqiClass}`;
  document.getElementById('wqiDesc').textContent = env.wqiStatus === 'SAFE'
    ? 'Meets EPA standards' : env.waterViolationDetail;
  document.getElementById('wqiDesc').className   = `env-desc ${wqiClass}`;

  // Food desert
  const dClass = data.foodDesert
    ? (data.foodDesertSeverity === 'SEVERE' ? 'env-bad' : 'env-mod')
    : 'env-good';
  document.getElementById('desertVal').textContent  = data.foodDesert
    ? (data.foodDesertSeverity === 'SEVERE' ? 'Severe Desert' : 'Low Access')
    : 'Adequate Access';
  document.getElementById('desertVal').className    = `env-val ${dClass}`;
  document.getElementById('desertDesc').textContent = data.foodDesert
    ? `Nearest store ${data.nearestGroceryMiles.toFixed(1)} mi away`
    : 'Grocery access within 0.5 mi';
  document.getElementById('desertDesc').className   = `env-desc ${dClass}`;

  // AI summary
  const aiEl = document.getElementById('aiText');
  if (data.aiSummary) {
    aiEl.textContent = data.aiSummary;
  } else {
    aiEl.innerHTML = '<span class="ai-dots"><span></span><span></span><span></span></span>';
  }
}

function renderResources() {
  const visible = allResources.filter(r => {
    if (currentFilter === 'all') return true;

    if (currentFilter === 'PANTRY')
      return r.type === 'PANTRY'
          || r.type === 'HOT_MEAL'
          || r.type === 'FRIDGE'
          || r.type === 'GARDEN'
          || r.type === 'RESOURCE';

    if (currentFilter === 'COOLING')
      return r.type === 'COOLING'
          || r.type === 'WARMING';

    if (currentFilter === 'SNAP')    return r.type === 'SNAP';
    if (currentFilter === 'SHELTER') return r.type === 'SHELTER';
    if (currentFilter === 'WATER')   return r.type === 'WATER';

    return false;
  });

  document.getElementById('sbCount').textContent =
      visible.length + ' location' + (visible.length !== 1 ? 's' : '') + ' shown';

  document.getElementById('emptyState').style.display =
      allResources.length === 0 ? 'block' : 'none';

  renderList(visible);
  renderPins(visible);
}

function renderList(resources) {
  const el = document.getElementById('rlist');
  el.innerHTML = '';

  if (!resources.length) {
    el.innerHTML = `<div class="empty-state">
      <div class="empty-icon">🔍</div>
      <div class="empty-text">No resources match this filter</div>
      <div class="empty-sub">Try "Show All" to see everything nearby</div>
    </div>`;
    return;
  }

  resources.forEach(r => {
    const cfg = typeConfig(r.type);
    const card = document.createElement('div');
    card.className = 'rcard' + (selectedId === r.name ? ' sel' : '');
    card.id = 'card-' + encodeURIComponent(r.name);
    card.setAttribute('role', 'listitem');
    card.setAttribute('tabindex', '0');
    card.innerHTML = `
      <div class="rc-row">
        <div class="rc-ico" style="background:${cfg.color}28">
          <i class="fa-solid ${cfg.icon}" style="color:${cfg.color};font-size:20px"></i>
        </div>
        <div class="rc-body">
          <div class="rc-name">${r.name}</div>
          <div class="rc-type">${cfg.label}</div>
          <div class="rc-meta">
            <span class="badge ${r.openNow ? 'open-b' : 'closed-b'}">
              ${r.openNow ? '✓ Open Now' : '✗ Closed'}
            </span>
            <span class="rc-hrs">${r.hours || ''}</span>
          </div>
          ${r.notes ? `<div class="rc-notes">${r.notes}</div>` : ''}
        </div>
        <div class="rc-dist">${r.distanceMiles.toFixed(1)} mi</div>
      </div>`;

    card.addEventListener('click', () => selectResource(r));
    card.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') selectResource(r);
    });
    el.appendChild(card);
  });
}

function renderPins(resources) {
  clearMarkers();
  if (!map) return;

  resources.forEach(r => {
    const cfg = typeConfig(r.type);
    const marker = new google.maps.Marker({
      position: { lat: r.latitude, lng: r.longitude },
      map,
      title: r.name,
      icon: {
        path: google.maps.SymbolPath.CIRCLE,
        scale: 11,
        fillColor: cfg.color,
        fillOpacity: 1,
        strokeColor: '#ffffff',
        strokeWeight: 2.5,
      }
    });

    const infoWindow = new google.maps.InfoWindow({
      content: `<div style="font-family:'Nunito',sans-serif;padding:4px 2px;min-width:160px">
        <strong style="font-size:13px;color:#0a1628">${r.name}</strong>
        <p style="font-size:12px;color:#6b7a94;margin:4px 0 0">${cfg.label} · ${r.distanceMiles.toFixed(1)} mi</p>
        <p style="font-size:12px;color:#6b7a94;margin:2px 0 0">${r.hours || ''}</p>
      </div>`
    });

    marker.addListener('click', () => {
      infoWindow.open(map, marker);
      selectResource(r);
    });

    markers.push(marker);
  });
}

function clearMarkers() {
  markers.forEach(m => m.setMap(null));
  markers = [];
}

// ── SELECT RESOURCE ───────────────────────────────────────────
function selectResource(r) {
  selectedId = r.name;

  // Highlight card
  document.querySelectorAll('.rcard').forEach(c => c.classList.remove('sel'));
  const card = document.getElementById('card-' + encodeURIComponent(r.name));
  if (card) {
    card.classList.add('sel');
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  // Update detail panel
  const cfg = typeConfig(r.type);
  document.getElementById('detName').textContent = r.name;
  document.getElementById('detAddr').textContent =
    (r.address || '') + (r.phone ? ' · ' + r.phone : '');
  document.getElementById('detBtns').style.display = 'flex';

  // Directions button
  document.getElementById('dirBtn').onclick = () => {
    window.open(
      `https://www.google.com/maps/dir/?api=1&destination=${r.latitude},${r.longitude}`,
      '_blank'
    );
  };

  // Call button
  const callBtn = document.getElementById('callBtn');
  if (r.phone) {
    callBtn.style.display = 'flex';
    callBtn.onclick = () => { window.location.href = 'tel:' + r.phone; };
  } else {
    callBtn.style.display = 'none';
  }

  // Pan map
  if (map) {
    map.panTo({ lat: r.latitude, lng: r.longitude });
    map.setZoom(16);
  }

  if (!sidebarOpen) toggleSidebar();
}

// ── FILTER ────────────────────────────────────────────────────
function setFilter(type, btn) {
  document.querySelectorAll('.fbtn').forEach(b => {
    b.classList.remove('active');
    b.setAttribute('aria-pressed', 'false');
  });
  btn.classList.add('active');
  btn.setAttribute('aria-pressed', 'true');
  currentFilter = type;
  selectedId = null;
  resetDetailPanel();
  renderResources();
}

// ── LIVE FEED ─────────────────────────────────────────────────
function startLivePolling(zip) {
  if (liveInterval) clearInterval(liveInterval);
  loadLivePosts(zip);
  liveInterval = setInterval(() => loadLivePosts(zip), 60000);
}

async function loadLivePosts(zip) {
  try {
    const res = await fetch(`/api/live?zip=${zip}`);
    const posts = await res.json();
    renderLivePosts(posts);
    addLivePinsToMap(posts);
  } catch (e) {
    console.error('Live feed error:', e);
  }
}

function renderLivePosts(posts) {
  const list = document.getElementById('liveList');
  if (!posts.length) {
    list.innerHTML = '<div style="font-size:12px;font-weight:700;color:#8a96a8;padding:4px 0">No live posts right now. Be the first to share!</div>';
    return;
  }

  const icons = { SURPLUS: '🥖', RESTOCK: '✅', EMPTY: '⚠️', POPUP: '🍽️' };

  list.innerHTML = posts.map(p => {
    const msLeft = new Date(p.expiresAt) - Date.now();
    const minsLeft = Math.max(0, Math.floor(msLeft / 60000));
    const urgentClass = minsLeft < 30 ? 'urgent' : '';

    return `<div class="live-card type-${p.postType}">
      <button class="live-close" onclick="closePost(${p.id})" title="Mark as gone">✕</button>
      <div class="live-org">${icons[p.postType] || '📌'} ${p.orgName} · via ${p.source}</div>
      <div class="live-desc">${p.description}</div>
      <div class="live-meta">
        <span>${p.zipCode}</span>
        <span class="live-exp ${urgentClass}">Expires in ${minsLeft} min</span>
      </div>
    </div>`;
  }).join('');
}

function addLivePinsToMap(posts) {
  if (!map) return;
  posts.forEach(p => {
    if (!p.latitude || !p.longitude) return;
    const marker = new google.maps.Marker({
      position: { lat: p.latitude, lng: p.longitude },
      map,
      title: p.description,
      icon: {
        path: google.maps.SymbolPath.CIRCLE,
        scale: 12,
        fillColor: '#D4860B',
        fillOpacity: 0.9,
        strokeColor: '#ffffff',
        strokeWeight: 2.5,
      }
    });

    new google.maps.InfoWindow({
      content: `<div style="font-family:'Nunito',sans-serif;padding:4px 2px">
        <strong style="font-size:13px">🥖 ${p.orgName}</strong>
        <p style="font-size:12px;color:#6b7a94;margin:4px 0 0">${p.description}</p>
      </div>`
    }).open(map, marker);

    markers.push(marker);
  });
}

function togglePostForm() {
  const form = document.getElementById('postForm');
  form.style.display = form.style.display === 'none' ? 'block' : 'none';
}

async function submitPost() {
  const orgName    = document.getElementById('postOrg').value.trim() || 'Anonymous';
  const description = document.getElementById('postDesc').value.trim();
  const zipCode    = document.getElementById('postZip').value.trim() || currentZip;
  const expiryHours = document.getElementById('postExpiry').value;

  if (!description || !zipCode || !/^\d{5}$/.test(zipCode)) {
    alert('Please fill in what is available and a valid ZIP code.');
    return;
  }

  try {
    await fetch('/api/live', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ orgName, description, zipCode, expiryHours })
    });
    togglePostForm();
    document.getElementById('postOrg').value  = '';
    document.getElementById('postDesc').value = '';
    loadLivePosts(zipCode);
  } catch (e) {
    alert('Could not submit post. Try again.');
  }
}

async function closePost(id) {
  await fetch(`/api/live/${id}`, { method: 'DELETE' });
  if (currentZip) loadLivePosts(currentZip);
}

// ── SIDEBAR TOGGLE ────────────────────────────────────────────
function toggleSidebar() {
  const sb  = document.getElementById('sidebar');
  const btn = document.getElementById('toggleBtn');
  const arr = document.getElementById('toggleArrow');
  sidebarOpen = !sidebarOpen;
  btn.setAttribute('aria-expanded', sidebarOpen);
  if (sidebarOpen) {
    sb.classList.remove('collapsed');
    btn.classList.add('open');
    arr.className = 'arrow-r';
  } else {
    sb.classList.add('collapsed');
    btn.classList.remove('open');
    arr.className = 'arrow-l';
  }
}

// ── CLEAR ─────────────────────────────────────────────────────
function clearAll() {
  document.getElementById('searchInput').value = '';
  document.getElementById('suggestions').style.display = 'none';
  document.getElementById('envStrip').style.display = 'none';
  document.getElementById('liveSection').style.display = 'none';
  document.getElementById('rlist').innerHTML = '';
  document.getElementById('emptyState').style.display = 'block';
  document.getElementById('sbArea').textContent = 'Enter a ZIP code to search';
  document.getElementById('sbCount').textContent = '';
  document.getElementById('youLbl').textContent = 'Enter a ZIP code above';
  resetDetailPanel();
  clearMarkers();
  allResources = [];
  currentZip = null;
  if (liveInterval) { clearInterval(liveInterval); liveInterval = null; }
  if (map) { map.setCenter({ lat: 33.7490, lng: -84.3880 }); map.setZoom(13); }
}

// ── HELPERS ───────────────────────────────────────────────────
function resetDetailPanel() {
  document.getElementById('detName').textContent  = '👆 Tap any location to see details';
  document.getElementById('detAddr').textContent  = 'Select a pin on the map or an item in the list';
  document.getElementById('detBtns').style.display = 'none';
  selectedId = null;
}

function showLoading(show) {
  document.getElementById('loadingOverlay').style.display = show ? 'flex' : 'none';
}

function shakeInput() {
  const input = document.getElementById('searchInput');
  input.style.borderColor = '#C0392B';
  input.animate([
    { transform: 'translateX(0)'  },
    { transform: 'translateX(-8px)' },
    { transform: 'translateX(8px)'  },
    { transform: 'translateX(-5px)' },
    { transform: 'translateX(0)'  },
  ], { duration: 300 }).onfinish = () => { input.style.borderColor = ''; };
}

function updateClock() {
  const n  = new Date();
  let h    = n.getHours(), m = n.getMinutes();
  const ap = h >= 12 ? 'PM' : 'AM';
  h = h % 12 || 12;
  document.getElementById('clock').textContent =
    h + ':' + String(m).padStart(2, '0') + ' ' + ap;

  const days   = ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
  const months = ['January','February','March','April','May','June','July','August','September','October','November','December'];
  document.getElementById('hdate').textContent =
    days[n.getDay()] + ', ' + months[n.getMonth()] + ' ' + n.getDate() + ', ' + n.getFullYear();
}

// Close suggestions when clicking outside
document.addEventListener('click', e => {
  if (!document.getElementById('searchWrap').contains(e.target))
    document.getElementById('suggestions').style.display = 'none';
});

// ── MAP STYLE ─────────────────────────────────────────────────
function mapStyle() {
  return [
    { featureType: 'all',       elementType: 'geometry',       stylers: [{ color: '#162840' }] },
    { featureType: 'road',      elementType: 'geometry',       stylers: [{ color: '#1d3455' }] },
    { featureType: 'road',      elementType: 'geometry.stroke',stylers: [{ color: '#122040' }] },
    { featureType: 'road',      elementType: 'labels.text.fill',stylers: [{ color: '#4a6888' }] },
    { featureType: 'water',     elementType: 'geometry',       stylers: [{ color: '#0e2240' }] },
    { featureType: 'poi.park',  elementType: 'geometry',       stylers: [{ color: '#1a3d1a' }] },
    { featureType: 'poi',       elementType: 'labels',         stylers: [{ visibility: 'off' }] },
    { featureType: 'transit',   elementType: 'geometry',       stylers: [{ color: '#1a3355' }] },
    { elementType: 'labels.text.fill',   stylers: [{ color: '#6a8aaa' }] },
    { elementType: 'labels.text.stroke', stylers: [{ color: '#162840' }] },
  ];
}

// ── INIT ──────────────────────────────────────────────────────
updateClock();
setInterval(updateClock, 30000);
