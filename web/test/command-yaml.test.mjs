import test from 'node:test';
import assert from 'node:assert/strict';
import { editableCommands, formatCommands, isConnection, replaceEditableCommands } from '../src/command-yaml.js';

test('YAML text shows every editable command and keeps multiline text', () => {
  const values=[{bc:'Привет\nвсем'},{all_players:{sound:'bell 1 1'}},'[setblock] 10 10 10 air',{if:'ready',do:[{message:'go'}]}];
  assert.equal(formatCommands(values), '- bc: |-\n    Привет\n    всем\n- all_players:\n    sound: "bell 1 1"\n- "[setblock] 10 10 10 air"\n- if: "ready"\n  do:\n    - message: "go"');
});

test('editing YAML keeps graph connections and their order', () => {
  const previous=[{bc:'first'},{create:'timer'},{bc:'second'},{destroy:''}];
  assert.deepEqual(editableCommands(previous),[{bc:'first'},{bc:'second'}]);
  assert.deepEqual(replaceEditableCommands(previous,[{bc:'new'},{bc:'next'},{bc:'extra'}]),
    [{bc:'new'},{create:'timer'},{bc:'next'},{bc:'extra'},{destroy:''}]);
  assert.equal(isConnection('[call] update timer'),true);
});
