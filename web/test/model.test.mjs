import test from 'node:test';
import assert from 'node:assert/strict';
import { GraphModel, connections, defaultsNodeId } from '../src/model.js';

function fixture() {
  return { key:'events.yml', revision:'original', config:{ id:'demo', world:'world', custom:{ preserved:true }, nodes:{
    __init__:{ type:'__init__', on_init:[{ weights:{ 20:[{ create:'timer' }, { call:'update timer' }], 80:[{ bc:'leave this alone' }] } }, '[remove?] timer', { create:'timer --anon' }, { create:'{args:node}' }] },
    timer:{ type:'tick:timer', timer:30, on_timer_end:[{ remove:'timer' }] }, __destroy__:{ type:'__destroy__' }
  } }, metadata:{} };
}
test('discovers nested EventDSL references and keeps dynamic commands distinct', () => {
  const edges = connections(fixture().config.nodes);
  assert.equal(edges.length, 6);
  assert.equal(edges.filter(e => e.dynamic).length, 1);
  assert.deepEqual(edges[1].path, ['on_init', 0, 'weights', '20', 1, 'call']);
  assert.equal(edges[1].event, 'update');
});
test('rename updates nested commands, string commands and options without touching other handlers', () => {
  const model = new GraphModel(fixture()); model.rename('timer', 'opening');
  assert.equal(model.nodes.__init__.on_init[0].weights[20][0].create, 'opening');
  assert.equal(model.nodes.__init__.on_init[0].weights[20][1].call, 'update opening');
  assert.equal(model.nodes.__init__.on_init[1], '[remove?] opening');
  assert.equal(model.nodes.__init__.on_init[2].create, 'opening --anon');
  assert.equal(model.nodes.__init__.on_init[0].weights[80][0].bc, 'leave this alone');
  assert.equal(model.nodes.__init__.on_init[3].create, '{args:node}');
  assert.ok(model.metadata.nodes.opening);
  model.undo(); assert.ok(model.nodes.timer); model.redo(); assert.ok(model.nodes.opening);
});
test('deleting a node removes all static incoming commands without damaging weighted branches', () => {
  const model = new GraphModel(fixture()); model.remove('timer');
  assert.deepEqual(model.nodes.__init__.on_init[0].weights[20], []);
  assert.equal(model.nodes.__init__.on_init[0].weights[80][0].bc, 'leave this alone');
  assert.equal(connections(model.nodes).length, 1);
  assert.equal(connections(model.nodes)[0].dynamic, true);
  model.undo(); assert.equal(connections(model.nodes).length, 6);
});
test('adding and disconnecting a graph edge preserves existing event object', () => {
  const model = new GraphModel(fixture()); model.nodes.timer.on_tick = { bc:'hello' };
  model.connect('timer', 'on_tick', '__destroy__', 'destroy');
  assert.deepEqual(model.nodes.timer.on_tick, [{ bc:'hello' }, { destroy:'' }]);
  model.removeEdge(connections(model.nodes).find(e => e.output === 'on_tick'));
  assert.deepEqual(model.nodes.timer.on_tick, [{ bc:'hello' }]);
});
test('payload owns only editable settings and keeps revisions and positions', () => {
  const model = new GraphModel(fixture());
  model.add('new_timer', 'tick:timer', { properties:{ timer:{ default:20 }, on_create:{ default:[] } } }, {x:3,y:8});
  const payload = model.payload();
  assert.equal(payload.revision, 'original'); assert.equal(payload.settings.id, 'demo');
  assert.equal(payload.settings.custom, undefined); assert.deepEqual(payload.metadata.nodes.new_timer, {x:3,y:8});
  assert.equal(payload.nodes.new_timer.timer, 20);
  assert.throws(() => model.rename('__init__', 'other'));
  assert.throws(() => model.add('a.b', 'tick:timer', {}, {}));
  assert.throws(() => model.add('__proto__', 'tick:timer', {}, {}));
});
test('generator edits are saved in settings and support undo', () => {
  const model = new GraphModel(fixture());
  model.setGenerator({type:'generator:fixed',at:'10;90;20'});
  assert.deepEqual(model.payload().settings.generator,{type:'generator:fixed',at:'10;90;20'});
  model.undo();
  assert.equal(model.payload().settings.generator,undefined);
  model.redo();
  assert.equal(model.payload().settings.generator.at,'10;90;20');
});
test('spawner and default args edits travel together in settings', () => {
  const model = new GraphModel(fixture());
  model.setArgs({type:'обычный'});
  model.setSpawner({type:'spawner:timer',time:'5m',when:{'2m':[{bc:'Скоро'}]},on_timer_end:[{make:{type:'{value:type}'}}]});
  assert.equal(model.payload().settings.args.type,'обычный');
  assert.equal(model.payload().settings.spawner.when['2m'][0].bc,'Скоро');
  model.undo();
  assert.equal(model.payload().settings.spawner,undefined);
  assert.equal(model.payload().settings.args.type,'обычный');
});
test('edits made during a pending save remain dirty after response', () => {
  const model = new GraphModel(fixture()); const sent = model.snapshot();
  model.change(() => model.nodes.timer.timer = 35);
  model.saved({ ...fixture(), revision:'new' }, sent);
  assert.equal(model.document.revision, 'new'); assert.equal(model.dirty, true); assert.equal(model.nodes.timer.timer, 35);
  model.saved({ ...fixture(), revision:'newer' }, model.snapshot()); assert.equal(model.dirty, false);
});
test('invalid layout metadata falls back to usable coordinates', () => {
  const document = fixture(); document.metadata = { viewport:{x:'bad',y:null,zoom:0}, nodes:{timer:{x:Infinity,y:'bad'}} };
  const model = new GraphModel(document);
  assert.deepEqual(model.metadata.viewport, {x:48,y:48,zoom:.25});
  assert.ok(Number.isFinite(model.metadata.nodes.timer.x));
  assert.ok(Number.isFinite(model.metadata.nodes.timer.y));
});

