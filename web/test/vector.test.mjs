import test from 'node:test';
import assert from 'node:assert/strict';
import { splitVector, joinVector } from '../src/vector.js';

test('vector fields preserve the YAML vector format for integer and fractional coordinates', () => {
  assert.deepEqual(splitVector('0;1.5;-2'), ['0', '1.5', '-2']);
  assert.equal(joinVector([' 3 ', '4', '-5'], false), '3;4;-5');
  assert.equal(joinVector(['0.25', '-1.5', '2'], true), '0.25;-1.5;2');
  assert.equal(joinVector(['{x}', '1', '2'], false), '{x};1;2');
  assert.equal(joinVector(['{position}'], false), '{position}');
  assert.equal(joinVector(['{position}', '', ''], false), '{position}');
  assert.equal(joinVector(['{x}', '{y}', '{z}'], false), '{x};{y};{z}');
  assert.equal(joinVector(['0', '', '2'], true), '0;;2');
});
