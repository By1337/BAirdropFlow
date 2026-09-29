import { GraphModel, clone, settingsKeys } from './model.js';
import { editableCommands, replaceEditableCommands, formatCommands } from './command-yaml.js';
const $ = selector => document.querySelector(selector);
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
const colors = { default:'#a898f4', tick:'#edc482', loot:'#63dec0', block:'#ff9da4', dh:'#84aaff', wg:'#a1c8ff' };
const color = type => colors[String(type).split(':')[0]] ?? '#9aaac4';
let model, schemas = {}, selected, selectedEdge, linking, graphView, drag, invalidFields = new Map(), catalogue = [];
const nodeScroll = new Map();
const nodeDrafts = new Map();
const yamlRequests = new Map();
const pendingYaml = new Set();
function trackYaml(task) { pendingYaml.add(task); task.finally(()=>pendingYaml.delete(task)); }
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
  model = null; selected = null; selectedEdge = null; linking = null; invalidFields.clear(); nodeDrafts.clear(); yamlRequests.clear(); pendingYaml.clear();
  $('#breadcrumb').textContent = 'Все аирдропы';
  $('#app').innerHTML = '<div class="loading"><span class="spinner"></span>Загрузка аирдропов…</div>';
  try {
    [catalogue, schemas] = await Promise.all([api('airdrops'), api('schemas')]);
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
    model = new GraphModel(workflowDocument); selected = null; selectedEdge = null; linking = null; invalidFields.clear(); nodeScroll.clear(); nodeDrafts.clear(); yamlRequests.clear(); pendingYaml.clear();
    history.replaceState(null, '', `#edit/${encodeURIComponent(key)}`);
    $('#breadcrumb').textContent = `Аирдропы / ${workflowDocument.config.id ?? key}`;
    $('#app').innerHTML = `<section class="workspace"><div class="workspace-toolbar"><button id="back" class="secondary">←</button><button id="add-node">+ Нода</button><button id="undo" title="Ctrl+Z">↶</button><button id="redo" title="Ctrl+Shift+Z">↷</button><button id="settings" class="optional">Настройки аирдропа</button><div class="spacer"></div><button id="reload-editor" title="Перезагрузить с диска">↻</button><button id="save" class="primary">Сохранить</button></div><aside class="library"><div class="section-label">Компоненты</div><div id="node-library"></div><p>Добавьте компонент, затем потяните от выхода события к нужному входу другой ноды.</p><div class="section-label" style="margin-top:28px">Ноды сценария</div><div id="outline"></div></aside><div class="graph-shell"><svg class="graph" tabindex="0" aria-label="Схема аирдропа"></svg><div class="graph-hint" id="graph-hint">Перетаскивайте ноды · Колесо — масштаб · Фон — перемещение</div><div class="graph-controls"><button id="zoom-out" aria-label="Уменьшить">−</button><span id="zoom-value">100%</span><button id="zoom-in" aria-label="Увеличить">+</button><button id="fit" title="Показать весь граф">⤢</button></div></div><aside class="inspector" id="inspector"></aside><footer class="workspace-status"><span id="node-count"></span><span id="edge-count"></span><span id="warnings"></span><span id="save-state"></span></footer></section>`;
    $('#node-library').innerHTML = Object.entries(schemas).map(([type, schema]) => `<button data-type="${escape(type)}" style="--node-color:${color(type)}">${escape(schema.title)}<small>${escape(type)}</small></button>`).join('');
    $('#node-library').querySelectorAll('button').forEach(button => button.onclick = () => addNode(button.dataset.type));
    $('#back').onclick = () => { if (leave()) { history.replaceState(null, '', '#'); loadHome(); } };
    $('#add-node').onclick = () => addNode(); $('#settings').onclick = editSettings;
    $('#save').onclick = save; $('#reload-editor').onclick = () => openEditor(model.document.key);
    $('#undo').onclick = () => { model.undo(); invalidFields.clear(); nodeDrafts.clear(); refresh(); }; $('#redo').onclick = () => { model.redo(); invalidFields.clear(); nodeDrafts.clear(); refresh(); };
    $('#zoom-in').onclick = () => zoomBy(1.15); $('#zoom-out').onclick = () => zoomBy(1 / 1.15); $('#fit').onclick = fit;
    const library=$('#node-library');
    { const b=document.createElement('button'); b.textContent='Заметка · Note'; b.style.setProperty('--node-color','#edc482'); b.onclick=()=>quickAdd('web:note',{x:((($('.graph-shell').clientWidth/2)-graphView.x)/graphView.zoom),y:((($('.graph-shell').clientHeight/2)-graphView.y)/graphView.zoom)}); library.append(b); }
    graphView = clone(model.metadata.viewport); setupGraph(); refresh();
    if (!workflowDocument.metadata?.viewport) fit(false);
    if (workflowDocument.validationError) toast(`Конфиг содержит ошибки. Исправьте их перед сохранением.\n${workflowDocument.validationError}`, true);
  } catch (error) { toast(error.message, true); loadHome(); }
}
function schemaFor(node) { return schemas[node.type] ?? schemas[`default:${node.type}`] ?? { title: node.type, properties:{}, 'x-events':Object.keys(node).filter(key => key.startsWith('on_')) }; }
const eventsFor = node => [...new Set([...(schemaFor(node)['x-events'] ?? []), ...Object.keys(node).filter(key => key.startsWith('on_'))])];
function refresh() {
  if (!model) return;
  graphView = clone(model.metadata.viewport);
  for (const key of invalidFields.keys()) if (!model.node(JSON.parse(key)[0])) invalidFields.delete(key);
  if (selected && !model.node(selected)) selected = null;
  if (selectedEdge) selectedEdge = model.edges().find(edge => edge.id === selectedEdge.id) ?? null;
  drawGraph(); drawInspector();
  $('#outline').innerHTML = Object.entries(model.allNodes()).map(([name, node]) => `<button class="outline-node ${selected === name ? 'active' : ''}" data-name="${escape(name)}"><span style="color:${model.layout(name).color || color(node.type)}">●</span> ${escape(model.title(name))}</button>`).join('');
  $('#outline').querySelectorAll('button').forEach(button => button.onclick = () => { selected = button.dataset.name; selectedEdge = null; refresh(); });
  updateStatus();
}
function updateStatus() {
  const edges = model.edges(); $('#node-count').textContent = `${Object.keys(model.allNodes()).length} нод`; $('#edge-count').textContent = `${edges.length} связей`;
  const missing = edges.filter(edge => edge.missing && !edge.dynamic).length;
  $('#warnings').textContent = missing ? `${missing} ссылок на отсутствующие ноды` : '';
  $('#warnings').className = missing ? 'warn' : '';
  $('#save-state').textContent = model.dirty ? '● Есть несохранённые изменения' : 'Все изменения сохранены';
  $('#undo').disabled = !model.undoStack.length; $('#redo').disabled = !model.redoStack.length;
}
const protectedNode = name => ['__init__','__destroy__'].includes(name);
function inputsFor(name, edges = model.edges()) {
  const node = model.node(name);
  if (node.type === 'web:note') return [];
  if (name === '__destroy__') return [{key:'destroy',label:'destroy',action:'destroy'}];
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
function nodeFields(name,node,schema) {
  const known=new Set(['type']);
  const fields=Object.entries(schema.properties??{}).filter(([key])=>key!=='type').map(([key,field])=>{
    known.add(key);
    const value=node[key]??field.default??'', control=field['x-control'], title=field.title??key;
    if(control==='event') {
      const state=invalidFields.get(JSON.stringify([name,key]));
      return `<div class="node-field"><label>${escape(title)} <span>команды</span></label><textarea data-node-event="${escape(name)}" data-field="${escape(key)}" class="${state?'invalid':''}" spellcheck="false" placeholder="- bc: Привет всем">${escape(nodeDrafts.get(JSON.stringify([name,key]))??formatCommands(handlerList(value)))}</textarea><small class="node-field-error">${escape(state?.message??'')}</small></div>`;
    }
    if(control==='lines') return `<div class="node-field"><label>${escape(title)}</label><textarea data-node-lines="${escape(name)}" data-field="${escape(key)}" spellcheck="false" placeholder="Одна строка на линию">${escape(nodeDrafts.get(JSON.stringify([name,key]))??(Array.isArray(value)?value.join('\n'):value))}</textarea></div>`;
    if(field.type==='string'||field.type==='integer') return `<div class="node-field"><label>${escape(title)}</label><input data-inline-node="${escape(name)}" data-inline-field="${escape(key)}" type="${field.type==='integer'?'number':'text'}" value="${escape(value)}" ${field.minimum!==undefined?`min="${field.minimum}"`:''}></div>`;
    return `<div class="node-field"><label>${escape(title)}</label><div class="node-field-summary">${escape(Array.isArray(value)?`${value.length} элементов`:typeof value==='object'?Object.keys(value??{}).join(', '):String(value))}</div></div>`;
  });
  for(const [key,value] of Object.entries(node)) if(!known.has(key)) fields.push(`<div class="node-field"><label>${escape(key)}</label><div class="node-field-summary">${escape(typeof value==='object'?Array.isArray(value)?`${value.length} элементов`:Object.keys(value).join(', '):String(value))}</div></div>`);
  return fields.join('')||'<div class="node-field-empty">Настроек нет</div>';
}
async function commitYaml(input, read, write) {
  const key=JSON.stringify([input.dataset.nodeEvent,input.dataset.field]);
  const current=model, request=Symbol();yamlRequests.set(key,request);
  const message=input.parentElement.querySelector('.node-field-error');
  invalidFields.set(key,{value:input.value,message:'Проверка YAML…'});
  input.classList.remove('invalid');message.textContent='Проверка YAML…';
  try {
    const commands=await api('commands/parse',{yaml:input.value});
    if (model!==current || yamlRequests.get(key)!==request || !model.node(JSON.parse(key)[0])) return;
    write(replaceEditableCommands(read(),commands));
    invalidFields.delete(key);nodeDrafts.delete(key);refresh();
  } catch(error) {
    if(model!==current || yamlRequests.get(key)!==request)return;
    invalidFields.set(key,{value:input.value,message:error.message});
    if(input.isConnected){input.classList.add('invalid');message.textContent=error.message;}
  }
}
function drawGraph() {
  $('.graph').querySelectorAll('.node-fields').forEach(el=>nodeScroll.set(el.dataset.scrollNode,el.scrollTop));
  $('.graph').querySelectorAll('[data-node-event],[data-node-lines]').forEach(input=>{
    const key=JSON.stringify([input.dataset.nodeEvent||input.dataset.nodeLines,input.dataset.field]);
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
    if (node.type==='web:note') return `<g class="node note${selectedClass}" data-node="${escape(name)}" transform="translate(${p.x},${p.y})"><rect class="node-card" rx="10" width="${width}" height="${height}"/><path d="M10,1 H${width-10}" stroke="${p.color||'#d8b773'}" stroke-width="3"/><text class="node-name" x="18" y="29">${escape(title)}</text><text class="note-kind" x="18" y="47">NOTE · только редактор</text><foreignObject x="18" y="65" width="${width-36}" height="${height-90}"><div xmlns="http://www.w3.org/1999/xhtml" class="note-content">${escape(node.text||'Выберите заметку, чтобы написать текст')}</div></foreignObject>${resize}</g>`;
    const inputs=ports.get(name), outputs=outputsFor(name), schema=schemaFor(node), tint=p.color || color(node.type);
    const portMarkup=inputs.map((input,i)=>`<g class="input-port" data-target="${escape(name)}" data-input="${escape(input.key)}"><title>${escape(input.action==='call'?`call: ${input.event} ${name}`:`${input.action}: ${name}`)}</title><circle class="port-hit" cx="0" cy="${82+i*26}" r="13"/><circle class="port port-${input.action}" cx="0" cy="${82+i*26}" r="6"/><text class="input-label" x="17" y="${86+i*26}">${escape(input.label)}</text></g>`).join('')+
      outputs.map((event,i)=>`<g class="output-port" data-source="${escape(name)}" data-output="${escape(event)}"><title>Зажмите и потяните к входу другой ноды</title><circle class="port-hit" cx="${width}" cy="${82+i*26}" r="13"/><circle class="port" cx="${width}" cy="${82+i*26}" r="6"/><text class="event-label" text-anchor="end" x="${width-17}" y="${86+i*26}">${escape(event)}</text></g>`).join('');
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
  $('.graph').querySelectorAll('[data-inline-field]').forEach(input=>input.onchange=()=>safe(()=>{
    const node=model.nodes[input.dataset.inlineNode], field=input.dataset.inlineField, schema=schemaFor(node).properties[field];
    const value=schema.type==='integer'?Number(input.value):input.value;
    if(schema.type==='integer'&&(!Number.isInteger(value)||!input.value||schema.minimum!==undefined&&value<schema.minimum)) throw new Error('Введите допустимое целое число');
    model.setField(input.dataset.inlineNode,field,value);refresh();
  }));
  $('.graph').querySelectorAll('[data-node-lines]').forEach(input=>input.onchange=()=>safe(()=>{
    model.setField(input.dataset.nodeLines,input.dataset.field,input.value?input.value.split('\n'):[]);
    nodeDrafts.delete(JSON.stringify([input.dataset.nodeLines,input.dataset.field]));refresh();
  }));
  $('.graph').querySelectorAll('[data-node-event]').forEach(input=>input.onchange=()=>{
    const name=input.dataset.nodeEvent, field=input.dataset.field;
    trackYaml(commitYaml(input,()=>handlerList(model.nodes[name][field]),values=>model.setField(name,field,values)));
  });
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
  svg.onpointerdown=event=> {
    if (event.button!==0) return;
    if (event.target.closest('.node-fields')) { selected=event.target.closest('[data-node]')?.dataset.node??selected;selectedEdge=null;drawInspector();return; }
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
    if (event.target.closest('.node-fields') && !drag && !linking) return;
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
  svg.onpointercancel=cancelGesture;
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
function commandEditor(values, key) {
  return `<textarea class="command-yaml" data-command-field="${escape(key)}" rows="11" spellcheck="false" placeholder="- bc: Привет всем\n- sound: entity.experience_orb.pickup 1 1\n- '[setblock] 10 10 10 air'">${escape(formatCommands(handlerList(values)))}</textarea><small class="field-error"></small>`;
}
function bindCommandEditor(panel, key, read, write) {
  const input=panel.querySelector(`[data-command-field="${key}"]`); if(!input)return;
  const commit=async()=>{
    const name=selected, draftKey=JSON.stringify([name,key]), error=input.parentElement.querySelector('.field-error');
    const current=model, request=Symbol();yamlRequests.set(draftKey,request);
    invalidFields.set(draftKey,{value:input.value,message:'Проверка YAML…'});
    try {
      const commands=await api('commands/parse',{yaml:input.value});
      if(model!==current || yamlRequests.get(draftKey)!==request || !model.node(name)) return;
      write(replaceEditableCommands(read(),commands));invalidFields.delete(draftKey);refresh();
    } catch(problem) {
      if(model!==current || yamlRequests.get(draftKey)!==request)return;
      invalidFields.set(draftKey,{value:input.value,message:problem.message});
      if(input.isConnected){input.classList.add('invalid');error.textContent=problem.message;}
    }
  };
  input.onchange=()=>trackYaml(commit());
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
    const body = control === 'event' ? commandEditor(value,key) : control === 'lines' || json ? `<textarea data-field="${escape(key)}" data-control="${control ?? 'json'}" rows="${json ? 5 : 3}" spellcheck="false">${escape(display)}</textarea>` : `<input data-field="${escape(key)}" data-control="${field.type}" type="${field.type === 'integer' ? 'number' : 'text'}" value="${escape(display)}" ${field.minimum !== undefined ? `min="${field.minimum}"` : ''} ${field.type === 'integer' ? 'step="1"' : ''} ${required.includes(key) ? 'required' : ''}>`;
    return `<div class="field${draft ? ' error' : ''}"><label>${escape(field.title ?? key)}${required.includes(key) ? ' *' : ''}</label>${body}<small>${escape(control === 'event' ? 'Связи create, remove и destroy соединяются на схеме.' : field.description ?? '')}</small><small class="field-error">${escape(draft?.message ?? '')}</small></div>`;
  }).join('')}<details><summary class="section-label">Полный JSON ноды</summary><textarea id="raw-node" rows="14" spellcheck="false">${escape(JSON.stringify(node, null, 2))}</textarea><small>Неизвестные поля сохраняются.</small><button id="apply-raw" style="margin-top:10px">Применить JSON</button></details><div class="section-label">Исходящие связи</div><div id="inspector-edges"></div>`;
  $('#node-title').onchange = event => { model.setTitle(selected,event.target.value); refresh(); };
  $('#node-color').oninput = event => { model.setColor(selected,event.target.value);drawGraph();updateStatus(); };
  $('#reset-color').onclick = () => {model.setColor(selected,null);refresh();};
  fields.filter(([,field])=>field['x-control']==='event').forEach(([key])=>{const name=selected;bindCommandEditor(panel,key,()=>model.nodes[name][key]??[],values=>model.setField(name,key,values));});
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
  $('#note-text').onchange=event=>{model.change(()=>model.webNodes[selected].text=event.target.value);drawGraph();updateStatus();};
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
  const data = model.payload().settings;
  const result = await dialog(`<h2>Настройки аирдропа</h2><p>Идентификатор, мир, аргументы, генератор и спавнер. Прочие поля файла сохраняются без изменения.</p><textarea class="settings-json" name="settings" spellcheck="false">${escape(JSON.stringify(data, null, 2))}</textarea>`, 'Применить');
  if (!result) return;
  safe(() => {
    const parsed = JSON.parse(result.get('settings'));
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || Object.keys(parsed).some(key => !settingsKeys.includes(key))) throw new Error('Допустимые поля: ' + settingsKeys.join(', '));
    model.change(() => Object.assign(model.config, parsed)); refresh();
  });
}
async function save() {
  if (!model || $('#save').disabled) return;
  const button = $('#save'), current = model; button.disabled = true; button.textContent = 'Проверка…';
  try {
    if (pendingYaml.size) await Promise.all([...pendingYaml]);
    if (model !== current) return;
    if (invalidFields.size) { toast('Исправьте ошибки в полях перед сохранением.', true); return; }
    button.textContent = 'Сохранение…';
    const sent = current.snapshot();
    const document = await api('airdrop', current.payload());
    if (model === current) { current.saved(document, sent); updateStatus(); }
    toast('Сохранено. Создана резервная копия конфига.');
  } catch (error) { toast(error.message, true); }
  finally { if (model === current) { button.disabled = false; button.textContent = 'Сохранить'; } }
}
window.addEventListener('keydown', event => {
  if (!model || $('#dialog').open) return;
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') { event.preventDefault(); document.activeElement?.blur(); save(); return; }
  if (['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName)) return;
  if (event.key === 'Escape') { cancelGesture(); return; }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') { event.preventDefault(); event.shiftKey ? model.redo() : model.undo(); invalidFields.clear(); nodeDrafts.clear(); refresh(); }
  if (event.key === 'Delete') safe(() => { if (selectedEdge) { model.removeEdge(selectedEdge); selectedEdge = null; refresh(); } else if (selected && confirm(`Удалить ноду «${selected}»?`)) { model.remove(selected); selected = null; refresh(); } });
});
const initialKey = location.hash.startsWith('#edit/') ? decodeURIComponent(location.hash.slice(6)) : null;
if (initialKey && token) openEditor(initialKey); else loadHome();
