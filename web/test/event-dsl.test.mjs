import test from 'node:test';
import assert from 'node:assert/strict';
import {decodeList,encodeList,validateList} from '../src/event-dsl-model.js';
import {formatCommands} from '../src/command-yaml.js';
const round = value => encodeList(decodeList(value));
test('simple commands and unknown shapes retain values and order',()=>{
  const value=[{bc:'Аирдроп {value:type}'},{console:'eco take %player_name% 1000'},{sound:'bell 1 1'},{plugin_action:{a:[1,{x:true}]}},'message'];
  assert.deepEqual(round(value),value);
});
test('if, if-all, if-one and nested do/else short forms',()=>{
  const value=[{if:'%vault_eco_balance% > 1000',do:'message',else:[{bc:'no'},{'if-all':['a','b'],do:[{'if-one':['c','d'],do:[{console:'ok'}]}]}]}];
  const result=round(value);
  assert.deepEqual(result[0].do,['message']);
  assert.deepEqual(result[0].else[1]['if-all'],['a','b']);
  assert.deepEqual(result[0].else[1].do[0]['if-one'],['c','d']);
  assert.deepEqual(validateList(decodeList(value)),[]);
});
test('oneOf order and fallback survive recursive conversion',()=>{
  const value=[{oneOf:[{if:'first',do:'message'},{if:'second',do:[{bc:'second'}]},{do:'Fallback'}]}];
  const result=round(value)[0].oneOf;
  assert.deepEqual(result.map(v=>v.if),['first','second',undefined]);
  assert.deepEqual(result[2].do,['Fallback']);
});
test('weights keep decimal comma and nested actions',()=>{
  const value=[{weights:{'10,7':[{set_value:'loot_table example/epic'},{if:'yes',do:[{weights:{'20,9':[{test:123}]}}]}],'20,9':[{set_value:'type элитный'}]}}];
  assert.deepEqual(round(value),value);
  const model=decodeList(value);model[0].branches.reverse();
  assert.deepEqual(Object.keys(encodeList(model)[0].weights),['20,9','10,7']);
});
test('repository timer example survives visual model and YAML formatting',()=>{
  const value=[{weights:{'20':[{set_value:'loot_table example/epic'},{set_value:'type элитный'}],'50':[{set_value:'loot_table example/rare'},{set_value:'type редкий'}],'100':[{set_value:'loot_table example/default'},{set_value:'type обычный'}]}},{bc:'Аирдроп {value:type} появится через 5 минут'}];
  assert.deepEqual(round(value),value);
  const yaml=formatCommands(round(value));
  assert.match(yaml,/"20":/);assert.match(yaml,/set_value: "type элитный"/);
  assert.match(yaml,/Аирдроп \{value:type\}/);
});
test('raw YAML equivalent JSON shapes convert to visual and back',()=>{
  const parsed=[{if:'a',do:[{oneOf:[{if:'b',do:'yes'},{do:[{weights:{'10,7':[{custom:{nested:['x']}}]}}]}]}]}];
  const yaml=formatCommands(parsed);
  assert.match(yaml,/oneOf:/);
  assert.deepEqual(round(parsed)[0].do[0].oneOf[1].do[0].weights['10,7'][0],{custom:{nested:['x']}});
});

test('malformed and multi-key actions remain raw and editable',()=>{
  const value=[{if:['unexpected'],do:[{bc:'x'}]},{unknown:{list:[1,2]},metadata:true}];
  assert.deepEqual(decodeList(value).map(v=>v.kind),['raw','raw']);
  assert.deepEqual(round(value),value);
});
