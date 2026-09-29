export const clone = value => structuredClone(value);
export const settingsKeys = ['id', 'world', 'args', 'generator', 'spawner'];
const validName = name => name.trim() === name && !!name && name.length <= 128 && !/[\s.{}%"'\\]/u.test(name) && !['__proto__', 'prototype', 'constructor'].includes(name);
const object = value => value && typeof value === 'object' && !Array.isArray(value);
const command = /^\[(create|remove\??|rm\??|call\??|destroy)\](?:\s+(.+))?$/;
const firstWord = /^\s*(?:"([^"]+)"|'([^']+)'|([^\s]+))/;
const word = value => firstWord.exec(value)?.slice(1).find(v => v !== undefined);

function reference(action, value) {
  if (typeof value !== 'string') return null;
  const normalized = action.replace(/[?]/g, '').replace(/^rm$/, 'remove');
  if (!['create', 'remove', 'call', 'destroy'].includes(normalized)) return null;
  const tokens = value.trim().match(/"[^"]*"|'[^']*'|\S+/g) ?? [];
  const target = normalized === 'destroy' ? '__destroy__' : normalized === 'call' ? word(tokens[1] ?? '') : word(value);
  if (!target) return null;
  return { action: normalized, target, event: normalized === 'call' ? word(tokens[0]) : null, dynamic: /[{}%]/.test(target) };
}

export function connections(nodes) {
  const result = [];
  for (const [source, node] of Object.entries(nodes)) {
    const walk = (value, path, event) => {
      if (typeof value === 'string') {
        const parsed = command.exec(value);
        const ref = parsed && reference(parsed[1], parsed[2] ?? '');
        if (ref) result.push({ ...ref, source, output: event, path, string: true });
      } else if (Array.isArray(value)) value.forEach((item, i) => walk(item, [...path, i], event));
      else if (value && typeof value === 'object') {
        for (const [key, item] of Object.entries(value)) {
          const ref = reference(key, item);
          if (ref) result.push({ ...ref, source, output: event, path: [...path, key], string: false });
          else if (key === 'weights') { for (const [weight, branch] of Object.entries(item ?? {})) walk(branch,[...path,key,weight],event); }
          else if (['do','else','oneOf','all_players'].includes(key) || key.startsWith('nearby-')) walk(item, [...path, key], event);
        }
      }
    };
    for (const [key, value] of Object.entries(node)) if (key.startsWith('on_')) walk(value, [key], key);
  }
  return result.map(edge => ({ ...edge, id: JSON.stringify([edge.source, ...edge.path]), missing: !Object.hasOwn(nodes, edge.target) }));
}

function parentAt(node, path) {
  return path.slice(0, -1).reduce((current, key) => current[key], node);
}
function removeConnection(nodes, edge) {
  const parent = parentAt(nodes[edge.source], edge.path);
  const key = edge.path.at(-1);
  if (Array.isArray(parent)) parent.splice(key, 1);
  else {
    delete parent[key];
    if (!Object.keys(parent).length && edge.path.length > 1) {
      const container = parentAt(nodes[edge.source], edge.path.slice(0, -1));
      if (Array.isArray(container)) container.splice(edge.path.at(-2), 1);
    }
  }
}
function descendingPaths(a, b) {
  for (let i = 0; i < Math.min(a.path.length, b.path.length); i++) {
    if (a.path[i] !== b.path[i]) return typeof a.path[i] === 'number' && typeof b.path[i] === 'number' ? b.path[i] - a.path[i] : String(b.path[i]).localeCompare(String(a.path[i]));
  }
  return b.path.length - a.path.length;
}

