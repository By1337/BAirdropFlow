const attributes = (element, values) => {
  if (!element) return;
  for (const [key, value] of Object.entries(values)) element.setAttribute(key, value);
};

// Keep form controls and CodeMirror attached while changing graph geometry.
export class GraphRenderer {
  constructor(svg, nodes, edges, curve) {
    this.svg = svg;
    this.scene = svg.querySelector('#scene');
    this.nodes = nodes;
    this.curve = curve;
    this.adjacent = new Map();
    const cards = new Map([...svg.querySelectorAll('[data-node]')].map(el => [el.dataset.node, el]));
    for (const [name, node] of nodes) node.element = cards.get(name);
    const wires = new Map([...svg.querySelectorAll('.wire')].map(el => [el.dataset.edge, el]));
    const handles = new Map([...svg.querySelectorAll('[data-reconnect]')].map(el => [el.dataset.reconnect, el]));
    for (const edge of edges) {
      const wire = wires.get(edge.id), handle = handles.get(edge.id);
      if (!wire || !handle) continue;
      const entry = {edge, paths:wire.querySelectorAll('path'), label:wire.querySelector('text'), circles:handle.querySelectorAll('circle')};
      for (const name of new Set([edge.source, edge.target])) {
        if (!this.adjacent.has(name)) this.adjacent.set(name, new Set());
        this.adjacent.get(name).add(entry);
      }
    }
  }

  viewport(view) {
    this.scene.setAttribute('transform', `translate(${view.x},${view.y}) scale(${view.zoom})`);
  }

  move(names) {
    const edges = new Set();
    for (const name of names) {
      const node = this.nodes.get(name);
      node.element.setAttribute('transform', `translate(${node.layout.x},${node.layout.y})`);
      for (const edge of this.adjacent.get(name) ?? []) edges.add(edge);
    }
    for (const entry of edges) this.updateEdge(entry);
  }

  updateEdge({edge, paths, label, circles}) {
    const source = this.nodes.get(edge.source), target = this.nodes.get(edge.target);
    const x = source.layout.x + source.width, y = source.layout.y + 82 + source.outputs.indexOf(edge.output)*26;
    const tx = target.layout.x, ty = target.layout.y + 82 + edge.row*26;
    const hx = tx - 22 - (edge.sibling%5)*14, hy = ty + (edge.sibling%3-1)*9;
    const d = this.curve(x,y,hx,hy) + ` L${tx},${ty}`;
    for (const path of paths) path.setAttribute('d', d);
    attributes(label, {x:(x+hx)/2, y:(y+hy)/2-8});
    for (const circle of circles) attributes(circle, {cx:hx, cy:hy});
  }

  resize(name, width, height, top) {
    const node = this.nodes.get(name), el = node.element;
    node.width = width; node.height = height;
    attributes(el.querySelector('.node-card'), {width, height});
    attributes(el.querySelector('.node-divider'), {x2:width});
    attributes(el.querySelector(':scope > path'), {d:`M10,1 H${width-10}`});
    const inset = node.type === 'web:note' || node.type === 'default:commands' ? 18 : 14;
    attributes(el.querySelector('foreignObject'), {width:width-inset*2, height:Math.max(20,height-top-25)});
    attributes(el.querySelector('.note-resize rect'), {x:width-25, y:height-25});
    attributes(el.querySelector('.note-resize path'), {d:`M${width-18},${height-6} L${width-6},${height-18} M${width-12},${height-6} L${width-6},${height-12}`});
    for (const port of el.querySelectorAll('.output-port')) {
      for (const circle of port.querySelectorAll('circle')) circle.setAttribute('cx', width);
      port.querySelector('text').setAttribute('x', width-17);
    }
    for (const entry of this.adjacent.get(name) ?? []) this.updateEdge(entry);
  }

  selection(selected) {
    for (const [name, node] of this.nodes) node.element.classList.toggle('selected', selected.has(name));
  }

  box(selector, box) {
    const el = this.svg.querySelector(selector);
    el.style.display = box ? '' : 'none';
    if (box) attributes(el, box);
  }

  preview(linking) {
    const path = this.svg.querySelector('.connection-preview'), tip = this.svg.querySelector('.preview-tip');
    path.style.display = tip.style.display = linking ? '' : 'none';
    if (!linking) return;
    const node = this.nodes.get(linking.source);
    path.setAttribute('d', this.curve(node.layout.x+node.width, node.layout.y+82+node.outputs.indexOf(linking.output)*26, linking.point.x, linking.point.y));
    attributes(tip, {cx:linking.point.x, cy:linking.point.y});
  }
}
