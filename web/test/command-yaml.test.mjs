import test from 'node:test';
import assert from 'node:assert/strict';
import { editableCommands, formatCommands, formatWorkflowYaml, isConnection, replaceEditableCommands } from '../src/command-yaml.js';

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

test('command card text keeps graph links outside the YAML editor', () => {
  const previous=[{setblock:'{x} {y} {z} stone'},{create:'next'},{console:'old'}];
  assert.equal(formatCommands(previous), '- setblock: "{x} {y} {z} stone"\n- console: "old"');
  assert.deepEqual(replaceEditableCommands(previous,[{setblock:'{x} {y} {z} minecraft:respawn_anchor[charges=4]'},{console:'123213'}]),
    [{setblock:'{x} {y} {z} minecraft:respawn_anchor[charges=4]'},{create:'next'},{console:'123213'}]);
});

test('offline YAML export includes unsaved commands, links, notes and unknown config fields', () => {
  const config={id:'example:test', extra:{enabled:true}, nodes:{commands:{type:'default:commands',on_run:[{console:'old'},{create:'next'}]},next:{type:'default:timer',ticks:20}}};
  const metadata={version:3,webNodes:{note:{type:'web:note',text:'не терять'}}};
  const draft=new Map([[JSON.stringify(['commands','on_run']),"- console: 'новая команда'"]]);
  const yaml=formatWorkflowYaml(config,metadata,draft);
  assert.match(yaml,/extra:\n  enabled: true/);
  assert.match(yaml,/on_run:\n      - console: 'новая команда'\n      - create: "next"/);
  assert.match(yaml,/"\$web_meta": .*не терять/);
  assert.deepEqual(config.nodes.commands.on_run,[{console:'old'},{create:'next'}]);
});
