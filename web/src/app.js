import {EventDslEditor, makeDescriptor} from './event-dsl-editor.js';
import {actionCount} from './event-dsl-model.js';
import { GraphModel, clone } from './model.js';
import { editableCommands, replaceEditableCommands, formatCommands, formatWorkflowYaml } from './command-yaml.js';
import { validDuration, validTimeKey } from './spawner.js';
import { splitVector, joinVector } from './vector.js';
import { highlightYaml } from './yaml-highlight.js';
const $ = selector => document.querySelector(selector);
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
const colors = { default:'#a898f4', tick:'#edc482', loot:'#63dec0', block:'#ff9da4', dh:'#84aaff', wg:'#a1c8ff' };
const color = type => type === 'default:commands' ? '#8996aa' : colors[String(type).split(':')[0]] ?? '#9aaac4';
let model, schemas = {}, generatorSchemas = {}, spawnerSchemas = {}, selected, selectedEdge, linking, graphView, drag, invalidFields = new Map(), catalogue = [], editorTab = 'graph', generatorError = '', spawnerError = '';
const nodeScroll = new Map();
const nodeDrafts = new Map();
const pendingCommandEdits = new Map();
let graphListeners;
let copyingYaml = false;
const url = new URL(location.href), segments = location.pathname.split('/').filter(Boolean);
let token = url.searchParams.get('token') ?? (segments.length >= 2 ? segments[0] : sessionStorage.getItem('baf-token')) ?? '';
let apiOrigin = url.searchParams.get('api') ?? sessionStorage.getItem('baf-api') ?? location.origin;
let toastTimer;
function toast(message, error = false) { $('#toast').textContent = message; $('#toast').className = `show${error ? ' error' : ''}`; clearTimeout(toastTimer); toastTimer = setTimeout(() => $('#toast').className = '', error ? 6500 : 3000); }
function safe(action) { try { action(); } catch (error) { toast(error.message, true); } }
async function api(route, data) {
  if (!token) throw new Error('Укажите токен подключения из /baf web');
  const response = await fetch(`${apiOrigin}/api/${encodeURIComponent(token)}/${route}`, { method: data ? 'POST' : 'GET', cache:'no-store',
    headers: data ? { 'Content-Type':'application/json' } : {}, body: data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(response.status === 404 ? 'Подключение недоступно. Получите новую ссылку через /baf web.' : `Ошибка HTTP ${response.status}`);
  const body = await response.json();
  if (!body.ok) { const error = new Error(body.error ?? 'Ошибка сервера'); error.code = body.code; throw error; }
  $('#connection-label').textContent = 'Подключено'; return body.data;
}
async function dialog(content, confirmText = 'Продолжить') {
  $('#dialog-content').innerHTML = content; $('#dialog-confirm').textContent = confirmText;
  $('#dialog').returnValue = '';
  $('#dialog').showModal();
  return new Promise(resolve => $('#dialog').onclose = () => resolve($('#dialog').returnValue === 'ok' ? new FormData($('#dialog-form')) : null));
}
async function configure() {
  if (!leave()) return;
  const result = await dialog(`<h2>Подключение к ноде</h2><p>Откройте ссылку из команды <code>/baf web</code> или укажите её токен. API и статика должны быть доступны на одном origin либо через настроенный прокси nginx.</p><div class="field"><label>Адрес BWebTunnel</label><input name="origin" value="${escape(apiOrigin)}" required type="url"></div><div class="field"><label>Токен</label><input name="token" value="${escape(token)}" required autocomplete="off"></div>`, 'Подключиться');
  if (!result) return;
  safe(() => {
    const origin = new URL(result.get('origin'));
    if (!['http:', 'https:'].includes(origin.protocol)) throw new Error('Нужен HTTP или HTTPS адрес');
    apiOrigin = origin.origin; token = String(result.get('token')).trim();
    sessionStorage.setItem('baf-token', token); sessionStorage.setItem('baf-api', apiOrigin);
    loadHome();
  });
}
$('#connect').onclick = configure;
function leave() { return !model?.dirty || confirm('Есть несохранённые изменения. Покинуть редактор?'); }
$('.brand').onclick = event => { event.preventDefault(); if (leave()) { history.replaceState(null, '', '#'); loadHome(); } };
window.addEventListener('beforeunload', event => { if (model?.dirty) { event.preventDefault(); event.returnValue = ''; } });
async function loadHome() {
  model = null; selected = null; selectedEdge = null; linking = null; generatorError = ''; spawnerError = ''; invalidFields.clear(); nodeDrafts.clear();
  $('#breadcrumb').textContent = 'Все аирдропы';
  $('#app').innerHTML = '<div class="loading"><span class="spinner"></span>Загрузка аирдропов…</div>';
  try {
    [catalogue, schemas, generatorSchemas, spawnerSchemas] = await Promise.all([api('airdrops'), api('schemas'), api('generator-schemas'), api('spawner-schemas')]);
    $('#app').innerHTML = `<section class="page"><div class="page-heading"><div><div class="eyebrow">СЦЕНАРИИ ВАШЕГО СЕРВЕРА</div><h1>Аирдропы</h1><p>Настройте события, таймеры и связи между нодами.<br>Все сценарии вашего сервера — в одном месте.</p></div><button id="reload-home" class="secondary">↻ Обновить список</button></div><div class="stats"><div class="stat"><b>${catalogue.length}</b><span>аирдропов</span></div><div class="stat"><b>${catalogue.reduce((n, item) => n + (item.nodeCount ?? 0), 0)}</b><span>нод в сценариях</span></div><div class="stat"><b>${Object.keys(schemas).length}</b><span>типов компонентов</span></div></div><div class="searchbar"><input id="search" placeholder="Поиск по имени, файлу или миру…" aria-label="Поиск аирдропов"><span>Конфиги из папки workflow</span></div><div class="cards" id="cards"></div></section>`;
    $('#reload-home').onclick = loadHome; $('#search').oninput = renderCards; renderCards();
  } catch (error) {
    $('#connection-label').textContent = 'Нет подключения';
    $('#app').innerHTML = `<section class="page"><div class="eyebrow">BAIRDROPFLOW</div><h1>Подключитесь к серверу</h1><p class="muted">${escape(error.message)}</p><button id="setup" class="primary">Настроить подключение</button></section>`;
    $('#setup').onclick = configure;
  }
}
function renderCards() {
  const query = $('#search').value.toLowerCase();
  const items = catalogue.filter(item => `${item.id} ${item.key} ${item.world}`.toLowerCase().includes(query));
  $('#cards').innerHTML = items.map(item => `<article class="card"><div class="card-top"><div class="air-icon">◇</div><span class="badge ${item.valid ? '' : 'error'}">${item.valid ? 'Готов к запуску' : 'Есть ошибки'}</span></div><div class="card-body"><h2>${escape(item.id ?? item.key)}</h2><code>${escape(item.key)}</code><div class="card-info"><span>Мир<b>${escape(item.world ?? '—')}</b></span><span>Компоненты<b>${item.nodeCount ?? 0} нод</b></span></div>${item.error ? `<details class="error-details"><summary>Ошибка конфигурации</summary>${escape(item.error)}</details>` : ''}</div><div class="card-foot"><span>Нодовый сценарий</span><button data-open="${escape(item.key)}">Редактировать ↗</button></div></article>`).join('') || '<div class="empty">Аирдропы не найдены</div>';
  $('#cards').querySelectorAll('[data-open]').forEach(button => button.onclick = () => openEditor(button.dataset.open));
}
async function openEditor(key) {
  if (!leave()) return;
  $('#app').innerHTML = '<div class="loading"><span class="spinner"></span>Загрузка сценария…</div>';
  try {
    const workflowDocument = await api(`airdrop?key=${encodeURIComponent(key)}`);
    if (!Object.keys(schemas).length) schemas = await api('schemas');
    if (!Object.keys(generatorSchemas).length) generatorSchemas = await api('generator-schemas');
    if (!Object.keys(spawnerSchemas).length) spawnerSchemas = await api('spawner-schemas');
    model = new GraphModel(workflowDocument); selected = null; selectedEdge = null; linking = null; generatorError = ''; spawnerError = ''; invalidFields.clear(); nodeScroll.clear(); nodeDrafts.clear();
    history.replaceState(null, '', `#edit/${encodeURIComponent(key)}`);
    $('#breadcrumb').textContent = `Аирдропы / ${workflowDocument.config.id ?? key}`;
    $('#app').innerHTML = `<section class="workspace"><div class="workspace-toolbar"><button id="back" class="secondary">←</button><button id="tab-graph" class="editor-tab">Сценарий</button><button id="tab-generator" class="editor-tab">Генератор локации</button><button id="tab-spawner" class="editor-tab">Спавнер и параметры</button><button id="add-node">+ Нода</button><button id="undo" title="Ctrl+Z">↶</button><button id="redo" title="Ctrl+Shift+Z">↷</button><button id="settings" class="optional">Настройки аирдропа</button><div class="spacer"></div><button id="reload-editor" title="Перезагрузить с диска">↻</button><button id="copy-yaml" class="secondary" title="Скопировать текущий YAML-файл без сохранения на сервере">Скопировать YAML</button><button id="save" class="primary">Сохранить</button></div><aside class="library"><div class="section-label">Компоненты</div><div id="node-library"></div><p>Добавьте компонент, затем потяните от выхода события к нужному входу другой ноды.</p><div class="section-label" style="margin-top:28px">Ноды сценария</div><div id="outline"></div></aside><div class="graph-shell"><svg class="graph" tabindex="0" aria-label="Схема аирдропа"></svg><div class="graph-hint" id="graph-hint">Перетаскивайте ноды · Колесо — масштаб · Фон — перемещение</div><div class="graph-controls"><button id="zoom-out" aria-label="Уменьшить">−</button><span id="zoom-value">100%</span><button id="zoom-in" aria-label="Увеличить">+</button><button id="fit" title="Показать весь граф">⤢</button></div></div><aside class="inspector" id="inspector"></aside><main class="generator-panel" id="generator-panel"></main><main class="spawner-panel" id="spawner-panel"></main><footer class="workspace-status"><span id="node-count"></span><span id="edge-count"></span><span id="warnings"></span><span id="save-state"></span></footer></section>`;
    $('#node-library').innerHTML = Object.entries(schemas).map(([type, schema]) => `<button data-type="${escape(type)}" style="--node-color:${color(type)}">${escape(schema.title)}<small>${escape(type)}</small></button>`).join('');
    $('#node-library').querySelectorAll('button').forEach(button => button.onclick = () => addNode(button.dataset.type));
    $('#back').onclick = () => { if (leave()) { history.replaceState(null, '', '#'); loadHome(); } };
    $('#add-node').onclick = () => addNode(); $('#settings').onclick = editSettings;
    $('#tab-graph').onclick = () => switchTab('graph'); $('#tab-generator').onclick = () => switchTab('generator'); $('#tab-spawner').onclick = () => switchTab('spawner');
    $('#save').onclick = save; $('#copy-yaml').onclick = copyYaml;
    $('#copy-yaml').onpointerdown = event => event.preventDefault();
    $('#reload-editor').onclick = () => openEditor(model.document.key);
    $('#undo').onclick = () => { model.undo(); generatorError = ''; spawnerError = ''; invalidFields.clear(); nodeDrafts.clear(); refresh(); }; $('#redo').onclick = () => { model.redo(); generatorError = ''; spawnerError = ''; invalidFields.clear(); nodeDrafts.clear(); refresh(); };
    $('#zoom-in').onclick = () => zoomBy(1.15); $('#zoom-out').onclick = () => zoomBy(1 / 1.15); $('#fit').onclick = fit;
    const library=$('#node-library');
    { const b=document.createElement('button'); b.textContent='Заметка · Note'; b.style.setProperty('--node-color','#edc482'); b.onclick=()=>quickAdd('web:note',{x:((($('.graph-shell').clientWidth/2)-graphView.x)/graphView.zoom),y:((($('.graph-shell').clientHeight/2)-graphView.y)/graphView.zoom)}); library.append(b); }
    graphView = clone(model.metadata.viewport); setupGraph(); switchTab('graph'); refresh();
    if (!workflowDocument.metadata?.viewport) fit(false);
    if (workflowDocument.validationError) toast(`Конфиг содержит ошибки. Исправьте их перед сохранением.\n${workflowDocument.validationError}`, true);
  } catch (error) { toast(error.message, true); loadHome(); }
}
function schemaFor(node) { return schemas[node.type] ?? schemas[`default:${node.type}`] ?? { title: node.type, properties:{}, 'x-events':Object.keys(node).filter(key => key.startsWith('on_')) }; }
function switchTab(tab) {
  editorTab = tab;
  $('.workspace').classList.toggle('generator-active', tab === 'generator');
  $('.workspace').classList.toggle('spawner-active', tab === 'spawner');
  $('#tab-graph').classList.toggle('active', tab === 'graph');
  $('#tab-generator').classList.toggle('active', tab === 'generator');
  $('#tab-spawner').classList.toggle('active', tab === 'spawner');
  if (tab === 'generator') drawGenerator();
  if (tab === 'spawner') drawSpawner();
}
function editGenerator(action) {
  try { action(); }
  catch (error) { generatorError = error.message; const label = $('.generator-error'); if (label) label.textContent = generatorError; updateStatus(); toast(generatorError, true); }
}
function drawGenerator() {
  const panel = $('#generator-panel'); if (!panel || !model) return;
  const generator = model.config.generator ?? {type:'generator:fixed', at:'0;100;0'};
  const schema = generatorSchemas[generator.type];
  const options = Object.entries(generatorSchemas).map(([type, entry]) => `<option value="${escape(type)}" ${type === generator.type ? 'selected' : ''}>${escape(entry.title)} · ${escape(type)}</option>`).join('');
  panel.innerHTML = `<div class="generator-card"><div class="eyebrow">МЕСТО ПОЯВЛЕНИЯ</div><h2>Генератор локации</h2><p>Генератор определяет координаты нового аирдропа в выбранном мире.</p><div class="field"><label>Тип генератора</label><select id="generator-type">${schema ? '' : `<option value="${escape(generator.type)}" selected>${escape(generator.type)} · неизвестный тип</option>`}${options}</select></div><p class="muted">${escape(schema?.description ?? 'Тип генератора не зарегистрирован. Его JSON можно отредактировать ниже.')}</p><p class="generator-error">${escape(generatorError)}</p>${Object.entries(schema?.properties ?? {}).filter(([key]) => key !== 'type').map(([key, field]) => {
    const value = generator[key] ?? field.default ?? '';
    return `<div class="field"><label>${escape(field.title ?? key)}</label>${field['x-control'] === 'vector' ? vectorInputs(value, field['x-fractional'] === true, `data-generator-vector="${escape(key)}"`) : `<input data-generator-field="${escape(key)}" type="${field.type === 'integer' ? 'number' : 'text'}" value="${escape(value)}" ${field.minimum !== undefined ? `min="${field.minimum}"` : ''} ${field.type === 'integer' ? 'step="1"' : ''}>`}<small>${escape(field.description ?? '')}</small></div>`;
  }).join('')}<details><summary class="section-label">Полный JSON генератора</summary><textarea id="raw-generator" rows="12" spellcheck="false">${escape(JSON.stringify(generator, null, 2))}</textarea><small>Неизвестные поля сохраняются.</small><button id="apply-generator-json" type="button">Применить JSON</button></details></div>`;
  $('#generator-type').onchange = event => editGenerator(() => {
    const type = event.target.value, selectedSchema = generatorSchemas[type];
    const next = {type};
    for (const [key, field] of Object.entries(selectedSchema.properties ?? {})) if (key !== 'type' && Object.hasOwn(field, 'default')) next[key] = clone(field.default);
    generatorError = ''; model.setGenerator(next); drawGenerator(); updateStatus();
  });
  panel.querySelectorAll('[data-generator-field]').forEach(input => input.onchange = () => editGenerator(() => {
    const key = input.dataset.generatorField, field = schema.properties[key];
    let value = input.value;
    if (field.type === 'integer') {
      value = Number(value);
      if (!input.value || !Number.isInteger(value) || field.minimum !== undefined && value < field.minimum) throw new Error(`Недопустимое число для «${field.title}»`);
    }
    generatorError = ''; model.setGenerator({...model.config.generator, [key]:value}); drawGenerator(); updateStatus();
  }));
  panel.querySelectorAll('[data-generator-vector]').forEach(container => {
    container.querySelectorAll('input').forEach(input => input.onchange = () => editGenerator(() => {
      const key = container.dataset.generatorVector;
      const value = readVector(container, schema.properties[key]['x-fractional'] === true);
      generatorError = ''; model.setGenerator({...model.config.generator, [key]:value}); drawGenerator(); updateStatus();
    }));
  });
  $('#apply-generator-json').onclick = () => editGenerator(() => {
    const next = JSON.parse($('#raw-generator').value);
    if (!next || Array.isArray(next) || typeof next !== 'object' || typeof next.type !== 'string') throw new Error('Нужен объект генератора с полем type');
    generatorError = ''; model.setGenerator(next); drawGenerator(); updateStatus();
  });
}
function editSpawner(action) {
  try {
    action(); spawnerError = ''; drawSpawner(); updateStatus();
  } catch (error) {
    spawnerError = error.message; const label = $('.spawner-error'); if (label) label.textContent = spawnerError;
    updateStatus(); toast(spawnerError, true);
  }
}
function changeSpawner(action) {
  const next = clone(model.config.spawner ?? {type:'spawner:timer', time:'5m', when:{}, on_timer_end:[{make:{}}]});
  action(next); model.setSpawner(next);
}
function mapRows(kind, entries, extra = '') {
  return Object.entries(entries ?? {}).map(([key,value]) => `<div class="spawner-map-row" data-map-row="${kind}" data-original="${escape(key)}" ${extra}><input data-map-key value="${escape(key)}" aria-label="Имя параметра" placeholder="loot_table"><input data-map-value value="${escape(value)}" aria-label="Значение параметра" placeholder="example/default"><button type="button" data-op="remove-pair" title="Удалить">×</button></div>`).join('');
}
function mountActions(host, value, write, descriptors = {}, compact = false) {
  return new EventDslEditor(host, {value, descriptors, compact, parseYaml: yaml => api('commands/parse',{yaml}), onChange: next => {
    write(next); const spawnerJson=$('#raw-spawner'),nodeJson=$('#raw-node'); if(spawnerJson)spawnerJson.value=JSON.stringify(model.config.spawner,null,2); if(nodeJson&&selected)nodeJson.value=JSON.stringify(model.nodes[selected],null,2); drawGraph(); updateStatus();
  }});
}
function drawSpawner() {
  const panel=$('#spawner-panel');if(!panel||!model)return;
  const spawner=model.config.spawner??{type:'spawner:timer',time:'5m',when:{},on_timer_end:[{make:{}}]};
  const schema=spawnerSchemas[spawner.type], supported=spawner.type==='spawner:timer';
  const options=Object.entries(spawnerSchemas).map(([type,entry])=>`<option value="${escape(type)}" ${type===spawner.type?'selected':''}>${escape(entry.title)} · ${escape(type)}</option>`).join('');
  const section=(title,key)=>`<section class="spawner-section"><h3>${title}</h3><div data-event-section="${key}"></div></section>`;
  panel.innerHTML=`<div class="spawner-content"><div class="eyebrow">ЗАПУСК АИРДРОПА</div><h2>Спавнер и параметры</h2><p class="spawner-error">${escape(spawnerError)}</p><section class="spawner-section"><h3>Тип спавнера</h3><select id="spawner-type">${schema?'':`<option value="${escape(spawner.type)}" selected>${escape(spawner.type)}</option>`}${options}</select><small>${escape(schema?.description??'Неизвестный тип')}</small></section><section class="spawner-section"><h3>Параметры аирдропа</h3>${mapRows('args',model.config.args??{})}<button data-spawner-op="add-arg">+ Параметр</button></section>${supported?`<section class="spawner-section"><h3>Отсчёт</h3><div class="field"><label>Время до появления</label><input id="spawner-time" value="${escape(spawner.time??'5m')}"></div></section>${section('При запуске отсчёта','on_create')}<section class="spawner-section"><div class="spawner-section-head"><h3>Объявления по времени</h3><button data-spawner-op="add-time">+ Время</button></div><p>Момент или диапазон: 2m, 5s-1s.</p>${Object.entries(spawner.when??{}).map(([time])=>`<div class="spawner-card" data-time-card="${escape(time)}"><div class="spawner-card-head"><label>Осталось <input data-time-key value="${escape(time)}"></label><button data-spawner-op="remove-time">Удалить</button></div><div data-when-actions="${escape(time)}"></div></div>`).join('')}</section>${section('Когда время вышло','on_timer_end')}`:''}<section class="spawner-section"><details><summary>Полный JSON спавнера</summary><textarea id="raw-spawner" rows="16" spellcheck="false">${escape(JSON.stringify(spawner,null,2))}</textarea><button id="apply-spawner-json">Применить JSON</button></details></section></div>`;
  $('#spawner-type').onchange=e=>editSpawner(()=>{const type=e.target.value,selectedSchema=spawnerSchemas[type];if(!selectedSchema)throw new Error('Тип спавнера не найден');const next={type};for(const [key,field] of Object.entries(selectedSchema.properties??{}))if(key!=='type'&&Object.hasOwn(field,'default'))next[key]=clone(field.default);if(type==='spawner:timer')next.on_timer_end??=[{make:{}}];model.setSpawner(next);});
  $('#spawner-time')?.addEventListener('change',e=>editSpawner(()=>{if(!validDuration(e.target.value))throw new Error('Время: например 5m');changeSpawner(next=>next.time=e.target.value);}));
  $('#apply-spawner-json').onclick=()=>editSpawner(()=>{const value=JSON.parse($('#raw-spawner').value);if(!value||Array.isArray(value)||typeof value!=='object')throw new Error('Нужен объект');model.setSpawner(value);});
  const descriptors={make:makeDescriptor()};
  panel.querySelectorAll('[data-event-section]').forEach(host=>{const key=host.dataset.eventSection;mountActions(host,spawner[key],value=>changeSpawner(next=>next[key]=value),descriptors);});
  panel.querySelectorAll('[data-when-actions]').forEach(host=>{const time=host.dataset.whenActions;mountActions(host,spawner.when?.[time],value=>changeSpawner(next=>{next.when??={};next.when[time]=value;}),descriptors);});
  panel.onchange=e=>{const input=e.target;if(input.matches('[data-time-key]'))editSpawner(()=>{const old=input.closest('[data-time-card]').dataset.timeCard,key=input.value.trim();if(!validTimeKey(key))throw new Error('Время: используйте 2m или 5s-1s');changeSpawner(next=>{if(key!==old&&Object.hasOwn(next.when,key))throw new Error('Такое время уже есть');const value=next.when[old];delete next.when[old];next.when[key]=value;});});
    if(input.matches('[data-map-key],[data-map-value]'))editSpawner(()=>{const entries=[...panel.querySelectorAll('[data-map-row="args"]')].map(row=>[row.querySelector('[data-map-key]').value.trim(),row.querySelector('[data-map-value]').value]);if(entries.some(([key])=>!key)||new Set(entries.map(([key])=>key)).size!==entries.length)throw new Error('Имена параметров должны быть уникальны');model.setArgs(Object.fromEntries(entries));drawSpawner();});};
  panel.onclick=async e=>{const button=e.target.closest('[data-spawner-op], [data-op="remove-pair"]');if(!button)return;const op=button.dataset.spawnerOp??button.dataset.op;
    if(op==='add-time'){const result=await dialog('<h2>Время объявления</h2><div class="field"><label>Осталось времени</label><input name="time" placeholder="2m или 5s-1s" required></div>','Добавить');if(!result)return;editSpawner(()=>{const key=String(result.get('time')).trim();if(!validTimeKey(key))throw new Error('Время: используйте 2m или 5s-1s');changeSpawner(next=>{next.when??={};if(Object.hasOwn(next.when,key))throw new Error('Такое время уже есть');next.when[key]=[];});});}
    if(op==='remove-time')editSpawner(()=>changeSpawner(next=>delete next.when[button.closest('[data-time-card]').dataset.timeCard]));
    if(op==='add-arg'){const result=await dialog('<h2>Параметр</h2><div class="field"><label>Имя</label><input name="key" required></div><div class="field"><label>Значение</label><input name="value"></div>','Добавить');if(!result)return;editSpawner(()=>{const key=String(result.get('key')).trim(),args={...model.config.args};if(!key||Object.hasOwn(args,key))throw new Error('Имя должно быть уникальным');args[key]=String(result.get('value'));model.setArgs(args);drawSpawner();});}
    if(op==='remove-pair')editSpawner(()=>{const args={...model.config.args};delete args[button.closest('[data-map-row]').dataset.original];model.setArgs(args);drawSpawner();});};
}

const eventsFor = node => [...new Set([...(schemaFor(node)['x-events'] ?? []), ...Object.keys(node).filter(key => key.startsWith('on_'))])];
function refresh() {
  if (!model) return;
  graphView = clone(model.metadata.viewport);
  for (const key of invalidFields.keys()) if (!model.node(JSON.parse(key)[0])) invalidFields.delete(key);
  if (selected && !model.node(selected)) selected = null;
  if (selectedEdge) selectedEdge = model.edges().find(edge => edge.id === selectedEdge.id) ?? null;
  drawGraph(); drawInspector();
  if (editorTab === 'generator') drawGenerator();
  if (editorTab === 'spawner') drawSpawner();
  $('#outline').innerHTML = Object.entries(model.allNodes()).map(([name, node]) => `<button class="outline-node ${selected === name ? 'active' : ''}" data-name="${escape(name)}"><span style="color:${model.layout(name).color || color(node.type)}">●</span> ${escape(model.title(name))}</button>`).join('');
  $('#outline').querySelectorAll('button').forEach(button => button.onclick = () => { selected = button.dataset.name; selectedEdge = null; refresh(); });
  updateStatus();
}
function updateStatus() {
  const edges = model.edges(); $('#node-count').textContent = `${Object.keys(model.allNodes()).length} нод`; $('#edge-count').textContent = `${edges.length} связей`;
  const missing = edges.filter(edge => edge.missing && !edge.dynamic).length;
  $('#warnings').textContent = [missing ? `${missing} ссылок на отсутствующие ноды` : '', generatorError, spawnerError].filter(Boolean).join(' · ');
  $('#warnings').className = missing || generatorError || spawnerError ? 'warn' : '';
  $('#save-state').textContent = model.dirty ? '● Есть несохранённые изменения' : 'Все изменения сохранены';
  $('#undo').disabled = !model.undoStack.length; $('#redo').disabled = !model.redoStack.length;
}
const protectedNode = name => ['__init__','__destroy__'].includes(name);
function inputsFor(name, edges = model.edges()) {
  const node = model.node(name);
  if (node.type === 'web:note') return [];
  if (name === '__destroy__') return [{key:'destroy',label:'destroy',action:'destroy'}];
  if (node.type === 'default:commands') return [{key:'create',label:'run',action:'create'}];
  const inputs = [{key:'create', label:'create', action:'create'}];
  if (!protectedNode(name) && !['__init__','default:__init__','__destroy__','default:__destroy__'].includes(node.type)) inputs.push({key:'remove', label:'remove', action:'remove'});
  const calls = [...new Set([...(schemaFor(node)['x-inputs'] ?? []).filter(v => !['create','remove','destroy'].includes(v)),
    ...edges.filter(e => e.target === name && e.action === 'call').map(e => e.event)])];
  calls.forEach(event => inputs.push({key:`call:${event}`,label:event,action:'call',event}));
  return inputs;
}
const outputsFor = name => model.isWeb(name) ? [] : eventsFor(model.node(name));
const nodeWidth = name => Math.min(760,Math.max(260,model.layout(name).width || 340));
const portRows = name => Math.max(inputsFor(name).length,outputsFor(name).length,1);
const bodyTop = name => 106+(portRows(name)-1)*26;
function heightFor(name, inputs = inputsFor(name)) {
  const node = model.node(name);
  const minimum=node.type==='web:note'?140:bodyTop(name)+70;
  const suggested=node.type==='web:note'?220:Math.max(310,bodyTop(name)+180);
  return Math.min(780,Math.max(minimum,model.layout(name).height || suggested));
}
function inputKey(edge) { return edge.action === 'call' ? `call:${edge.event}` : edge.action; }
function endpoints(edge, inputs = inputsFor(edge.target)) {
  const a = model.layout(edge.source), b = model.layout(edge.target);
  const row = Math.max(0, inputs.findIndex(p => p.key === inputKey(edge)));
  return {x:a.x+nodeWidth(edge.source), y:a.y+82+outputsFor(edge.source).indexOf(edge.output)*26, tx:b.x, ty:b.y+82+row*26};
}
const edgeColor = action => ({remove:'#ff9da4',destroy:'#ff727f',call:'#84aaff',create:'#63dec0'}[action]);
function curve(x,y,tx,ty) { const d=Math.max(70,Math.abs(tx-x)*.45); return `M${x},${y} C${x+d},${y} ${tx-d},${ty} ${tx},${ty}`; }
const handlerList = value => value === undefined || value === null ? [] : Array.isArray(value) ? value : [value];
function vectorInputs(value, fractional, attributes = '') {
  const parts = splitVector(value);
  return `<div class="vector-inputs" style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px" ${attributes}>${['x','y','z'].map((axis, index) => `<label style="min-width:0;font-size:11px">${axis.toUpperCase()}<input data-vector-axis="${axis}" type="number" step="${fractional ? 'any' : '1'}" value="${escape(parts[index] ?? '')}"></label>`).join('')}</div>`;
}
function readVector(container, fractional) {
  return joinVector([...container.querySelectorAll('[data-vector-axis]')].map(input => input.value), fractional);
}
function nodeFields(name,node,schema) {
  const known=new Set(['type']);
  const fields=Object.entries(schema.properties??{}).filter(([key])=>key!=='type').map(([key,field])=>{
    known.add(key);
    const value=node[key]??field.default??'', control=field['x-control'], title=field.title??key;
    if(control==='event') {
      return `<div class="node-field"><label>${escape(title)}</label><div class="node-field-summary">${actionCount(editableCommands(handlerList(value)))} действий · настройка справа</div></div>`;
    }
    if(control==='lines') return `<div class="node-field"><label>${escape(title)}</label><textarea data-node-lines="${escape(name)}" data-field="${escape(key)}" spellcheck="false" placeholder="Одна строка на линию">${escape(nodeDrafts.get(JSON.stringify([name,key]))??(Array.isArray(value)?value.join('\n'):value))}</textarea></div>`;
    if(control==='materials' || control==='material-map') return `<div class="node-field"><label>${escape(title)}</label><div class="node-field-summary">${escape(control==='materials' ? `${Array.isArray(value)?value.length:0} материалов` : `${Object.keys(value ?? {}).length} замен`)} · настройка справа</div></div>`;
    if(control==='vector') return `<div class="node-field"><span>${escape(title)}</span>${vectorInputs(value, field['x-fractional'] === true, `data-node-vector="${escape(name)}" data-vector-field="${escape(key)}"`)}</div>`;
    if(field.type==='string'||field.type==='integer') return `<div class="node-field"><label>${escape(title)}</label><input data-inline-node="${escape(name)}" data-inline-field="${escape(key)}" type="${field.type==='integer'?'number':'text'}" value="${escape(value)}" ${field.minimum!==undefined?`min="${field.minimum}"`:''}></div>`;
    return `<div class="node-field"><label>${escape(title)}</label><div class="node-field-summary">${escape(Array.isArray(value)?`${value.length} элементов`:typeof value==='object'?Object.keys(value??{}).join(', '):String(value))}</div></div>`;
  });
  for(const [key,value] of Object.entries(node)) if(!known.has(key)) fields.push(`<div class="node-field"><label>${escape(key)}</label><div class="node-field-summary">${escape(typeof value==='object'?Array.isArray(value)?`${value.length} элементов`:Object.keys(value).join(', '):String(value))}</div></div>`);
  return fields.join('')||'<div class="node-field-empty">Настроек нет</div>';
}
function drawGraph() {
  $('.graph').querySelectorAll('.node-fields').forEach(el=>nodeScroll.set(el.dataset.scrollNode,el.scrollTop));
  $('.graph').querySelectorAll('[data-note-text],[data-command-yaml]').forEach(input => {
    if (document.activeElement === input) nodeDrafts.set(JSON.stringify([input.dataset.noteText ?? input.dataset.commandYaml, input.dataset.noteText ? 'text' : 'on_run']), input.value);
  });
  $('.graph').querySelectorAll('[data-node-lines]').forEach(input=>{
    const key=JSON.stringify([input.dataset.nodeLines,input.dataset.field]);
    if(document.activeElement===input || invalidFields.has(key)) nodeDrafts.set(key,input.value);
  });
  const nodes = model.allNodes(), edges = model.edges(), handles = [];
  const ports = new Map(Object.keys(nodes).map(name => [name, inputsFor(name, edges)]));
  const siblingsByInput = new Map();
  for (const edge of edges) { const key=JSON.stringify([edge.target,inputKey(edge)]); if(!siblingsByInput.has(key))siblingsByInput.set(key,[]);siblingsByInput.get(key).push(edge); }
  const curves = edges.filter(e => !e.missing && !e.dynamic && e.id !== linking?.edge?.id).map(edge => {
    const {x,y,tx,ty}=endpoints(edge,ports.get(edge.target)), siblings=siblingsByInput.get(JSON.stringify([edge.target,inputKey(edge)]));
    const index=siblings.findIndex(e=>e.id===edge.id), hx=tx-22-(index%5)*14, hy=ty+(index%3-1)*9;
    const d=curve(x,y,hx,hy)+` L${tx},${ty}`, stroke=edgeColor(edge.action), selectedClass=selectedEdge?.id===edge.id?' selected':'';
    handles.push(`<g class="edge-end" data-edge="${escape(edge.id)}" data-reconnect="${escape(edge.id)}"><title>Потяните конец связи на другой вход или на пустое место</title><circle class="edge-grab" cx="${hx}" cy="${hy}" r="12"/><circle class="edge-knob" cx="${hx}" cy="${hy}" r="4.5" stroke="${stroke}"/></g>`);
    return `<g class="wire${selectedClass}" data-edge="${escape(edge.id)}"><path class="edge-hit" d="${d}"/><path class="edge" d="${d}" stroke="${stroke}" marker-end="url(#arrow-${edge.action})"/><text class="edge-label" x="${(x+hx)/2}" y="${(y+hy)/2-8}">${escape(edge.action==='call'?edge.event:edge.action)}</text></g>`;
  }).join('');
  const cards = Object.entries(nodes).map(([name,node]) => {
    const p=model.layout(name), width=nodeWidth(name), height=heightFor(name,ports.get(name)), title=model.title(name), selectedClass=selected===name?' selected':'';
    const resize=`<g data-resize="${escape(name)}" class="note-resize"><title>Изменить размер</title><rect x="${width-25}" y="${height-25}" width="25" height="25" fill="transparent"/><path d="M${width-18},${height-6} L${width-6},${height-18} M${width-12},${height-6} L${width-6},${height-12}"/></g>`;
    if (node.type==='web:note') return `<g class="node note${selectedClass}" data-node="${escape(name)}" transform="translate(${p.x},${p.y})"><rect class="node-card" rx="10" width="${width}" height="${height}"/><path d="M10,1 H${width-10}" stroke="${p.color||'#d8b773'}" stroke-width="3"/><text class="node-name" x="18" y="29">${escape(title)}</text><text class="note-kind" x="18" y="47">NOTE · только редактор</text><foreignObject x="18" y="65" width="${width-36}" height="${height-90}"><div xmlns="http://www.w3.org/1999/xhtml" class="node-fields note-editor" data-scroll-node="${escape(name)}"><textarea data-note-text="${escape(name)}" placeholder="Текст заметки">${escape(nodeDrafts.get(JSON.stringify([name,'text'])) ?? node.text ?? '')}</textarea></div></foreignObject>${resize}</g>`;
    const inputs=ports.get(name), outputs=outputsFor(name), schema=schemaFor(node), tint=p.color || color(node.type);
    const portMarkup=inputs.map((input,i)=>`<g class="input-port" data-target="${escape(name)}" data-input="${escape(input.key)}"><title>${escape(input.action==='call'?`call: ${input.event} ${name}`:`${input.action}: ${name}`)}</title><circle class="port-hit" cx="0" cy="${82+i*26}" r="13"/><circle class="port port-${input.action}" cx="0" cy="${82+i*26}" r="6"/><text class="input-label" x="17" y="${86+i*26}">${escape(input.label)}</text></g>`).join('')+
      outputs.map((event,i)=>`<g class="output-port" data-source="${escape(name)}" data-output="${escape(event)}"><title>Зажмите и потяните к входу другой ноды</title><circle class="port-hit" cx="${width}" cy="${82+i*26}" r="13"/><circle class="port" cx="${width}" cy="${82+i*26}" r="6"/><text class="event-label" text-anchor="end" x="${width-17}" y="${86+i*26}">${escape(event)}</text></g>`).join('');
    if (node.type === 'default:commands') {
      const commandText = nodeDrafts.get(JSON.stringify([name,'on_run'])) ?? formatCommands(handlerList(node.on_run));
      return `<g class="node command-note${selectedClass}" data-node="${escape(name)}" transform="translate(${p.x},${p.y})"><rect class="node-card" rx="10" width="${width}" height="${height}"/><path d="M10,1 H${width-10}" stroke="${tint}" stroke-width="3"/><text class="node-name" x="18" y="29">${escape(title)}</text><text class="note-kind" x="18" y="47">Команды · YAML</text>${portMarkup}<foreignObject x="18" y="104" width="${width-36}" height="${height-129}"><div xmlns="http://www.w3.org/1999/xhtml" class="node-fields command-editor" data-scroll-node="${escape(name)}"><pre class="yaml-highlight" aria-hidden="true">${highlightYaml(commandText)}</pre><textarea data-command-yaml="${escape(name)}" spellcheck="false" placeholder="- console: 'Команда'">${escape(commandText)}</textarea><small class="node-field-error"></small></div></foreignObject>${resize}</g>`;
    }
    const top=bodyTop(name), innerHeight=Math.max(20,height-top-25);
    const body=`<foreignObject x="14" y="${top}" width="${width-28}" height="${innerHeight}"><div xmlns="http://www.w3.org/1999/xhtml" class="node-fields" data-scroll-node="${escape(name)}">${nodeFields(name,node,schema)}</div></foreignObject>`;
    return `<g class="node${selectedClass}" data-node="${escape(name)}" transform="translate(${p.x},${p.y})"><rect class="node-card" rx="10" width="${width}" height="${height}"/><path d="M10,1 H${width-10}" stroke="${tint}" stroke-width="3"/><text class="node-name" x="18" y="27">${escape(title.length>33?title.slice(0,31)+'…':title)}</text><text class="node-type" x="18" y="46">${escape(`${name} · ${schema.title}`)}</text><line class="node-divider" x1="0" x2="${width}" y1="61" y2="61"/>${portMarkup}${body}${resize}</g>`;
  }).join('');
  let preview='';
  if (linking) {
    const point=model.layout(linking.source), x=point.x+nodeWidth(linking.source), y=point.y+82+outputsFor(linking.source).indexOf(linking.output)*26;
    preview=`<path class="connection-preview" d="${curve(x,y,linking.point.x,linking.point.y)}" marker-end="url(#arrow-preview)"/><circle class="preview-tip" cx="${linking.point.x}" cy="${linking.point.y}" r="5"/>`;
  }
  $('.graph').innerHTML=`<defs>${['create','remove','destroy','call','preview'].map(action=>`<marker id="arrow-${action}" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0 0 L10 5 L0 10 Z" fill="${action==='preview'?'#e2fff5':edgeColor(action)}"/></marker>`).join('')}</defs><g id="scene" transform="translate(${graphView.x},${graphView.y}) scale(${graphView.zoom})">${curves}${cards}${handles.join('')}${preview}</g>`;
  $('#zoom-value').textContent=`${Math.round(graphView.zoom*100)}%`;
  $('#graph-hint').textContent=linking?(linking.edge?'Отпустите на другом входе · На пустом месте — отвязать · Esc — отмена':'Тяните к входу create, remove, destroy или событию · Esc — отмена'):'Потяните от выхода к входу · Конец стрелки — отсоединить · ПКМ — меню';
  $('#graph-hint').classList.toggle('active',!!linking);
  $('.graph').classList.toggle('connecting',!!linking);
  $('.graph').querySelectorAll('.node-fields').forEach(el=>el.scrollTop=nodeScroll.get(el.dataset.scrollNode)||0);
  $('.graph').querySelectorAll('[data-note-text]').forEach(input => {
    input.oninput = () => nodeDrafts.set(JSON.stringify([input.dataset.noteText,'text']), input.value);
    input.onchange = () => {
      const name = input.dataset.noteText;
      model.change(() => model.webNodes[name].text = input.value);
      nodeDrafts.delete(JSON.stringify([name,'text']));
      updateStatus(); if (selected === name) drawWebInspector();
    };
  });
  $('.graph').querySelectorAll('[data-command-yaml]').forEach(input => {
    const preview = input.previousElementSibling;
    input.oninput = () => {
      nodeDrafts.set(JSON.stringify([input.dataset.commandYaml,'on_run']), input.value);
      preview.innerHTML = highlightYaml(input.value);
    };
    input.onscroll = () => { preview.scrollTop = input.scrollTop; preview.scrollLeft = input.scrollLeft; };
    input.onchange = () => {
      if (copyingYaml) return;
      const name = input.dataset.commandYaml, draft = input.value, owner = model;
      const task = (async () => {
        try {
          const parsed = await api('commands/parse', {yaml:draft});
          if (model !== owner || nodeDrafts.get(JSON.stringify([name,'on_run'])) !== draft || !model.nodes[name]) return;
          model.setField(name, 'on_run', replaceEditableCommands(handlerList(model.nodes[name].on_run), parsed));
          input.blur();
          nodeDrafts.delete(JSON.stringify([name,'on_run']));
          invalidFields.delete(JSON.stringify([name,'on_run']));
          refresh();
        } catch (error) {
          if (model !== owner) return;
          invalidFields.set(JSON.stringify([name,'on_run']), {value:draft,message:error.message});
          input.classList.add('invalid');
          input.parentElement.querySelector('.node-field-error').textContent = error.message;
        }
      })();
      pendingCommandEdits.set(name, task);
      task.finally(() => { if (pendingCommandEdits.get(name) === task) pendingCommandEdits.delete(name); });
    };
  });
  $('.graph').querySelectorAll('[data-inline-field]').forEach(input=>input.onchange=()=>safe(()=>{
    const node=model.nodes[input.dataset.inlineNode], field=input.dataset.inlineField, schema=schemaFor(node).properties[field];
    const value=schema.type==='integer'?Number(input.value):input.value;
    if(schema.type==='integer'&&(!Number.isInteger(value)||!input.value||schema.minimum!==undefined&&value<schema.minimum)) throw new Error('Введите допустимое целое число');
    model.setField(input.dataset.inlineNode,field,value);refresh();
  }));
  $('.graph').querySelectorAll('[data-node-vector]').forEach(container => {
    container.querySelectorAll('input').forEach(input => input.onchange = () => safe(() => {
      const name = container.dataset.nodeVector, field = container.dataset.vectorField;
      const schema = schemaFor(model.nodes[name]).properties[field];
      model.setField(name, field, readVector(container, schema['x-fractional'] === true));
      refresh();
    }));
  });
  $('.graph').querySelectorAll('[data-node-lines]').forEach(input=>input.onchange=()=>safe(()=>{
    model.setField(input.dataset.nodeLines,input.dataset.field,input.value?input.value.split('\n'):[]);
    nodeDrafts.delete(JSON.stringify([input.dataset.nodeLines,input.dataset.field]));refresh();
  }));

}
function graphPoint(event) {
  const b=$('.graph').getBoundingClientRect();
  return {x:(event.clientX-b.left-graphView.x)/graphView.zoom,y:(event.clientY-b.top-graphView.y)/graphView.zoom};
}
function targetAt(event) { return document.elementFromPoint(event.clientX,event.clientY)?.closest('[data-target][data-input]'); }
function finishLink(port) {
  const target=port.dataset.target, input=inputsFor(target).find(p=>p.key===port.dataset.input);
  if (!input) return;
  if (linking.edge) model.reconnect(linking.edge,target,input.action,input.event);
  else model.connect(linking.source,linking.output,target,input.action,input.event);
}
function cancelGesture() {
  if (drag?.before) { model.config=drag.before.config; model.metadata=drag.before.metadata; model.dirty=drag.dirtyBefore; }
  drag=null; linking=null; closeContext(); if (model) refresh();
}
function setupGraph() {
  const svg=$('.graph');
  graphListeners?.abort(); graphListeners=new AbortController();
  let fieldPointer = null;
  svg.onpointerdown=event=> {
    if (event.button!==0) return;
    fieldPointer = null;
    if (event.target.closest('.node-fields')) { fieldPointer=event.pointerId; selected=event.target.closest('[data-node]')?.dataset.node??selected;selectedEdge=null;drawInspector();return; }
    closeContext();
    const port=event.target.closest('[data-target][data-input]');
    if (linking?.click) { if (port) safe(()=>finishLink(port)); linking=null; refresh(); return; }
    const output=event.target.closest('[data-output]'), end=event.target.closest('[data-reconnect]'), wire=event.target.closest('[data-edge]'), resize=event.target.closest('[data-resize]'), node=event.target.closest('[data-node]');
    if (output) linking={source:output.dataset.source,output:output.dataset.output,point:graphPoint(event),startX:event.clientX,startY:event.clientY};
    else if (end) {
      const edge=model.edges().find(e=>e.id===end.dataset.reconnect);
      linking={source:edge.source,output:edge.output,edge,point:graphPoint(event),startX:event.clientX,startY:event.clientY};
    } else if (wire || port) {
      const edge=wire?model.edges().find(e=>e.id===wire.dataset.edge):model.edges().filter(e=>e.target===port.dataset.target&&inputKey(e)===port.dataset.input).at(-1);
      if (edge) {
        const end=endpoints(edge),point=graphPoint(event);
        if (port||Math.hypot(point.x-end.tx,point.y-end.ty)<60) linking={source:edge.source,output:edge.output,edge,point,startX:event.clientX,startY:event.clientY};
        else { selectedEdge=edge;selected=null;refresh();return; }
      } else {selected=node?.dataset.node??null;refresh();return;}
    } else {
      selectedEdge=null;
      if (node) {
        selected=node.dataset.node;
        drag={kind:resize?'resize':'node',name:selected,startX:event.clientX,startY:event.clientY,point:{...clone(model.layout(selected)),width:nodeWidth(selected),height:heightFor(selected)},before:model.snapshot(),dirtyBefore:model.dirty};
      } else { selected=null; drag={kind:'pan',startX:event.clientX,startY:event.clientY,point:clone(graphView),before:model.snapshot(),dirtyBefore:model.dirty}; }
      drawInspector();
    }
    svg.setPointerCapture(event.pointerId); event.preventDefault(); drawGraph();
  };
  svg.onpointermove=event=> {
    if (linking) { linking.point=graphPoint(event); drawGraph(); const target=targetAt(event); target?.classList.add('drop-target'); return; }
    if (!drag) return;
    const dx=(event.clientX-drag.startX)/graphView.zoom, dy=(event.clientY-drag.startY)/graphView.zoom;
    if (drag.kind==='node') Object.assign(model.layout(drag.name),{x:drag.point.x+dx,y:drag.point.y+dy});
    else if (drag.kind==='resize') Object.assign(model.layout(drag.name),{width:Math.min(760,Math.max(260,drag.point.width+dx)),height:Math.min(780,Math.max(model.node(drag.name).type==='web:note'?140:bodyTop(drag.name)+70,drag.point.height+dy))});
    else {graphView.x=drag.point.x+dx*graphView.zoom;graphView.y=drag.point.y+dy*graphView.zoom;model.metadata.viewport=clone(graphView);}
    drawGraph();
  };
  svg.onpointerup=event=> {
    if (fieldPointer===event.pointerId) { fieldPointer=null; return; }
    if (linking && !linking.click) {
      const port=targetAt(event), moved=Math.hypot(event.clientX-linking.startX,event.clientY-linking.startY)>6;
      if (port) safe(()=>finishLink(port));
      else if (linking.edge && moved) {model.removeEdge(linking.edge);toast('Связь отсоединена. Ctrl+Z — вернуть.');}
      linking=null;
    }
    if (drag) {model.remember(drag.before);drag=null;}
    if (svg.hasPointerCapture(event.pointerId)) svg.releasePointerCapture(event.pointerId);
    refresh();
  };
  svg.onpointercancel=event=>{ if (fieldPointer===event.pointerId) { fieldPointer=null; return; } cancelGesture(); };
  document.addEventListener('pointerup', event => { if (fieldPointer===event.pointerId) fieldPointer=null; }, {signal:graphListeners.signal});
  svg.onwheel=event=> { if(event.target.closest('.node-fields')) return; event.preventDefault(); if (linking||drag) return; closeContext(); const b=svg.getBoundingClientRect(); zoomBy(Math.exp(-event.deltaY*.001),event.clientX-b.left,event.clientY-b.top); };
  svg.oncontextmenu=event=> {
    event.preventDefault(); if (drag||linking&&!linking.click) cancelGesture();
    const node=event.target.closest('[data-node]'), edge=event.target.closest('[data-edge]');
    showContext(event.clientX,event.clientY,{node:node?.dataset.node,edge:edge?model.edges().find(e=>e.id===edge.dataset.edge):null,position:graphPoint(event)});
  };
}
function closeContext() { $('#graph-context')?.remove(); }
function showContext(x,y,context) {
  closeContext(); const menu=document.createElement('div'); menu.id='graph-context';menu.className='graph-context';menu.setAttribute('role','menu');document.body.append(menu);
  const heading=text=>{const el=document.createElement('div');el.className='context-heading';el.textContent=text;menu.append(el);};
  const item=(text,action,danger=false)=>{const button=document.createElement('button');button.type='button';button.setAttribute('role','menuitem');button.textContent=text;if(danger)button.className='danger';button.onclick=()=>{closeContext();safe(action);};menu.append(button);};
  if (context.edge) {
    const edge=context.edge; heading('Связь');
    item('Переподключить',()=>{selectedEdge=edge;selected=null;linking={source:edge.source,output:edge.output,edge,click:true,point:context.position};refresh();});
    item('Удалить связь',()=>{model.removeEdge(edge);selectedEdge=null;refresh();},true);
  } else if (context.node) {
    const name=context.node;selected=name;selectedEdge=null;refresh();heading(model.title(name));
    item('Изменить название',()=>{$('#node-title')?.focus();$('#node-title')?.select();});
    if (inputsFor(name).length) item('Отвязать входящие связи',()=>{model.disconnectIncoming(name);refresh();});
    outputsFor(name).forEach(output=>item(`Связать: ${output}`,()=>{linking={source:name,output,click:true,point:context.position};drawGraph();}));
    if (!protectedNode(name)) item('Удалить ноду',()=>{model.remove(name);selected=null;refresh();},true);
  } else {
    heading('Добавить здесь');
    item('Заметка · Note',()=>quickAdd('web:note',context.position));
    heading('Компоненты');
    Object.entries(schemas).filter(([type])=>!type.includes('__init__')&&!type.includes('__destroy__')).forEach(([type,schema])=>item(schema.title,()=>quickAdd(type,context.position)));
  }
  menu.style.left=`${Math.max(8,Math.min(x,innerWidth-menu.offsetWidth-8))}px`;
  menu.style.top=`${Math.max(8,Math.min(y,innerHeight-menu.offsetHeight-8))}px`;
  menu.querySelector('button')?.focus({preventScroll:true});
}
function quickAdd(type,position) {
  if (type.startsWith('web:')) selected=model.addWeb(type,position);
  else {const stem=type.split(':').at(-1).replace(/[^\w-]/g,'node');let id=stem,i=1;while(model.node(id))id=stem+'_'+i++;model.add(id,type,schemas[type],position);selected=id;}
  selectedEdge=null;refresh();
}
document.addEventListener('pointerdown',event=>{if(!event.target.closest('#graph-context'))closeContext();});

function zoomBy(multiplier, cx = $('.graph-shell').clientWidth / 2, cy = $('.graph-shell').clientHeight / 2) {
  const zoom = Math.min(2, Math.max(.25, graphView.zoom * multiplier)), ratio = zoom / graphView.zoom;
  graphView.x = cx - (cx - graphView.x) * ratio; graphView.y = cy - (cy - graphView.y) * ratio; graphView.zoom = zoom;
  model.metadata.viewport = clone(graphView); model.dirty = true; drawGraph(); updateStatus();
}
function fit(markDirty = true) {
  const entries = Object.entries(model.allNodes()); if (!entries.length) return;
  const positions = entries.map(([name, node]) => ({ ...model.layout(name), width:nodeWidth(name), height:heightFor(name) }));
  const minX = Math.min(...positions.map(p => p.x)), minY = Math.min(...positions.map(p => p.y)), maxX = Math.max(...positions.map(p => p.x + p.width)), maxY = Math.max(...positions.map(p => p.y + p.height));
  const width = $('.graph-shell').clientWidth, height = $('.graph-shell').clientHeight;
  graphView.zoom = Math.min(1.2, Math.max(.25, Math.min((width - 90) / (maxX - minX), (height - 90) / (maxY - minY))));
  graphView.x = (width - (maxX - minX) * graphView.zoom) / 2 - minX * graphView.zoom;
  graphView.y = (height - (maxY - minY) * graphView.zoom) / 2 - minY * graphView.zoom;
  model.metadata.viewport = clone(graphView); if (markDirty) model.dirty = true; drawGraph(); updateStatus();
}
function materialEditor(key, value, control) {
  const entries = control === 'materials' ? (Array.isArray(value) ? value : []).map(item => [item]) : Object.entries(value && typeof value === 'object' && !Array.isArray(value) ? value : {});
  const rows = entries.map(([source, target], index) => `<div class="material-row"><input data-material-key="${escape(key)}" data-material-index="${index}" value="${escape(source)}" placeholder="${control === 'materials' ? 'bedrock' : 'barrier'}" aria-label="Материал ${index + 1}">${control === 'material-map' ? `<span>→</span><input data-material-value="${escape(key)}" data-material-index="${index}" value="${escape(target)}" placeholder="air" aria-label="Замена ${index + 1}">` : ''}<button type="button" data-material-remove="${escape(key)}" data-material-index="${index}" title="Удалить">×</button></div>`).join('');
  return `<div class="material-editor" data-material-editor="${escape(key)}" data-material-control="${control}">${rows}<button type="button" class="secondary" data-material-add="${escape(key)}">+ ${control === 'materials' ? 'Материал' : 'Замена'}</button></div>`;
}
function bindMaterialEditors(panel) {
  panel.querySelectorAll('[data-material-editor]').forEach(editor => {
    const key = editor.dataset.materialEditor, control = editor.dataset.materialControl;
    const commit = () => {
      const rows = [...editor.querySelectorAll('.material-row')];
      const entries = rows.map(row => [row.querySelector('[data-material-key]').value.trim(), row.querySelector('[data-material-value]')?.value.trim()]);
      if (entries.some(([source, target]) => !source || control === 'material-map' && !target)) throw new Error('Заполните все материалы');
      if (new Set(entries.map(([source]) => source.toLowerCase())).size !== entries.length) throw new Error('Материалы не должны повторяться');
      model.setField(selected, key, control === 'materials' ? entries.map(([source]) => source) : Object.fromEntries(entries));
      invalidFields.delete(JSON.stringify([selected, key]));
      drawGraph(); drawInspector(); updateStatus();
    };
    const apply = () => { try { commit(); } catch (error) { invalidFields.set(JSON.stringify([selected, key]), {message:error.message}); toast(error.message, true); } };
    editor.querySelectorAll('input').forEach(input => input.onchange = apply);
    editor.querySelectorAll('[data-material-remove]').forEach(button => button.onclick = () => { button.closest('.material-row').remove(); apply(); });
    editor.querySelector('[data-material-add]').onclick = () => {
      const row = document.createElement('div'); row.className = 'material-row';
      row.innerHTML = `<input data-material-key="${escape(key)}" placeholder="${control === 'materials' ? 'bedrock' : 'barrier'}" aria-label="Новый материал">${control === 'material-map' ? `<span>→</span><input data-material-value="${escape(key)}" placeholder="air" aria-label="Новая замена">` : ''}<button type="button" title="Удалить">×</button>`;
      editor.insertBefore(row, editor.querySelector('[data-material-add]'));
      invalidFields.set(JSON.stringify([selected, key]), {message:'Заполните новый материал'});
      row.querySelector('input').focus();
      row.querySelectorAll('input').forEach(input => input.onchange = apply);
      row.querySelector('button').onclick = () => { row.remove(); apply(); };
    };
  });
}
function drawInspector() {
  const panel = $('#inspector');
  if (selectedEdge) {
    panel.innerHTML = `<div class="section-label">Связь</div><h2>${escape(selectedEdge.action)}</h2><p>${escape(selectedEdge.source)} · ${escape(selectedEdge.output)} → ${escape(selectedEdge.target)}</p><button id="remove-edge" class="danger">Удалить связь</button>`;
    $('#remove-edge').onclick = () => { model.removeEdge(selectedEdge); selectedEdge = null; refresh(); }; return;
  }
  if (!selected) { panel.innerHTML = '<div class="inspector-empty"><div class="large-icon">⌘</div><h2>Настройки ноды</h2><p>Выберите ноду на схеме,<br>чтобы изменить её параметры и события.</p></div>'; return; }
  if (model.isWeb(selected)) { drawWebInspector(); return; }
  const node = model.nodes[selected], schema = schemaFor(node), required = schema.required ?? [];
  const fields = Object.entries(schema.properties ?? {}).filter(([key]) => key !== 'type');
  panel.innerHTML = `<div class="section-label">Компонент</div><div class="inspector-title"><h2>${escape(schema.title ?? node.type)}</h2><button id="delete-node" class="icon-button danger" title="Удалить ноду">×</button></div><p>${escape(schema.description ?? 'Тип не зарегистрирован.')}</p><div class="field"><label for="node-title">Название в редакторе</label><input id="node-title" value="${escape(model.layout(selected).title || '')}" placeholder="${escape(selected)}"></div><div class="field"><label for="node-name">ID ноды</label><input id="node-name" value="${escape(selected)}" ${['__init__','__destroy__'].includes(selected) ? 'readonly' : ''}></div><div class="field"><label for="node-color">Цвет ноды</label><div class="color-control"><input id="node-color" type="color" value="${escape(model.layout(selected).color || color(node.type))}"><button id="reset-color" type="button" class="secondary">Сбросить</button></div></div><div class="field"><label>Тип</label><div class="readonly-type">${escape(node.type)}</div></div>${fields.map(([key, field]) => {
    const value = node[key] ?? field.default ?? '', control = field['x-control'], json = control === 'event' || (!control && ['array','object'].includes(field.type));
    const draft = invalidFields.get(JSON.stringify([selected, key]));
    const display = draft?.value ?? (control === 'lines' ? (Array.isArray(value) ? value.join('\n') : value) : json ? JSON.stringify(value, null, 2) : value);
    const body = control === 'event' ? `<div data-inspector-event="${escape(key)}"></div>` : control === 'materials' || control === 'material-map' ? materialEditor(key,value,control) : control === 'vector' ? vectorInputs(draft?.value ?? value, field['x-fractional'] === true, `data-inspector-vector="${escape(key)}"`) : control === 'lines' || json ? `<textarea data-field="${escape(key)}" data-control="${control ?? 'json'}" rows="${json ? 5 : 3}" spellcheck="false">${escape(display)}</textarea>` : `<input data-field="${escape(key)}" data-control="${field.type}" type="${field.type === 'integer' ? 'number' : 'text'}" value="${escape(display)}" ${field.minimum !== undefined ? `min="${field.minimum}"` : ''} ${field.type === 'integer' ? 'step="1"' : ''} ${required.includes(key) ? 'required' : ''}>`;
    return `<div class="field${draft ? ' error' : ''}"><label>${escape(field.title ?? key)}${required.includes(key) ? ' *' : ''}</label>${body}<small>${escape(control === 'event' ? 'Связи create, remove и destroy соединяются на схеме.' : field.description ?? '')}</small><small class="field-error">${escape(draft?.message ?? '')}</small></div>`;
  }).join('')}<details><summary class="section-label">Полный JSON ноды</summary><textarea id="raw-node" rows="14" spellcheck="false">${escape(JSON.stringify(node, null, 2))}</textarea><small>Неизвестные поля сохраняются.</small><button id="apply-raw" style="margin-top:10px">Применить JSON</button></details><div class="section-label">Исходящие связи</div><div id="inspector-edges"></div>`;
  $('#node-title').onchange = event => { model.setTitle(selected,event.target.value); refresh(); };
  $('#node-color').oninput = event => { model.setColor(selected,event.target.value);drawGraph();updateStatus(); };
  $('#reset-color').onclick = () => {model.setColor(selected,null);refresh();};
  fields.filter(([,field])=>field['x-control']==='event').forEach(([key])=>{const name=selected;mountActions(panel.querySelector(`[data-inspector-event="${key}"]`),editableCommands(handlerList(model.nodes[name][key])),values=>model.setField(name,key,replaceEditableCommands(model.nodes[name][key]??[],values)),{},true);});
  bindMaterialEditors(panel);
  panel.querySelectorAll('[data-inspector-vector]').forEach(container => {
    container.querySelectorAll('input').forEach(input => input.onchange = () => {
      const field = container.dataset.inspectorVector, row = container.closest('.field');
      try {
        const value = readVector(container, schema.properties[field]['x-fractional'] === true);
        model.setField(selected, field, value);
        invalidFields.delete(JSON.stringify([selected, field]));
        $('#raw-node').value = JSON.stringify(model.nodes[selected], null, 2);
        row.classList.remove('error'); row.querySelector('.field-error').textContent = '';
        drawGraph(); updateStatus(); drawInspectorEdges();
      } catch (error) {
        invalidFields.set(JSON.stringify([selected, field]), {value:[...container.querySelectorAll('input')].map(input => input.value).join(';'), message:error.message});
        row.classList.add('error'); row.querySelector('.field-error').textContent = error.message;
      }
    });
  });
  $('#node-name').onchange = event => safe(() => {
    const old = selected, name = event.target.value.trim(); model.rename(old, name);
    for (const [key, draft] of [...invalidFields]) { const [nodeName, field] = JSON.parse(key); if (nodeName === old) { invalidFields.delete(key); invalidFields.set(JSON.stringify([name, field]), draft); } }
    selected = name; refresh();
  });
  $('#delete-node').onclick = () => safe(() => { if (confirm(`Удалить ноду «${selected}» и ссылки на неё?`)) { model.remove(selected); selected = null; refresh(); } });
  panel.querySelectorAll('[data-field]').forEach(input => input.onchange = () => {
    const field = input.dataset.field, control = input.dataset.control, container = input.closest('.field');
    try {
      let value;
      if (control === 'lines') value = input.value ? input.value.split('\n') : [];
      else if (control === 'event' || control === 'json') value = JSON.parse(input.value);
      else if (control === 'integer') { value = Number(input.value); if (!input.value || !Number.isInteger(value) || (input.min && value < Number(input.min))) throw new Error(`Нужно целое число от ${input.min || 'допустимого минимума'}`); }
      else value = input.value;
      model.setField(selected,field,value); invalidFields.delete(JSON.stringify([selected, field]));
      $('#raw-node').value = JSON.stringify(model.nodes[selected], null, 2);
      container.classList.remove('error'); container.querySelector('.field-error').textContent = '';
      drawGraph(); updateStatus(); drawInspectorEdges();
    } catch (error) { invalidFields.set(JSON.stringify([selected, field]), {value:input.value, message:error.message}); container.classList.add('error'); container.querySelector('.field-error').textContent = error.message; }
  });
  $('#apply-raw').onclick = () => safe(() => {
    const parsed = JSON.parse($('#raw-node').value);
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object' || typeof parsed.type !== 'string') throw new Error('Нужен объект с полем type');
    model.setNode(selected,parsed); invalidFields.clear(); nodeDrafts.clear(); refresh();
  });
  drawInspectorEdges();
}
function drawWebInspector() {
  const node=model.node(selected);
  $('#inspector').innerHTML=`<div class="section-label">Заметка</div><div class="inspector-title"><h2>Заметка</h2><button id="delete-web" class="danger icon-button" title="Удалить">×</button></div><p>Потяните нижний правый угол, чтобы изменить размер.</p><div class="field"><label for="node-title">Название</label><input id="node-title" value="${escape(node.title)}"></div><div class="field"><label for="node-color">Цвет ноды</label><input id="node-color" type="color" value="${escape(node.color||'#edc482')}"></div><div class="field"><label>Текст заметки</label><textarea id="note-text" rows="12">${escape(node.text)}</textarea></div><div class="note-size">${Math.round(node.width)} × ${Math.round(node.height)}</div>`;
  $('#node-title').onchange=event=>{model.setTitle(selected,event.target.value);refresh();};
  $('#node-color').oninput=event=>{model.setColor(selected,event.target.value);drawGraph();updateStatus();};
  $('#delete-web').onclick=()=>{model.remove(selected);selected=null;refresh();};
  $('#note-text').onchange=event=>{model.change(()=>model.webNodes[selected].text=event.target.value);nodeDrafts.delete(JSON.stringify([selected,'text']));drawGraph();updateStatus();};
}

function drawInspectorEdges() {
  const panel = $('#inspector-edges'); if (!panel || !selected) return;
  const edges = model.edges().filter(edge => edge.source === selected);
  panel.innerHTML = edges.map(edge => `<div class="connection-row"><span class="${edge.missing && !edge.dynamic ? 'warn' : ''}">${escape(edge.output)}<br>${escape(edge.action)} → ${escape(edge.target)}</span><button data-remove="${escape(edge.id)}" title="Удалить связь">×</button></div>`).join('') || '<p>Выберите выход события на схеме, затем вход другой ноды.</p>';
  panel.querySelectorAll('[data-remove]').forEach(button => button.onclick = () => { const edge = edges.find(e => e.id === button.dataset.remove); model.removeEdge(edge); refresh(); });
}
async function addNode(type) {
  const options = Object.entries({...schemas,'web:note':{title:'Заметка'}}).map(([key, schema]) => `<option value="${escape(key)}" ${key === type ? 'selected' : ''}>${escape(schema.title)} · ${escape(key)}</option>`).join('');
  const result = await dialog(`<h2>Новая нода</h2><p>Компонент определяет параметры и события ноды.</p><div class="field"><label>Тип компонента</label><select name="type">${options}</select></div><div class="field"><label>Уникальное имя</label><input name="name" required maxlength="128" placeholder="Например, timer_open" autocomplete="off"></div>`, 'Добавить ноду');
  if (!result) return;
  safe(() => {
    const name = String(result.get('name')).trim(), selectedType = result.get('type');
    const position = { x:(($('.graph-shell').clientWidth / 2) - graphView.x) / graphView.zoom - 130, y:(($('.graph-shell').clientHeight / 2) - graphView.y) / graphView.zoom - 60 };
    if (selectedType.startsWith('web:')) selected=model.addWeb(selectedType,position,name);
    else { model.add(name,selectedType,schemas[selectedType],position); selected=name; } selectedEdge = null; refresh();
  });
}
async function editSettings() {
  const {generator, spawner, args, ...data} = model.payload().settings;
  const result = await dialog(`<h2>Настройки аирдропа</h2><p>Идентификатор и мир. Генератор, спавнер и параметры аирдропа редактируются на вкладках.</p><textarea class="settings-json" name="settings" spellcheck="false">${escape(JSON.stringify(data, null, 2))}</textarea>`, 'Применить');
  if (!result) return;
  safe(() => {
    const parsed = JSON.parse(result.get('settings'));
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || Object.keys(parsed).some(key => !['id','world'].includes(key))) throw new Error('Допустимые поля: id, world');
    model.change(() => Object.assign(model.config, parsed)); refresh();
  });
}
async function flushCardDrafts() {
  for (const [encoded, draft] of [...nodeDrafts]) {
    const [name, field] = JSON.parse(encoded);
    if (field === 'text' && model.webNodes[name]) {
      model.change(() => model.webNodes[name].text = draft);
      nodeDrafts.delete(encoded);
    } else if (field === 'on_run' && model.nodes[name]?.type === 'default:commands') {
      const parsed = await api('commands/parse', {yaml:draft});
      model.setField(name, field, replaceEditableCommands(handlerList(model.nodes[name][field]), parsed));
      nodeDrafts.delete(encoded);
      invalidFields.delete(encoded);
    }
  }
}
async function copyYaml() {
  if (!model || $('#copy-yaml').disabled) return;
  const button = $('#copy-yaml'), current = model;
  button.disabled = true;
  copyingYaml = true;
  try {
    const config=clone(current.config), metadata=clone(current.metadata), drafts=new Map(nodeDrafts);
    for (const [encoded, entry] of invalidFields) {
      const [name, field]=JSON.parse(encoded);
      if (config.nodes[name] && field !== 'on_run') config.nodes[name][field]=entry.value;
      if (field === 'on_run' && config.nodes[name]?.type === 'default:commands') drafts.set(encoded,entry.value);
    }
    for (const [encoded, value] of drafts) {
      const [name, field]=JSON.parse(encoded);
      if (field === 'text' && metadata.webNodes?.[name]) metadata.webNodes[name].text=value;
      else if (field !== 'on_run' && config.nodes[name]) config.nodes[name][field]=Array.isArray(config.nodes[name][field]) ? (value ? value.split('\n') : []) : value;
    }
    const active=document.activeElement;
    if (active?.dataset.noteText && metadata.webNodes?.[active.dataset.noteText]) metadata.webNodes[active.dataset.noteText].text=active.value;
    if (active?.id === 'note-text' && selected && metadata.webNodes?.[selected]) metadata.webNodes[selected].text=active.value;
    if (active?.dataset.commandYaml) drafts.set(JSON.stringify([active.dataset.commandYaml,'on_run']),active.value);
    if (active?.dataset.nodeLines && config.nodes[active.dataset.nodeLines]) config.nodes[active.dataset.nodeLines][active.dataset.field]=active.value ? active.value.split('\n') : [];
    if (active?.dataset.inlineField && config.nodes[active.dataset.inlineNode]) config.nodes[active.dataset.inlineNode][active.dataset.inlineField]=active.type==='number' && active.value!=='' ? Number(active.value) : active.value;
    if (active?.dataset.vectorAxis) {
      const container=active.closest('[data-node-vector],[data-inspector-vector],[data-generator-vector]');
      if (container) {
        const value=[...container.querySelectorAll('[data-vector-axis]')].map(input=>input.value).join(';');
        if (container.dataset.nodeVector && config.nodes[container.dataset.nodeVector]) config.nodes[container.dataset.nodeVector][container.dataset.vectorField]=value;
        else if (container.dataset.inspectorVector && selected && config.nodes[selected]) config.nodes[selected][container.dataset.inspectorVector]=value;
        else if (container.dataset.generatorVector && config.generator) config.generator[container.dataset.generatorVector]=value;
      }
    }
    if (active?.dataset.field && !active.dataset.nodeLines && selected && config.nodes[selected]) {
      const control=active.dataset.control;
      let value=active.value;
      if (control==='integer' && value!=='' && Number.isInteger(Number(value))) value=Number(value);
      else if (control==='lines') value=value ? value.split('\n') : [];
      else if (control==='json') { try { value=JSON.parse(value); } catch { /* Keep the raw draft. */ } }
      config.nodes[selected][active.dataset.field]=value;
    }
    const yaml=formatWorkflowYaml(config,metadata,drafts);
    let copied = false;
    try { if (navigator.clipboard?.writeText) { await navigator.clipboard.writeText(yaml); copied = true; } }
    catch { /* Try the browser's selection based copy below. */ }
    if (!copied) {
      const input = document.createElement('textarea');
      input.value = yaml; input.style.position = 'fixed'; input.style.opacity = '0';
      document.body.append(input); input.select();
      try { copied = document.execCommand('copy'); } catch { copied = false; }
      input.remove();
    }
    if (!copied) {
      await dialog(`<h2>YAML для копирования</h2><p>Браузер не разрешил автоматическое копирование. Выделите текст и скопируйте его вручную.</p><textarea readonly rows="16" spellcheck="false">${escape(yaml)}</textarea>`, 'Закрыть');
      return;
    }
    toast('Текущий YAML скопирован в буфер обмена.');
  } catch (error) { toast(error.message, true); }
  finally { copyingYaml = false; if (model === current) button.disabled = false; }
}
async function save() {
  if (!model || $('#save').disabled) return;
  const button = $('#save'), current = model; button.disabled = true; button.textContent = 'Проверка…';
  try {
    document.activeElement?.blur();
    await Promise.all([...pendingCommandEdits.values()]);
    await flushCardDrafts();
    if (model !== current) return;
    if (invalidFields.size || generatorError || spawnerError) { toast('Исправьте ошибки в полях перед сохранением.', true); return; }
    button.textContent = 'Сохранение…';
    const sent = current.snapshot();
    const savedDocument = await api('airdrop', current.payload());
    if (model === current) { current.saved(savedDocument, sent); updateStatus(); }
    toast('Сохранено. Создана резервная копия конфига.');
  } catch (error) { toast(error.message, true); }
  finally { if (model === current) { button.disabled = false; button.textContent = 'Сохранить'; } }
}
window.addEventListener('keydown', event => {
  if (!model || $('#dialog').open) return;
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') { event.preventDefault(); document.activeElement?.blur(); save(); return; }
  if (['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName)) return;
  if (event.key === 'Escape') { cancelGesture(); return; }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') { event.preventDefault(); event.shiftKey ? model.redo() : model.undo(); generatorError = ''; spawnerError = ''; invalidFields.clear(); nodeDrafts.clear(); refresh(); }
  if (event.key === 'Delete') safe(() => { if (selectedEdge) { model.removeEdge(selectedEdge); selectedEdge = null; refresh(); } else if (selected && confirm(`Удалить ноду «${selected}»?`)) { model.remove(selected); selected = null; refresh(); } });
});
const initialKey = location.hash.startsWith('#edit/') ? decodeURIComponent(location.hash.slice(6)) : null;
if (initialKey && token) openEditor(initialKey); else loadHome();