export class GraphModel {
  constructor(document) {
    this.document = clone(document);
    this.config = clone(document.config);
    if (!object(this.config.nodes)) this.config.nodes = {};
    this.metadata = clone(document.metadata ?? {});
    this.metadata.version = 3;
    if (!object(this.metadata.webNodes)) this.metadata.webNodes = {};
    // Older editors stored visual command blocks here. Their connected commands
    // already live in the source handlers; only the redundant projection is removed.
    for (const [id,node] of Object.entries(this.metadata.webNodes)) if (node.type === 'web:commands') delete this.metadata.webNodes[id];
    if (!object(this.metadata.nodes)) this.metadata.nodes = {};
    const view = this.metadata.viewport ?? {};
    this.metadata.viewport = { x: Number.isFinite(view.x) ? view.x : 48, y: Number.isFinite(view.y) ? view.y : 48, zoom: Number.isFinite(view.zoom) ? Math.min(2, Math.max(.25, view.zoom)) : 1 };
    this.undoStack = []; this.redoStack = []; this.dirty = false;
    Object.keys(this.nodes).forEach((name, i) => {
      const point = this.metadata.nodes[name] ?? {};
      this.metadata.nodes[name] = { ...point, x: Number.isFinite(point.x) ? point.x : (i % 3) * 340, y: Number.isFinite(point.y) ? point.y : Math.floor(i / 3) * 285 };
    });
  }
  get nodes() { return this.config.nodes; }
  get webNodes() { return this.metadata.webNodes; }
  allNodes() { return {...this.nodes, ...this.webNodes}; }
  node(name) { return this.nodes[name] ?? this.webNodes[name]; }
  layout(name) { return this.metadata.nodes[name] ?? this.webNodes[name]; }
  title(name) { return this.layout(name)?.title || name; }
  setTitle(name, title) { this.change(() => this.layout(name).title = title); }
  setColor(name, color) {
    if (color && !/^#[0-9a-fA-F]{6}$/.test(color)) throw new Error('Нужен цвет в формате #RRGGBB');
    this.change(() => { if (color) this.layout(name).color = color; else delete this.layout(name).color; });
  }
  isWeb(name) { return Object.hasOwn(this.webNodes, name); }
  newId() { let id; do { id = 'web_' + [...crypto.getRandomValues(new Uint8Array(6))].map(n => n.toString(16).padStart(2,'0')).join(''); } while (this.node(id)); return id; }
  edges() { return connections(this.nodes); }
  addWeb(type, position, title = '') {
    if (type !== 'web:note') throw new Error('Можно добавить только заметку');
    const id = this.newId();
    this.change(() => this.webNodes[id] = {type, x:position.x, y:position.y, title:title || 'Заметка', text:'', width:300, height:200});
    return id;
  }
  setNode(name, value) { this.change(() => this.nodes[name] = value); }
  setField(name, field, value) { this.change(() => this.nodes[name][field] = value); }

  snapshot() { return clone({ config: this.config, metadata: this.metadata }); }
  remember(before) {
    if (JSON.stringify(before) === JSON.stringify(this.snapshot())) return;
    this.undoStack.push(before); if (this.undoStack.length > 100) this.undoStack.shift();
    this.redoStack = []; this.dirty = true;
  }
  change(action) {
    const before = this.snapshot(), dirty = this.dirty;
    try { action(); this.remember(before); }
    catch (error) { this.config = before.config; this.metadata = before.metadata; this.dirty = dirty; throw error; }
  }
  restore(value) { this.config = clone(value.config); this.metadata = clone(value.metadata); this.dirty = true; }
  undo() { if (!this.undoStack.length) return; this.redoStack.push(this.snapshot()); this.restore(this.undoStack.pop()); }
  redo() { if (!this.redoStack.length) return; this.undoStack.push(this.snapshot()); this.restore(this.redoStack.pop()); }
  add(name, type, schema, position) {
    if (!validName(name)) throw new Error('Имя ноды: без пробелов, точек, кавычек и плейсхолдеров');
    if (this.node(name)) throw new Error('Нода с таким именем уже существует');
    this.change(() => {
      const node = { type };
      for (const [key, field] of Object.entries(schema.properties ?? {})) if (key !== 'type' && Object.hasOwn(field, 'default')) node[key] = clone(field.default);
      this.nodes[name] = node;
      this.metadata.nodes[name] = position;
    });
  }
  rename(old, name) {
    if (!validName(name)) throw new Error('Имя ноды: без пробелов, точек, кавычек и плейсхолдеров');
    if (name === old) return;
    if (this.node(name)) throw new Error('Нода с таким именем уже существует');
    if (['__init__', '__destroy__'].includes(old)) throw new Error('Служебную ноду нельзя переименовать');
    this.change(() => {
      for (const edge of connections(this.nodes)) {
        if (edge.target !== old || edge.dynamic) continue;
        const parent = parentAt(this.nodes[edge.source], edge.path), key = edge.path.at(-1);
        const value = parent[key];
        // Replace the target token, keeping the event name and command options intact.
        const start = edge.string ? command.exec(value)[0].length - command.exec(value)[2].length : 0;
        const tail = value.slice(start);
        const tokens = [...tail.matchAll(/"[^"]*"|'[^']*'|\S+/g)];
        const token = tokens[edge.action === 'call' ? 1 : 0];
        if (token) parent[key] = value.slice(0, start + token.index) + name + value.slice(start + token.index + token[0].length);
      }
      this.nodes[name] = this.nodes[old]; delete this.nodes[old];
      this.metadata.nodes[name] = this.metadata.nodes[old]; delete this.metadata.nodes[old];
    });
  }
  remove(name) {
    if (this.isWeb(name)) { this.change(() => delete this.webNodes[name]); return; }
    if (['__init__', '__destroy__'].includes(name)) throw new Error('Служебную ноду нельзя удалить');
    this.change(() => {
      connections(this.nodes).filter(e => e.target === name && e.source !== name && !e.dynamic).sort(descendingPaths).forEach(e => removeConnection(this.nodes, e));
      delete this.nodes[name]; delete this.metadata.nodes[name];
    });
  }
  connect(source, output, target, action = 'create', event = 'update') {
    if (!Object.hasOwn(this.nodes, source) || !this.node(target)) throw new Error('Нода не найдена');
    if (target === '__destroy__' && action !== 'destroy') throw new Error('Для __destroy__ используйте вход destroy');
    if (action === 'destroy' && target !== '__destroy__') throw new Error('Вход destroy есть только у __destroy__');
    this.change(() => {
      const node = this.nodes[source], current = node[output];
      const list = current === undefined ? [] : Array.isArray(current) ? current : [current];
      if (this.isWeb(target)) throw new Error('Заметка не выполняется и не имеет входов');
      list.push({ [action]: action === 'destroy' ? '' : action === 'call' ? `${event} ${target}` : target });
      node[output] = list;
    });
  }
  removeEdge(edge) { this.change(() => removeConnection(this.nodes,edge)); }
  reconnect(edge, target, action = 'create', event = 'update') {
    if (target === '__destroy__' && action !== 'destroy') throw new Error('Для __destroy__ используйте вход destroy');
    if (action === 'destroy' && target !== '__destroy__') throw new Error('Вход destroy есть только у __destroy__');
    if (edge.target === target && edge.action === action && (action !== 'call' || edge.event === event)) return;
    if (this.isWeb(target)) throw new Error('Заметка не имеет входов');
    this.change(() => {
        const parent = parentAt(this.nodes[edge.source], edge.path), key = edge.path.at(-1);
        const previous = parent[key];
        if (edge.action === action) {
          const start = edge.string ? command.exec(previous)[0].length - command.exec(previous)[2].length : 0;
          const tokens = [...previous.slice(start).matchAll(/"[^"]*"|'[^']*'|\S+/g)];
          const token = tokens[action === 'call' ? 1 : 0];
          let changed = previous.slice(0,start+token.index) + target + previous.slice(start+token.index+token[0].length);
          if (action === 'call') { const first = tokens[0]; changed = changed.slice(0,start+first.index) + event + changed.slice(start+first.index+first[0].length); }
          parent[key] = changed;
        } else {
          const value = action === 'destroy' ? '' : action === 'call' ? `${event} ${target}` : target;
          if (edge.string) parent[key] = `[${action}] ${value}`;
          else {
            if (Object.hasOwn(parent,action)) throw new Error('В этом обработчике уже есть такая связь. Удалите старую связь на схеме.');
            const entries = Object.entries(parent).map(([k,v]) => k === key ? [action,value] : [k,v]);
            for (const k of Object.keys(parent)) delete parent[k]; Object.assign(parent,Object.fromEntries(entries));
          }
        }
    });
  }
  disconnectIncoming(name) {
    const edges = this.edges().filter(e => e.target === name && !e.dynamic);
    edges.sort((a,b) => String(b.source).localeCompare(String(a.source)) || descendingPaths(a,b));
    this.change(() => {
      for (const edge of edges) removeConnection(this.nodes,edge);
    });
  }
  payload() {
    return { key: this.document.key, revision: this.document.revision, nodes: clone(this.nodes), metadata:clone(this.metadata),
      settings: Object.fromEntries(settingsKeys.filter(k => Object.hasOwn(this.config, k)).map(k => [k, clone(this.config[k])])) };
  }
  saved(document, sent = this.snapshot()) { this.document = clone(document); this.dirty = JSON.stringify(sent) !== JSON.stringify(this.snapshot()); }
}