function commandFixture() {
  return {key:'commands.yml',revision:'one',metadata:{},config:{id:'commands',nodes:{
    __init__:{type:'__init__',on_init:[{setblock:'{x} {y} {z} air'},{bc:'first'},{create:'timer'},{weights:{20:[{bc:'weighted'},{create:'timer'}],80:[{if:'[check] example',do:[{setblock:'stone'},{create:'timer'}],else:{bc:'fallback'}}]}},{bc:'last'}]},
    timer:{type:'tick:timer',timer:20},__destroy__:{type:'__destroy__',on_destroy:[]}
  }}};
}
test('commands stay in their handlers and retain their order',()=>{
  const doc=commandFixture(),model=new GraphModel(doc);
  assert.deepEqual(model.payload().nodes,doc.config.nodes);
  assert.deepEqual(model.edges().map(e=>e.action),['create','create','create']);
  assert.deepEqual(model.webNodes,{});
  const changed=structuredClone(model.nodes.__init__.on_init);
  changed.splice(0,2,{setblock:'gold_block'},{bc:'changed'},{bc:'extra'});
  model.setField('__init__','on_init',changed);
  assert.deepEqual(model.nodes.__init__.on_init.slice(0,4),[{setblock:'gold_block'},{bc:'changed'},{bc:'extra'},{create:'timer'}]);
  assert.equal(model.nodes.__init__.on_init[4].weights[20][0].bc,'weighted');
  model.undo();assert.deepEqual(model.nodes,doc.config.nodes);
  model.redo();assert.deepEqual(model.payload().nodes.__init__.on_init,changed);
});
test('old visual command blocks disappear while their handler commands remain',()=>{
  const doc=commandFixture();
  doc.metadata.webNodes={web_old:{type:'web:commands',title:'Команды',bindings:[{source:'__init__',output:'on_init'}]},web_note:{type:'web:note',title:'Заметка',text:'Текст',x:2,y:4}};
  const model=new GraphModel(doc);
  assert.equal(model.webNodes.web_old,undefined);
  assert.equal(model.webNodes.web_note.text,'Текст');
  assert.deepEqual(model.payload().nodes,doc.config.nodes);
  assert.equal(model.payload().metadata.webNodes.web_old,undefined);
  assert.throws(()=>model.addWeb('web:commands',{x:0,y:0}));
});
test('direct handler edits keep nested branches and their links',()=>{
  const model=new GraphModel(commandFixture());
  const changed=structuredClone(model.nodes.__init__.on_init);
  changed[3].weights[80][0].do[0]={setblock:'diamond_block'};
  model.setField('__init__','on_init',changed);
  const branch=model.nodes.__init__.on_init[3].weights[80][0];
  assert.equal(branch.if,'[check] example');assert.equal(branch.do[0].setblock,'diamond_block');
  assert.equal(branch.do[1].create,'timer');assert.deepEqual(branch.else,{bc:'fallback'});
  assert.equal(model.edges().length,3);
});
test('friendly titles and resizable notes live only in metadata and support undo',()=>{
  const model=new GraphModel(commandFixture());model.setTitle('timer','До открытия');
  const note=model.addWeb('web:note',{x:2,y:4});
  model.change(()=>Object.assign(model.webNodes[note],{text:'Не забудь лут',width:450,height:250}));
  const payload=model.payload();assert.equal(payload.nodes.timer.title,undefined);assert.equal(payload.nodes[note],undefined);
  assert.equal(payload.metadata.nodes.timer.title,'До открытия');assert.equal(payload.metadata.webNodes[note].width,450);
  model.undo();assert.equal(model.webNodes[note].width,300);model.redo();assert.equal(model.webNodes[note].text,'Не забудь лут');
});
test('reattaching links changes the existing command in place and preserves optional flags',()=>{
  const model=new GraphModel(fixture());model.add('other','tick:timer',{},{});
  const edge=model.edges().find(e=>e.string);
  model.reconnect(edge,'other','remove');assert.equal(model.nodes.__init__.on_init[1],'[remove?] other');
  const create=model.edges().find(e=>!e.string&&e.path[1]===2);
  model.reconnect(create,'other','create');assert.equal(model.nodes.__init__.on_init[2].create,'other --anon');
  const call=model.edges().find(e=>e.action==='call');model.reconnect(call,'other','call','refresh');
  assert.equal(model.nodes.__init__.on_init[0].weights[20][1].call,'refresh other');
});
test('plain message strings and command parameter maps are not mistaken for graph links',()=>{
  assert.deepEqual(connections({a:{on_init:['create b',{make:{create:'b'}},{bc:'[create] b'}]}}),[]);
});
test('disconnecting incoming links leaves surrounding commands and supports undo',()=>{
  const model=new GraphModel(commandFixture());
  model.disconnectIncoming('timer');
  assert.equal(model.edges().filter(e=>e.target==='timer').length,0);
  assert.equal(model.nodes.__init__.on_init[0].setblock,'{x} {y} {z} air');
  assert.equal(model.nodes.__init__.on_init.at(-1).bc,'last');
  model.undo();assert.equal(model.edges().filter(e=>e.target==='timer').length,3);
});

