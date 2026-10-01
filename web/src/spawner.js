// Edits only the commands represented by the form. Other EventDSL commands remain in place.
const object = value => value && typeof value === 'object' && !Array.isArray(value);
export const actions = value => value === undefined || value === null ? [] : Array.isArray(value) ? value : [value];
export const validDuration = value => typeof value === 'string' && /^(?:[1-9]\d*(?:mo|[smhdwy]))+$/.test(value);
const millis = value => [...value.matchAll(/(\d+)(mo|[smhdwy])/g)].reduce((n, match) => n + Number(match[1]) * ({s:1000,m:60000,h:3600000,d:86400000,w:604800000,mo:2629746000,y:31556908800}[match[2]]), 0);
export function validTimeKey(value) {
  const parts = String(value).split('-');
  return parts.length <= 2 && parts.every(validDuration) && (parts.length === 1 || millis(parts[0]) >= millis(parts[1]));
}
export const messages = handler => actions(handler).flatMap((item, index) => object(item) && typeof item.bc === 'string' && Object.keys(item).length === 1 ? [{index,text:item.bc}] : []);
export function setMessage(handler, index, value) {
  const list = [...actions(handler)];
  if (index === null) list.push({bc:value});
  else list[index] = {bc:value};
  return list;
}
export function removeMessage(handler, index) {
  const list = [...actions(handler)]; list.splice(index, 1); return list;
}
export function weightCommand(handler) {
  const index = actions(handler).findIndex(item => object(item) && object(item.weights) && Object.keys(item).length === 1);
  return index;
}
export function variants(handler) {
  const list = actions(handler), index = weightCommand(handler);
  return index < 0 ? [] : Object.entries(list[index].weights).map(([weight, branch]) => ({weight, branch:actions(branch)}));
}
export function editVariants(handler, edit) {
  const list = [...actions(handler)], index = weightCommand(handler);
  const weights = index < 0 ? {} : {...list[index].weights};
  edit(weights);
  if (Object.keys(weights).length) {
    if (index < 0) list.unshift({weights}); else list[index] = {weights};
  } else if (index >= 0) list.splice(index, 1);
  return list;
}
export function variantValues(branch) {
  return actions(branch).flatMap((item, index) => {
    const text = object(item) && typeof item.set_value === 'string' && Object.keys(item).length === 1 ? item.set_value : null;
    const match = text?.match(/^(\S+)\s+([\s\S]+)$/);
    return match ? [{index,key:match[1],value:match[2].startsWith('"') && match[2].endsWith('"') ? match[2].slice(1,-1).replace(/\\"/g,'"') : match[2]}] : [];
  });
}
export function setVariantValue(branch, index, key, value) {
  const list = [...actions(branch)];
  const encoded = /\s/.test(value) ? `"${value.replace(/"/g,'\\"')}"` : value;
  const command = {set_value:`${key} ${encoded}`};
  if (index === null) list.push(command); else list[index] = command;
  return list;
}
export function removeVariantValue(branch, index) {
  const list = [...actions(branch)]; list.splice(index, 1); return list;
}
export function makeCommand(handler) {
  const list = actions(handler);
  const index = list.findIndex(item => object(item) && Object.hasOwn(item, 'make') && Object.keys(item).length === 1);
  return {index, params:index >= 0 && object(list[index].make) ? list[index].make : {}};
}
export function setMakeParams(handler, params) {
  const list = [...actions(handler)], {index} = makeCommand(handler);
  if (index < 0) list.push({make:params}); else list[index] = {make:params};
  return list;
}
export function removeMake(handler) {
  const list = [...actions(handler)], {index} = makeCommand(handler);
  if (index >= 0) list.splice(index,1);
  return list;
}
