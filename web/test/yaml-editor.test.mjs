import test from 'node:test';
import assert from 'node:assert/strict';
import {EditorState} from '@codemirror/state';
import {snippetCompletion} from '@codemirror/autocomplete';
import {completionAt} from '../src/event-dsl-completion.js';
import {yamlNewline,insertYamlNewline,yamlLanguageExtensions,intellijKeymap} from '../src/yaml-editor.js';
const complete=(doc,options={})=>completionAt(doc,doc.length,{explicit:true,...options});
const labels=result=>result.options.map(item=>item.label);

test('command and parameter suggestions follow root and nested command contexts',()=>{
  assert.ok(labels(complete('- so')).includes('sound'));
  assert.match(complete('- sound: ').options[0].detail,/sound/);
  assert.ok(labels(complete('- nearby: 10 ')).includes('[sound]'));
  assert.ok(labels(complete('- nearby: 10 [so')).includes('sound'));
  assert.ok(labels(complete('- nearby: 10 [sound] block.note')).includes('block.note_block.pling'));
  assert.ok(labels(complete('- nearby: 10 [nearby] 5 [sound] block.note')).includes('block.note_block.pling'));
  assert.ok(labels(complete('- "[sound] block.note')).includes('block.note_block.pling'));
});
test('completion replaces an existing closing bracket or colon without duplicating it',()=>{
  const bracket='- nearby: 10 [so]';
  const found=completionAt(bracket,bracket.length-1);
  assert.equal(found.to,bracket.length);
  const option=found.options.find(item=>item.label==='sound');
  assert.equal(bracket.slice(0,found.from)+option.apply+bracket.slice(found.to),'- nearby: 10 [sound] ');
  const colon='- so: ';
  const key=completionAt(colon,4,{explicit:true});
  assert.equal(key.to,colon.length);
  assert.equal(colon.slice(0,key.from)+key.options.find(item=>item.label==='sound').apply+colon.slice(key.to),'- sound: ');
});
test('node IDs, command arguments and placeholders use the current workflow',()=>{
  assert.deepEqual(labels(complete('- create: loot',{nodes:['loot','ttl']})),['loot','ttl']);
  assert.deepEqual(labels(complete('- call: update lo',{nodes:['loot']})),['loot']);
  assert.ok(labels(complete('- sound: block.note_block.pling ')).includes('1'));
  assert.ok(labels(complete('- message: "{args:co',{args:['count']})).includes('{args:count}'));
});
test('spawner-only actions are offered in the spawner',()=>{
  assert.ok(!labels(complete('- ')).includes('make'));
  assert.ok(labels(complete('- ',{spawner:true})).includes('make'));
  assert.ok(labels(complete('- set_value: loot',{spawner:true})).includes('loot_table'));
  assert.match(complete('  - make: ',{spawner:true}).options[0].apply,/\n      loot_table:/);
});
test('comments and literal message blocks do not offer commands',()=>{
  assert.equal(complete('  # - sound'),null);
  assert.equal(complete('- message: |\n    line\n    - so'),null);
});
test('structure snippets preserve YAML indentation at nested levels',()=>{
  const doc='- if: condition\n  do:\n    - we';
  const result=complete(doc),option=result.options.find(item=>item.label==='weights');
  let state=EditorState.create({doc,selection:{anchor:doc.length},extensions:yamlLanguageExtensions});
  snippetCompletion(option.snippet,option).apply({state,dispatch:tr=>state=state.update(tr).state},option,result.from,doc.length);
  assert.match(state.doc.toString(),/    - weights:\n        "10":\n          - message:/);
  assert.ok(labels(complete('- ')).includes('oneOf'));
  assert.ok(labels(complete('  do')).includes('do'));
});
test('Enter adds two spaces to mappings and respects sequence mapping columns',()=>{
  assert.equal(yamlNewline('abc:',4),'\n  ');
  assert.equal(yamlNewline('  abc:',6),'\n    ');
  assert.equal(yamlNewline('- weights:',10),'\n    ');
  assert.equal(yamlNewline('- if: yes',9),'\n  ');
  assert.equal(yamlNewline('  do:',5),'\n    ');
  assert.equal(yamlNewline('- message: |',12),'\n    ');
  assert.equal(yamlNewline('    - console: say ok',21),'\n    - ');
  const message='- message: |\n    text: colon';
  assert.equal(yamlNewline(message,message.length),'\n    ');
  const listText='- message: |\n    - example';
  assert.equal(yamlNewline(listText,listText.length),'\n    ');
});
test('IntelliJ Enter, duplicate and comment bindings operate on editor state',()=>{
  let state=EditorState.create({doc:'abc:',selection:{anchor:4},extensions:yamlLanguageExtensions});
  const target={get state(){return state;},dispatch(tr){state=tr.state??state.update(tr).state;}};
  insertYamlNewline(target);assert.equal(state.doc.toString(),'abc:\n  ');
  state=EditorState.create({doc:'- sound: bell',selection:{anchor:0},extensions:yamlLanguageExtensions});
  intellijKeymap.find(item=>item.key==='Mod-d').run(target);assert.equal(state.doc.toString(),'- sound: bell\n- sound: bell');
  state=EditorState.create({doc:'- sound: bell',selection:{anchor:0},extensions:yamlLanguageExtensions});
  intellijKeymap.find(item=>item.key==='Mod-/').run(target);assert.equal(state.doc.toString(),'# - sound: bell');
});

test('Ctrl+Shift arrows move lines just like Shift+Alt arrows',()=>{
  for(const [key,alias] of [['Ctrl-Shift-ArrowUp','Shift-Alt-ArrowUp'],['Ctrl-Shift-ArrowDown','Shift-Alt-ArrowDown']]){
    assert.equal(intellijKeymap.find(binding=>binding.key===key).run,intellijKeymap.find(binding=>binding.key===alias).run);
  }
  let state=EditorState.create({doc:'first\nsecond\nthird',selection:{anchor:6}});
  const target={get state(){return state;},dispatch(tr){state=tr.state??state.update(tr).state;}};
  intellijKeymap.find(binding=>binding.key==='Ctrl-Shift-ArrowUp').run(target);
  assert.equal(state.doc.toString(),'second\nfirst\nthird');
  intellijKeymap.find(binding=>binding.key==='Ctrl-Shift-ArrowDown').run(target);
  assert.equal(state.doc.toString(),'first\nsecond\nthird');
});
