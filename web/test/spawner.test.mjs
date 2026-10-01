import test from 'node:test';
import assert from 'node:assert/strict';
import {validDuration, validTimeKey, messages, setMessage, removeMessage, variants, editVariants, variantValues, setVariantValue, removeVariantValue, makeCommand, setMakeParams, removeMake} from '../src/spawner.js';

test('timer durations and announcement ranges use the server format', () => {
  assert.equal(validDuration('1h30m'), true);
  assert.equal(validDuration('5 minutes'), false);
  assert.equal(validDuration('0s'), false);
  assert.equal(validTimeKey('5s-1s'), true);
  assert.equal(validTimeKey('1s-5s'), false);
});

test('structured messages retain unrelated commands', () => {
  const handler = [{weights:{20:[{set_value:'type elite'}]}},{bc:'Starting'}, {sound:'bell 1 1'}];
  assert.deepEqual(messages(handler),[{index:1,text:'Starting'}]);
  const changed = setMessage(handler,1,'Soon');
  assert.equal(changed[1].bc,'Soon');
  assert.deepEqual(changed[0],handler[0]);
  assert.deepEqual(changed[2],handler[2]);
  assert.deepEqual(removeMessage(changed,1),[handler[0],handler[2]]);
});

test('weighted variants retain other commands and allow value edits', () => {
  const handler = [{bc:'before'},{weights:{20:[{set_value:'type elite'},{sound:'bell 1 1'}],80:[{set_value:'type common'}]}},{bc:'after'}];
  assert.equal(variants(handler).length,2);
  const changed = editVariants(handler,weights => { weights[20] = setVariantValue(weights[20],0,'type','rare'); });
  assert.deepEqual(variantValues(variants(changed)[0].branch).map(v=>v.value),['rare']);
  assert.deepEqual(changed[1].weights[20][1],{sound:'bell 1 1'});
  assert.equal(changed[0].bc,'before');
  assert.equal(changed[2].bc,'after');
  const removed = editVariants(changed, weights => {weights[20] = removeVariantValue(weights[20],0);});
  assert.deepEqual(removed[1].weights[20],[{sound:'bell 1 1'}]);
  const spaced = setVariantValue([],null,'type','очень редкий');
  assert.equal(spaced[0].set_value,'type "очень редкий"');
  assert.equal(variantValues(spaced)[0].value,'очень редкий');
});

test('make parameters override defaults while final announcements remain', () => {
  const handler = [{bc:'Arrived'},{make:{loot_table:'{value:loot_table}'}}];
  assert.deepEqual(makeCommand(handler).params,{loot_table:'{value:loot_table}'});
  const changed = setMakeParams(handler,{type:'{value:type}'});
  assert.deepEqual(changed,[{bc:'Arrived'},{make:{type:'{value:type}'}}]);
  assert.deepEqual(removeMake(changed),[{bc:'Arrived'}]);
});
