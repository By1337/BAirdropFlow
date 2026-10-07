import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createContext, runInContext} from 'node:vm';
import {GraphModel, clone, defaultsNodeId} from '../src/model.js';

const source = readFileSync(new URL('../src/app.js', import.meta.url), 'utf8');
const functions = ['selectNode', 'graphPoint', 'cancelGesture', 'setupGraph'].map(name => {
  const start = source.indexOf(`function ${name}(`);
  const end = source.indexOf('\nfunction ', start + 1);
  return source.slice(start, end);
}).join('\n');

// Exercise the actual pointer handlers with a minimal DOM and the real history model.
function editor(zoom = 1) {
  const model = new GraphModel({config:{nodes:{a:{type:'tick:timer'}, b:{type:'block:click'}, c:{type:'default:gps'}}},
    metadata:{nodes:{a:{x:10,y:20},b:{x:100,y:200},c:{x:300,y:400}},
      webNodes:{note:{type:'web:note',x:500,y:600,width:300,height:200}},viewport:{x:0,y:0,zoom}}});
  const captured = new Set();
  const svg = {setPointerCapture:id=>captured.add(id),hasPointerCapture:id=>captured.has(id),releasePointerCapture:id=>captured.delete(id),
    getBoundingClientRect:()=>({left:0,top:0})};
  const context = createContext({model, clone, AbortController,
    document:{addEventListener() {}}, $:()=>svg,
    setupMinimap() {}, closeContext() {}, drawGraph() {}, drawInspector() {}, refresh() {},
    nodeWidth:()=>300, heightFor:()=>200, bodyTop:()=>60});
  runInContext(`let selected, selectedEdge, drag, linking, graphListeners;
    const selectedNodes = new Set(); let graphView = clone(model.metadata.viewport);
    ${functions}
    setupGraph();`, context);
  const event = (name, options = {}) => ({button:0,pointerId:1,clientX:0,clientY:0,shiftKey:false,preventDefault() {},
    target:{closest:selector=>selector==='[data-node]' && name ? {dataset:{node:name}} :
      selector==='[data-selection-drag]' && options.group ? {} :
      selector==='.node-fields' && options.fields ? {} :
      selector==='[data-resize]' && options.resize ? {dataset:{resize:name}} : null}, ...options});
  return {model,
    viewport(x,y) { runInContext(`graphView.x=${x};graphView.y=${y};`,context); },
    click(name, shiftKey = false) { svg.onpointerdown(event(name,{shiftKey})); svg.onpointerup(event(name,{shiftKey})); },
    down(name, options) { svg.onpointerdown(event(name,options)); },
    move(clientX, clientY) { svg.onpointermove(event(null,{clientX,clientY})); },
    up() { svg.onpointerup(event(null)); },
    cancel() { svg.onpointercancel(event(null)); },
    selection() { return Array.from(runInContext('selectedNodes', context)); }};
}

test('Shift-click toggles nodes and dragging any selected node moves the group in one undo step', () => {
  const e = editor(2);
  e.click('a'); e.click('b',true);
  assert.deepEqual(e.selection(),['a','b']);
  assert.equal(e.model.undoStack.length,0);
  e.down('a'); e.move(80,40); e.move(100,60); e.up();
  assert.deepEqual(e.model.layout('a'),{x:60,y:50});
  assert.deepEqual(e.model.layout('b'),{x:150,y:230});
  assert.deepEqual(e.model.layout('c'),{x:300,y:400});
  assert.equal(e.model.undoStack.length,1);
  e.model.undo();
  assert.deepEqual(e.model.layout('a'),{x:10,y:20});
  assert.deepEqual(e.model.layout('b'),{x:100,y:200});
  e.model.redo();
  assert.deepEqual(e.model.layout('b'),{x:150,y:230});
  assert.deepEqual(e.selection(),['a','b']);
  e.click('b',true);
  assert.deepEqual(e.selection(),['a']);
  e.click('c');
  assert.deepEqual(e.selection(),['c']);
  e.click(null);
  assert.deepEqual(e.selection(),[]);
});

test('group movement includes notes and defaults and cancellation restores all layouts without history', () => {
  const e = editor(.5);
  e.click('a',true); e.click('note',true); e.click(defaultsNodeId,true);
  const before = e.model.snapshot();
  e.down('note'); e.move(25,50);
  assert.equal(e.model.layout('a').x,60);
  assert.equal(e.model.layout('note').y,700);
  assert.equal(e.model.layout(defaultsNodeId).x,-330);
  e.cancel();
  assert.deepEqual(e.model.snapshot(),before);
  assert.equal(e.model.undoStack.length,0);
  assert.equal(e.model.dirty,false);
  e.down('a'); e.move(25,50); e.up();
  assert.equal(e.model.undoStack.length,1);
  const saved = new GraphModel({...e.model.document,config:e.model.config,metadata:e.model.payload().metadata});
  assert.equal(saved.layout('note').y,700);
  assert.equal(saved.layout(defaultsNodeId).x,-330);
});

test('resizing one member keeps the other selected nodes stationary', () => {
  const e = editor();
  e.click('a'); e.click('b',true);
  e.down('b',{resize:true}); e.move(50,30); e.up();
  assert.deepEqual(e.selection(),['a','b']);
  assert.deepEqual(e.model.layout('a'),{x:10,y:20});
  assert.deepEqual(e.model.layout('b'),{x:100,y:200,width:350,height:230});
});

test('Shift-drag on empty space selects overlapping nodes and keeps the group after release', () => {
  const e = editor(2);
  const before = e.model.snapshot();
  e.down(null,{shiftKey:true});
  e.move(500,500); // Shift can already be released while the mouse remains down.
  assert.deepEqual(e.selection(),['a','b']);
  e.up();
  assert.deepEqual(e.selection(),['a','b']);
  assert.deepEqual(e.model.snapshot(),before);
  assert.equal(e.model.dirty,false);
  assert.equal(e.model.undoStack.length,0);
  // The body, including form fields, drags the selected group instead of editing a field.
  e.down('a',{fields:true}); e.move(100,40); e.up();
  assert.deepEqual(e.model.layout('a'),{x:60,y:40});
  assert.deepEqual(e.model.layout('b'),{x:150,y:220});
  assert.deepEqual(e.selection(),['a','b']);
  assert.equal(e.model.undoStack.length,1);
  e.model.undo();
  assert.deepEqual(e.model.snapshot(),before);
  e.click(null);
  assert.deepEqual(e.selection(),[]);
});

test('marquee works in reverse at a panned viewport and can be cancelled without losing the previous selection', () => {
  const e = editor(.5);
  e.viewport(40,80);
  e.click('c');
  e.down(null,{shiftKey:true,clientX:165,clientY:205}); // graph point 250,250
  e.move(40,80); // graph point 0,0
  assert.deepEqual(e.selection(),['a','b']);
  e.cancel();
  assert.deepEqual(e.selection(),['c']);
  assert.equal(e.model.undoStack.length,0);
  e.down(null,{shiftKey:true,clientX:165,clientY:205});
  e.move(40,80); e.up();
  assert.deepEqual(e.selection(),['a','b']);
  // Dragging the empty area inside the group frame also moves every selected node.
  e.down(null,{group:true}); e.move(25,10); e.up();
  assert.deepEqual(e.model.layout('a'),{x:60,y:40});
  assert.deepEqual(e.model.layout('b'),{x:150,y:220});
  assert.deepEqual(e.selection(),['a','b']);
});
