import test from 'node:test';
import assert from 'node:assert/strict';
import {GraphRenderer} from '../src/graph-renderer.js';

// An SVG fixture records writes without implementing layout or replacing controls.
function element(dataset = {}, children = {}) {
  return {dataset, attrs:{}, writes:0, style:{},
    classList:{toggle() {}},
    setAttribute(key,value) { this.attrs[key]=String(value); this.writes++; },
    querySelector:selector=>children[selector] ?? null,
    querySelectorAll:selector=>children[selector] ?? []};
}
function fixture() {
  const nodes = new Map(['a','b','c','d'].map((name,i)=>[name,{
    layout:{x:i*400,y:i*100},width:340,height:310,outputs:['on_create','on_tick']
  }]));
  const cards = [...nodes.keys()].map(name=>element({node:name}));
  const edges = [{id:'ab',source:'a',target:'b',output:'on_tick',row:2,sibling:1},
    {id:'cd',source:'c',target:'d',output:'on_create',row:0,sibling:0},
    {id:'aa',source:'a',target:'a',output:'on_create',row:0,sibling:0}];
  const wires=edges.map(edge=>element({edge:edge.id},{path:[element(),element()],text:element()}));
  const handles=edges.map(edge=>element({reconnect:edge.id},{circle:[element(),element()]}));
  const scene=element();
  const svg=element({}, {'#scene':scene,'[data-node]':cards,'.wire':wires,'[data-reconnect]':handles});
  const curve=(x,y,tx,ty)=>`${x},${y}->${tx},${ty}`;
  return {nodes,cards,wires,handles,scene,renderer:new GraphRenderer(svg,nodes,edges,curve)};
}

test('pan and zoom retain nodes and do not touch any wires',()=>{
  const f=fixture();
  f.renderer.viewport({x:100,y:-50,zoom:.5});
  assert.equal(f.scene.attrs.transform,'translate(100,-50) scale(0.5)');
  assert.ok(f.cards.every(card=>card.writes===0));
  assert.ok(f.wires.every(wire=>wire.querySelectorAll('path').every(path=>path.writes===0)));
});

test('moving a group updates only incident wires once, including self-links and endpoint offsets',()=>{
  const f=fixture(), control=element();
  f.cards[0].control=control;
  Object.assign(f.nodes.get('a').layout,{x:100,y:50});
  Object.assign(f.nodes.get('b').layout,{x:600,y:200});
  f.renderer.move(['a','b']);
  assert.equal(f.cards[0].attrs.transform,'translate(100,50)');
  assert.equal(f.cards[1].attrs.transform,'translate(600,200)');
  assert.equal(f.cards[0].control,control);
  const ab=f.wires[0].querySelectorAll('path');
  assert.equal(ab[0].attrs.d,'440,158->564,334 L600,334');
  assert.equal(ab[0].writes,1);
  assert.equal(ab[1].attrs.d,ab[0].attrs.d);
  assert.equal(f.handles[0].querySelectorAll('circle')[0].attrs.cx,'564');
  assert.equal(f.wires[2].querySelectorAll('path')[0].attrs.d,'440,132->78,123 L100,132');
  assert.equal(f.wires[2].querySelectorAll('path')[0].writes,1);
  assert.equal(f.cards[2].writes,0);
  assert.equal(f.wires[1].querySelectorAll('path')[0].writes,0);
});