test('defaults project a single portless editor node and persist separately from executable nodes', () => {
  const document = fixture(); document.config.defaults = {delay:'45'};
  document.metadata.defaultsNode = {x:17,y:29};
  const model = new GraphModel(document);
  assert.equal(model.node(defaultsNodeId).type, 'web:defaults');
  assert.equal(model.isWeb(defaultsNodeId), true);
  assert.deepEqual(model.layout(defaultsNodeId), {x:17,y:29});
  model.setDefaults({delay:'60',position:'0;100;0'});
  model.setField('timer', 'timer', '{delay}');
  const payload = model.payload();
  assert.deepEqual(payload.settings.defaults, {delay:'60',position:'0;100;0'});
  assert.equal(payload.nodes.timer.timer, '{delay}');
  assert.equal(Object.hasOwn(payload.nodes, defaultsNodeId), false);
  assert.equal(Object.hasOwn(payload.metadata.webNodes, defaultsNodeId), false);
  assert.throws(() => model.connect('timer', 'on_tick', defaultsNodeId));
  assert.throws(() => model.remove(defaultsNodeId));
  model.undo(); model.undo(); assert.deepEqual(model.defaults, {delay:'45'});
  model.redo(); assert.equal(model.defaults.delay,'60');
  const loaded = new GraphModel({...document,config:{...document.config,...payload.settings,nodes:payload.nodes},metadata:payload.metadata});
  assert.deepEqual(loaded.defaults,payload.settings.defaults);
  assert.deepEqual(loaded.layout(defaultsNodeId),payload.metadata.defaultsNode);
});
