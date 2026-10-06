import { closeLivePost, createLivePost, getLivePosts, lookup } from './api.js';
import './style.css';

const state = {
  zip: null,
  resources: [],
  posts: [],
  filter: 'all',
  map: null,
  location: null,
  markers: [],
  pollTimer: null,
  toastTimer: null,
};

const $ = (selector) => document.querySelector(selector);
const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, (character) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
})[character]);

function showToast(message, isError = false) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.classList.toggle('error', isError);
  toast.classList.add('visible');
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => toast.classList.remove('visible'), 3600);
}

function setBusy(busy) {
  const button = $('#lookup-form button[type="submit"]');
  button.disabled = busy;
  button.innerHTML = busy ? 'Looking nearby…' : 'Find resources <span aria-hidden="true">↗</span>';
}

async function runLookup(zip) {
  if (!/^\d{5}$/.test(zip)) {
    showToast('Enter a valid 5-digit ZIP code.', true);
    $('#zip-input').focus();
    return;
  }

  setBusy(true);
  try {
    const data = await lookup(zip);
    state.zip = zip;
    state.location = { latitude: data.latitude, longitude: data.longitude };
    state.resources = data.resources || [];
    $('#results-section').hidden = false;
    $('#welcome').hidden = true;
    $('#area-title').textContent = data.neighborhood || `ZIP ${zip}`;
    renderEnvironment(data);
    renderSummary(data.aiSummary);
    renderResources();
    renderMap(data.latitude, data.longitude);
    await refreshLivePosts();
    startPolling();
    $('#results-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    console.error('Lookup failed:', error);
    showToast('Could not load resources. Check that the backend is running and try again.', true);
  } finally {
    setBusy(false);
  }
}

function renderEnvironment(data) {
  const environment = data.environment || {};
  const metrics = [
    {
      label: 'Food access',
      value: (data.foodDesert ?? data.isFoodDesert) ? `${data.foodDesertSeverity || 'Limited'} access` : 'Food resources nearby',
      detail: (data.foodDesert ?? data.isFoodDesert)
        ? `Nearest SNAP store: ${Number(data.nearestGroceryMiles || 0).toFixed(1)} mi`
        : 'Explore the nearby resource list',
      tone: data.isFoodDesert ? 'amber' : 'green',
    },
    {
      label: 'Air quality',
      value: `AQI ${environment.aqiValue ?? '—'} · ${environment.aqiCategory || 'Unavailable'}`,
      detail: environment.safeToWalkOutdoors ? 'Generally suitable for walking' : 'Consider transit or check current conditions',
      tone: Number(environment.aqiValue) > 100 ? 'amber' : 'green',
    },
    {
      label: 'Water quality',
      value: environment.wqiStatus === 'SAFE' ? 'No violations reported' : (environment.wqiStatus || 'Unavailable'),
      detail: environment.waterViolationDetail || 'Check local guidance for current information',
      tone: environment.wqiStatus === 'SAFE' ? 'green' : 'blue',
    },
  ];

  $('#environment-grid').innerHTML = metrics.map((metric) => `
    <article class="metric-card ${metric.tone}">
      <span class="metric-label">${escapeHtml(metric.label)}</span>
      <strong>${escapeHtml(metric.value)}</strong>
      <small>${escapeHtml(metric.detail)}</small>
    </article>`).join('');
}

function renderSummary(summary) {
  const card = $('#summary-card');
  card.hidden = !summary;
  $('#ai-summary').textContent = summary || '';
}

function matchesFilter(resource) {
  if (state.filter === 'all') return true;
  if (state.filter === 'food') return ['PANTRY', 'HOT_MEAL', 'FRIDGE', 'GARDEN', 'RESOURCE'].includes(resource.type);
  if (state.filter === 'centers') return ['COOLING', 'WARMING'].includes(resource.type);
  return resource.type === state.filter;
}

function renderResources() {
  const resources = state.resources.filter(matchesFilter);
  $('#resource-count').textContent = String(resources.length);
  if (!resources.length) {
    $('#resource-list').innerHTML = '<div class="empty-list">No locations match this filter. Try another category.</div>';
    renderMapMarkers();
    return;
  }

  $('#resource-list').innerHTML = resources.map((resource, index) => `
    <article class="resource-card">
      <div class="resource-symbol" aria-hidden="true">${resourceIcon(resource.type)}</div>
      <div class="resource-info">
        <div class="resource-title-row"><h3>${escapeHtml(resource.name || 'Community resource')}</h3><span>${Number(resource.distanceMiles || 0).toFixed(1)} mi</span></div>
        <p class="resource-type">${escapeHtml(resource.type || 'RESOURCE').replaceAll('_', ' ')}</p>
        <p class="resource-address">${escapeHtml(resource.address || 'Address unavailable')}</p>
        <div class="resource-meta"><span class="open-tag ${resource.openNow ? 'is-open' : ''}">${resource.openNow ? 'Open now' : 'Check hours'}</span><span>${escapeHtml(resource.hours || 'Hours unavailable')}</span></div>
        ${resource.notes ? `<p class="resource-notes">${escapeHtml(resource.notes)}</p>` : ''}
        <div class="resource-actions"><a href="https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(`${resource.latitude},${resource.longitude}`)}" target="_blank" rel="noopener noreferrer">Directions ↗</a>${resource.phone ? `<a href="tel:${escapeHtml(resource.phone)}">Call</a>` : ''}</div>
      </div>
    </article>`).join('');

  renderMapMarkers();
}

function resourceIcon(type) {
  return ({ PANTRY: '🥕', HOT_MEAL: '🍲', SNAP: '✳', SHELTER: '⌂', WATER: '♧', FRIDGE: '❄', GARDEN: '🌱', COOLING: '❄', WARMING: '♨' })[type] || '＋';
}

function renderMap(latitude, longitude) {
  if (!state.map && window.google?.maps) {
    $('#map').querySelector('.map-placeholder')?.remove();
    state.map = new google.maps.Map($('#map'), {
      center: { lat: Number(latitude), lng: Number(longitude) },
      zoom: 13,
      mapTypeControl: false,
      streetViewControl: false,
      fullscreenControl: false,
    });
  }
  if (state.map) state.map.setCenter({ lat: Number(latitude), lng: Number(longitude) });
  renderMapMarkers();
}

function renderMapMarkers() {
  state.markers.forEach((marker) => marker.setMap(null));
  state.markers = [];
  if (!state.map || !window.google?.maps) return;

  state.resources.filter(matchesFilter).forEach((resource) => {
    if (!Number.isFinite(Number(resource.latitude)) || !Number.isFinite(Number(resource.longitude))) return;
    const marker = new google.maps.Marker({
      map: state.map,
      position: { lat: Number(resource.latitude), lng: Number(resource.longitude) },
      title: resource.name,
    });
    const info = new google.maps.InfoWindow({ content: `<div><strong>${escapeHtml(resource.name)}</strong><p>${escapeHtml(resource.type)} · ${Number(resource.distanceMiles || 0).toFixed(1)} mi</p></div>` });
    marker.addListener('click', () => info.open({ map: state.map, anchor: marker }));
    state.markers.push(marker);
  });
}

function startPolling() {
  clearInterval(state.pollTimer);
  state.pollTimer = setInterval(refreshLivePosts, 60_000);
}

async function refreshLivePosts() {
  if (!state.zip) return;
  try {
    state.posts = await getLivePosts(state.zip);
    renderLivePosts();
  } catch (error) {
    console.error('Live feed failed:', error);
    $('#live-list').innerHTML = '<p class="muted-message">Community updates are temporarily unavailable.</p>';
  }
}

function renderLivePosts() {
  const list = $('#live-list');
  if (!state.posts.length) {
    list.innerHTML = '<p class="muted-message">No active posts for this ZIP yet. Share an update to help a neighbor.</p>';
    return;
  }
  list.innerHTML = state.posts.map((post) => {
    const minutes = Math.max(0, Math.floor((new Date(post.expiresAt).getTime() - Date.now()) / 60_000));
    return `<article class="live-card"><div><span class="live-type">${escapeHtml(post.postType || 'UPDATE')}</span><h3>${escapeHtml(post.orgName || 'Community member')}</h3><p>${escapeHtml(post.description)}</p><small>ZIP ${escapeHtml(post.zipCode)} · expires in ${minutes} min · via ${escapeHtml(post.source || 'community')}</small></div><button class="close-post" type="button" data-post-id="${escapeHtml(post.id)}" aria-label="Close post">×</button></article>`;
  }).join('');
  list.querySelectorAll('[data-post-id]').forEach((button) => {
    button.addEventListener('click', async () => {
      try {
        await closeLivePost(button.dataset.postId);
        await refreshLivePosts();
        showToast('Post closed. Thanks for keeping the feed current.');
      } catch (error) {
        showToast('Could not close that post.', true);
      }
    });
  });
}

function clearSearch() {
  clearInterval(state.pollTimer);
  state.pollTimer = null;
  state.zip = null;
  state.resources = [];
  state.posts = [];
  state.markers.forEach((marker) => marker.setMap(null));
  state.markers = [];
  $('#results-section').hidden = true;
  $('#welcome').hidden = false;
  $('#zip-input').value = '';
  $('#zip-input').focus();
}

$('#lookup-form').addEventListener('submit', (event) => {
  event.preventDefault();
  runLookup($('#zip-input').value.trim());
});

$('#clear-button').addEventListener('click', clearSearch);
$('#post-toggle').addEventListener('click', () => {
  $('#post-form').hidden = !$('#post-form').hidden;
});

$('#post-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const zipCode = String(form.get('zipCode')).trim() || state.zip;
  const description = String(form.get('description')).trim();
  if (!/^\d{5}$/.test(zipCode) || !description) {
    showToast('Enter a valid ZIP and describe what is available.', true);
    return;
  }
  try {
    await createLivePost({
      orgName: String(form.get('orgName')).trim() || 'Anonymous',
      description,
      zipCode,
      expiryHours: String(form.get('expiryHours')),
    });
    event.currentTarget.reset();
    $('#post-form').hidden = true;
    await refreshLivePosts();
    showToast('Your update is now in the community feed.');
  } catch (error) {
    showToast('Could not publish your update. Please try again.', true);
  }
});

document.querySelectorAll('.filter-button').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelector('.filter-button.active')?.classList.remove('active');
    button.classList.add('active');
    state.filter = button.dataset.filter;
    renderResources();
  });
});

const mapsKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim();
if (mapsKey) {
  window.streetAidMapsReady = () => {
    if (state.zip && state.location) {
      renderMap(state.location.latitude, state.location.longitude);
    }
  };
  const mapsScript = document.createElement('script');
  mapsScript.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(mapsKey)}&callback=streetAidMapsReady`;
  mapsScript.async = true;
  mapsScript.defer = true;
  mapsScript.onerror = () => showToast('Google Maps could not load; resource results remain available.', true);
  document.head.append(mapsScript);
}
