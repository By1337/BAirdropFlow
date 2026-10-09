// Command signatures follow EventDSL DefaultEventCommands and BAirdropFlow AirdropCommands.
const text = '<message>';
export const commands = {
  console: ['<command>', 'say Hello'], broadcast: [text, '<gold>Hello'], bc: [text, '<gold>Hello'],
  message_all: [text, '<gold>Hello'], message: [text, '<green>Hello'], chat: [text, 'Hello'],
  player: ['<command>', 'spawn'], ref: ['<message key>', 'welcome'], ref_all: ['<message key>', 'welcome'],
  sound: ['<sound> [volume] [pitch]', 'block.note_block.pling 1 1'],
  sound_all: ['<sound> [volume] [pitch]', 'block.note_block.pling 1 1'],
  action_bar: [text, '<gold>Hello'], action_bar_all: [text, '<gold>Hello'],
  title: ['<"title\\nsubtitle"> [fadeIn] [stay] [fadeOut]', '"Hello\\nWelcome" 10 70 20'],
  title_all: ['<"title\\nsubtitle"> [fadeIn] [stay] [fadeOut]', '"Hello\\nWelcome" 10 70 20'],
  give_effect: ['<effect> <duration ticks> <amplifier>', 'speed 200 0'],
  cooldown: ['<material> <ticks>', 'ender_pearl 20'],
  nearby: ['<radius> <command>', '10 [sound] block.note_block.pling 1 1'],
  create: ['<node> [options]', ''], remove: ['<node>', ''], rm: ['<node>', ''],
  call: ['<event> <node>', 'update '], 'call?': ['<event> <node>', 'update '],
  destroy: ['', ''], setblock: ['<x> <y> <z> <block>', '{x} {y} {z} minecraft:stone']
};
const spawnerCommands = {set_value: ['<key> <value>', 'loot_table example/epic'], make: ['<map of airdrop arguments>', '\n  loot_table: "{value:loot_table}"\n  type: "{value:type}"']};
const structures = {
  if: ['Условие · do / else', 'if: "${condition}"\n  do:\n    - message: "${message}"\n  else:\n    - message: "${otherwise}"'],
  'if-all': ['Все условия', 'if-all:\n    - "${condition}"\n  do:\n    - message: "${message}"'],
  'if-one': ['Хотя бы одно условие', 'if-one:\n    - "${condition}"\n  do:\n    - message: "${message}"'],
  oneOf: ['Первое подходящее условие', 'oneOf:\n    - if: "${condition}"\n      do:\n        - message: "${message}"\n    - do:\n        - message: "${otherwise}"'],
  weights: ['Случайный вариант с весом', 'weights:\n    "10":\n      - message: "${first}"\n    "20":\n      - message: "${second}"'],
  all_players: ['Действия для всех игроков', 'all_players:\n    - message: "${message}"'],
  'nearby-10': ['Действия в радиусе 10 блоков', 'nearby-10:\n    - sound: block.note_block.pling 1 1']
};
const sounds = ['block.note_block.pling', 'block.note_block.bell', 'block.note_block.chime', 'entity.player.levelup', 'entity.experience_orb.pickup', 'block.amethyst_block.chime', 'ui.toast.challenge_complete'];
const option = (label, apply = label, detail = '') => ({label, apply, detail, type:'text'});

