import test from 'node:test';
import assert from 'node:assert/strict';
import { GraphModel } from '../src/model.js';
import { formatWorkflowYaml } from '../src/command-yaml.js';
import { nodePlaceholders, renameNodePlaceholders, renamePlaceholderText } from '../src/node-placeholders.js';

test('renames every literal ctx reference without touching other IDs or ordinary text', () => {
  assert.equal(
    renamePlaceholderText('tto {ctx:tto.timer} {ctx:tto.count} {ctx:tto2.timer} {args:tto} {ctx:other.timer}', 'tto', 'time_to_open'),
    'tto {ctx:time_to_open.timer} {ctx:time_to_open.count} {ctx:tto2.timer} {args:tto} {ctx:other.timer}'
  );
  assert.equal(renamePlaceholderText('{ctx:timer+[1].timer}', 'timer+[1]', '$&'), '{ctx:$&.timer}');
  assert.equal(renameNodePlaceholders("- console: 'Осталось {ctx:tto.timer}'", 'tto', 'time_to_open'),
    "- console: 'Осталось {ctx:time_to_open.timer}'");
});

test('renaming updates the whole local document, saved payload and YAML export together and supports undo', () => {
  const document={key:'demo.yml',revision:'first',metadata:{webNodes:{note:{type:'web:note',text:'{ctx:tto.timer}'}}},config:{
    id:'demo',args:{time:'{ctx:tto.timer}'},generator:{custom:'{ctx:tto.timer}'},
    spawner:{when:{'2m':[{bc:'{ctx:tto.timer}'}]}},custom:{'{ctx:tto.timer}':['{ctx:tto.count}']},
    nodes:{
      tto:{type:'tick:timer',timer:20,on_tick:[{console:'{ctx:tto.timer}'}]},
      init:{type:'__init__',on_init:[{create:'tto'}]},
      hologram:{type:'dh:hologram',lines:['Осталось {ctx:tto.timer}', '{ctx:tto2.timer}']},
      commands:{type:'default:commands',on_run:[{weights:{20:[{console:'{ctx:tto.timer}'}]}}]}
    }
  }};
  const model=new GraphModel(document), before=model.snapshot();
  model.rename('tto','time_to_open');
  assert.equal(model.nodes.init.on_init[0].create,'time_to_open');
  assert.deepEqual(model.nodes.hologram.lines,['Осталось {ctx:time_to_open.timer}','{ctx:tto2.timer}']);
  assert.equal(model.nodes.commands.on_run[0].weights[20][0].console,'{ctx:time_to_open.timer}');
  assert.equal(model.config.spawner.when['2m'][0].bc,'{ctx:time_to_open.timer}');
  assert.deepEqual(model.config.custom,{'{ctx:time_to_open.timer}':['{ctx:time_to_open.count}']});
  assert.equal(model.webNodes.note.text,'{ctx:time_to_open.timer}');
  assert.equal(model.payload().settings.args.time,'{ctx:time_to_open.timer}');
  assert.equal(model.payload().settings.generator.custom,'{ctx:time_to_open.timer}');
  assert.equal(JSON.stringify(model.payload()).includes('{ctx:tto.'),false);
  assert.equal(formatWorkflowYaml(model.config,model.metadata).includes('{ctx:tto.'),false);
  model.undo(); assert.deepEqual(model.snapshot(),before);
  model.redo(); assert.equal(model.nodes.hologram.lines[0],'Осталось {ctx:time_to_open.timer}');
});

test('a colliding placeholder key cancels the rename without losing fields or graph links', () => {
  const model=new GraphModel({key:'demo.yml',revision:'first',config:{nodes:{tto:{type:'tick:timer'},init:{on_init:[{create:'tto'}]}},
    custom:{'{ctx:tto.timer}':'first','{ctx:time_to_open.timer}':'second'}},metadata:{}});
  const before=model.snapshot();
  assert.throws(()=>model.rename('tto','time_to_open'),/повторяющийся ключ/);
  assert.deepEqual(model.snapshot(),before);
  assert.equal(model.dirty,false);
});

test('the catalogue builds ready placeholders for each node from its schema, including type aliases', () => {
  const timer={'x-placeholders':{timer:{title:'Осталось секунд',description:'Текущее значение таймера.'}}};
  const entries=nodePlaceholders({tto:{type:'tick:timer'},tte:{type:'tick:timer'},breaker:{type:'breaker'},unknown:{type:'unknown'}},
    {'tick:timer':timer,'default:breaker':{'x-placeholders':{count:{title:'Осталось попыток'}}}});
  assert.deepEqual(entries.map(entry=>entry.value),['{ctx:tto.timer}','{ctx:tte.timer}','{ctx:breaker.count}']);
  assert.equal(entries[0].title,'Осталось секунд');
});
