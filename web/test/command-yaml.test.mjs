import test from 'node:test';
import assert from 'node:assert/strict';
import { formatCommands, formatWorkflowYaml } from '../src/command-yaml.js';

test('YAML text shows every editable command and keeps multiline text', () => {
  const values=[{bc:'Привет\nвсем'},{all_players:{sound:'bell 1 1'}},'[setblock] 10 10 10 air',{if:'ready',do:[{message:'go'}]}];
  assert.equal(formatCommands(values), '- bc: |-\n    Привет\n    всем\n- all_players:\n    sound: "bell 1 1"\n- "[setblock] 10 10 10 air"\n- if: "ready"\n  do:\n    - message: "go"');
});

test('command card YAML contains graph links as ordinary EventDSL actions', () => {
  const commands=[{console:'say hi'},{create:'next'},{if:'ready',do:[{call:'update next'}]}];
  const yaml=formatCommands(commands);
  assert.match(yaml,/create: "next"/);
  assert.match(yaml,/call: "update next"/);
});

test('offline YAML export includes unsaved commands, links, notes and unknown config fields', () => {
  const config={id:'example:test', extra:{enabled:true}, nodes:{commands:{type:'default:commands',on_run:[{console:'old'},{create:'next'}]},next:{type:'default:timer',ticks:20}}};
  const metadata={version:3,webNodes:{note:{type:'web:note',text:'не терять'}}};
  const draft=new Map([[JSON.stringify(['commands','on_run']),"- console: 'новая команда'\n- create: 'next'"]]);
  const yaml=formatWorkflowYaml(config,metadata,draft);
  assert.match(yaml,/extra:\n  enabled: true/);
  assert.match(yaml,/on_run:\n      - console: 'новая команда'\n      - create: 'next'/);
  assert.match(yaml,/"\$web_meta": .*не терять/);
  assert.equal((yaml.match(/- create:/g)??[]).length,1);
  assert.deepEqual(config.nodes.commands.on_run,[{console:'old'},{create:'next'}]);
});