export function inLiteralBlock(doc, pos) {
  const start=doc.lastIndexOf('\n',pos-1)+1, indent=doc.slice(start,pos).match(/^ */)[0].length;
  const prior=doc.slice(0,start).split('\n');
  for(let i=prior.length-2;i>=0;i--){
    if(!prior[i].trim())continue;
    if(prior[i].match(/^ */)[0].length<indent)return /:\s*[|>][+-]?\s*(?:#.*)?$/.test(prior[i]);
  }
  return false;
}

// Kept independent from the view so nested command contexts can be verified in Node.
export function completionAt(doc, pos, {nodes = [], args = [], spawner = false, explicit = false} = {}) {
  const start = doc.lastIndexOf('\n', pos - 1) + 1, line = doc.slice(start, pos);
  const catalog = {...commands, ...(spawner ? spawnerCommands : {})};
  // Never offer actions inside comments or literal block text.
  if (/^\s*#/.test(line)) return null;
  const indentation = line.match(/^ */)[0].length;
  if(inLiteralBlock(doc,pos))return null;
  const placeholder = line.match(/\{(?:args:)?[\w:-]*$/);
  if (placeholder) return {from:pos-placeholder[0].length, options:[...['{x}','{y}','{z}','{world}'], ...args.map(key=>`{args:${key}}`), ...(spawner?['{value:loot_table}','{value:type}','{time_left}','{formated_time_left}']:[])].map(value=>option(value))};
  const key = line.match(/^(\s*(?:-\s*)?)([\w?-]*)$/);
  if (key && (line.includes('-') || indentation>0 || explicit)) {
    const prefix = key[1], hasDash = /^\s*-/.test(prefix);
    const suffix=doc.slice(pos).match(/^:[ \t]*/)?.[0]??'';
    return {from:start+prefix.length, to:pos+suffix.length, options:[
      ...Object.entries(catalog).map(([name,[signature]])=>({...option(name, `${name}: `, signature),type:'function'})),
      ...Object.entries(structures).map(([name,[detail,snippet]])=>({label:name, detail, type:'keyword', snippet})),
      ...(!hasDash ? ['do','else'].map(name=>option(name,`${name}:\n${prefix}  - `,'Вложенный список действий')) : [])
    ]};
  }
  // Both YAML mapping commands and primitive '[command] args' are valid EventDSL.
  const mapping = line.match(/^\s*-?\s*([\w?-]+):\s*/);
  const rawPrefix=line.match(/^\s*(?:-\s+)?/)[0];
  let command = mapping?.[1], valueStart = mapping ? start+mapping[0].length : start+rawPrefix.length;
  let value = doc.slice(valueStart,pos).replace(/^['"]/, '');
  if (/^['"]/.test(doc.slice(valueStart,pos))) valueStart++;
  const brackets = [...value.matchAll(/(?:^|\s)\[([\w?-]*)\](?:\s|$)/g)];
  const partial = value.match(/(?:^|\s)\[([\w?-]*)$/);
  if (partial) return {from:pos-partial[1].length, to:pos+(doc[pos]===']'?1:0), options:Object.entries(catalog).map(([name,[signature]])=>({...option(name, `${name}] `,signature),type:'function'}))};
  if (brackets.length) {
    const last = brackets.at(-1);
    command=last[1]; valueStart+=last.index+last[0].length; value=doc.slice(valueStart,pos);
  }
  if (!command || !catalog[command]) return null;
  const [signature, example] = catalog[command];
  if (!value.trim()) {
    const insert=command==='make' && mapping ? example.split('\n').map((part,index)=>index ? ' '.repeat(indentation+(/^\s*-/.test(line)?4:2))+part.trimStart() : part).join('\n') : example;
    return {from:pos, options:insert ? [option(signature, insert,`Пример ${command}`)] : []};
  }
  if (command==='nearby' && /^\S+\s+$/.test(value)) return {from:pos, options:Object.entries(catalog).map(([name,[sig]])=>({...option(`[${name}]`, `[${name}] `,sig),type:'function'}))};
  const token = value.match(/[^\s'"\[\]]*$/)?.[0] ?? '';
  const tokenIndex = value.trimStart().slice(0,value.length-token.length).trim().split(/\s+/).filter(Boolean).length;
  let values=[];
  if (['sound','sound_all'].includes(command)) values=tokenIndex===0 ? sounds : ['1','0.5','2'];
  if (['create','remove','rm'].includes(command) && tokenIndex===0 || ['call','call?'].includes(command) && tokenIndex===1) values=nodes;
  if (['call','call?'].includes(command) && tokenIndex===0) values=['update','create','remove'];
  if (command==='nearby' && tokenIndex===0) values=['10','20','50'];
  if (command==='set_value' && tokenIndex===0) values=['loot_table','type',...args];
  if (command==='give_effect' && tokenIndex===0) values=['speed','glowing','slow_falling','resistance'];
  return values.length ? {from:pos-token.length, options:[...new Set(values)].map(item=>option(item,item,signature))} : null;
}
