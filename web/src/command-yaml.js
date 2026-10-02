const links = new Set(['create','remove','remove?','rm','rm?','call','call?','destroy']);
const stringLink = /^\[(?:create|remove\??|rm\??|call\??|destroy)\](?:\s|$)/;

export function isConnection(value) {
  if (typeof value === 'string') return stringLink.test(value.trim());
  return !!value && !Array.isArray(value) && typeof value === 'object' && Object.keys(value).some(key => links.has(key));
}

export function editableCommands(values) {
  return Array.isArray(values) ? values.filter(value => !isConnection(value)) : [];
}

export function replaceEditableCommands(previous, commands) {
  const source=Array.isArray(previous)?previous:[], result=[];
  let index=0;
  for (const item of source) {
    if (isConnection(item)) result.push(item);
    else if (index<commands.length) result.push(commands[index++]);
  }
  const remaining=commands.slice(index), tail=source.slice().reverse().findIndex(item=>!isConnection(item));
  const trailing=tail<0?result.length:tail;
  result.splice(result.length-trailing,0,...remaining);
  return result;
}

const scalar = value => value === null ? 'null' : typeof value === 'string' ? JSON.stringify(value) : String(value);
const keyOf = key => /^[A-Za-z_][A-Za-z0-9_-]*$/.test(key) ? key : JSON.stringify(key);
function lines(value, depth, prefix) {
  const space=' '.repeat(depth);
  const childDepth=depth+(prefix.startsWith('- ')&&prefix.length>2?4:2);
  if (typeof value === 'string' && value.includes('\n')) {
    const trailing=value.endsWith('\n'), parts=value.replace(/\n$/, '').split('\n');
    return [`${space}${prefix}${trailing?'|':'|-'}\n`, ...parts.map(line=>`${' '.repeat(childDepth)}${line}\n`)].join('');
  }
  if (Array.isArray(value)) {
    if (!value.length) return `${space}${prefix}[]\n`;
    return `${space}${prefix.trimEnd()}\n`+value.map(item=>lines(item,childDepth,'- ')).join('');
  }
  if (value && typeof value === 'object') {
    const entries=Object.entries(value);
    if (!entries.length) return `${space}${prefix}{}\n`;
    if (prefix==='- ') return entries.map(([key,item],i)=>lines(item,i?depth+2:depth,`${i?'':'- '}${keyOf(key)}: `)).join('');
    return `${space}${prefix.trimEnd()}\n`+entries.map(([key,item])=>lines(item,childDepth,`${keyOf(key)}: `)).join('');
  }
  return `${space}${prefix}${scalar(value)}\n`;
}

export function formatActions(values) {
  return values.map(value=>lines(value,0,'- ')).join('').replace(/\n$/, '');
}
export function formatCommands(values) {
  return formatActions(editableCommands(values));
}

// A local export must also retain command text that has not been parsed by the server yet.
export function formatWorkflowYaml(config, metadata, drafts = new Map()) {
  const document = structuredClone(config);
  document.$web_meta = JSON.stringify(metadata);
  const output = [];
  for (const [key, value] of Object.entries(document)) {
    if (key !== 'nodes' || !value || typeof value !== 'object' || Array.isArray(value)) {
      output.push(lines(value, 0, `${keyOf(key)}: `));
      continue;
    }
    if (!Object.keys(value).length) { output.push('nodes: {}\n'); continue; }
    output.push('nodes:\n');
    for (const [name, node] of Object.entries(value)) {
      output.push(`  ${keyOf(name)}:\n`);
      for (const [field, item] of Object.entries(node)) {
        const draft = drafts.get(JSON.stringify([name, field]));
        if (field !== 'on_run' || node.type !== 'default:commands' || draft === undefined) {
          output.push(lines(item, 4, `${keyOf(field)}: `));
          continue;
        }
        const connections = Array.isArray(item) ? item.filter(isConnection) : [];
        if (!draft.trim() && !connections.length) { output.push('    on_run: []\n'); continue; }
        output.push('    on_run:\n');
        if (draft.trim()) output.push(draft.replace(/\n$/, '').split('\n').map(line => `      ${line}\n`).join(''));
        for (const connection of connections) output.push(lines(connection, 6, '- '));
      }
    }
  }
  return output.join('');
}
