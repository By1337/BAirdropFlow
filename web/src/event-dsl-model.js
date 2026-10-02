const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);
export const eventItems = value => value == null ? [] : Array.isArray(value) ? value : [value];
export const copyValue = value => structuredClone(value);
const control = new Set(['if', 'if-all', 'if-one', 'oneOf', 'weights']);
export function decodeActions(value) { return decodeList(value); }
export function decodeAction(value) {
  if (!record(value) || Object.keys(value).length !== 1) return {kind:'raw', value:copyValue(value)};
  const [key, body] = Object.entries(value)[0];
  if (control.has(key)) {
    if (key === 'weights' && record(body)) return {kind:key, branches:Object.entries(body).map(([weight,actions])=>({weight,actions:decodeActions(actions)}))};
    if (key === 'oneOf' && Array.isArray(body)) return {kind:key,cases:body.map(item=> record(item) && Object.hasOwn(item,'do') && (Object.hasOwn(item,'if') || Object.keys(item).every(k=>k==='do')) ? {condition:item.if, actions:decodeActions(item.do), extra:Object.fromEntries(Object.entries(item).filter(([k])=>!['if','do'].includes(k)))} : {raw:copyValue(item)})};
  }
  // Conditional commands are maps with their companion do/else fields.
  return {kind:'command',key,value:copyValue(body)};
}
export function decodeConditional(value) {
  if (!record(value)) return null;
  const key=['if','if-all','if-one'].find(k=>Object.hasOwn(value,k));
  if (!key || !Object.hasOwn(value,'do')) return null;
  if (key==='if' ? typeof value[key]!=='string' : !Array.isArray(value[key]) || value[key].some(item=>typeof item!=='string')) return null;
  return {kind:key,condition:copyValue(value[key]),then:decodeActions(value.do),else:Object.hasOwn(value,'else')?decodeActions(value.else):null,
    extra:Object.fromEntries(Object.entries(value).filter(([k])=>![key,'do','else'].includes(k)))};
}
export function decodeList(value) { return eventItems(value).map(item=>decodeConditional(item)??decodeAction(item)); }
export function encodeAction(action) {
  switch(action.kind) {
    case 'raw': return copyValue(action.value);
    case 'command': return {[action.key]:copyValue(action.value)};
    case 'if': case 'if-all': case 'if-one': return {...copyValue(action.extra??{}),[action.kind]:copyValue(action.condition),do:encodeList(action.then),...(action.else===null?{}:{else:encodeList(action.else)})};
    case 'oneOf': return {oneOf:action.cases.map(item=>Object.hasOwn(item,'raw')?copyValue(item.raw):{...copyValue(item.extra??{}),...(item.condition===undefined?{}:{if:item.condition}),do:encodeList(item.actions)})};
    case 'weights': return {weights:Object.fromEntries(action.branches.map(branch=>[branch.weight,encodeList(branch.actions)]))};
    default: throw new Error(`Неизвестный тип действия: ${action.kind}`);
  }
}
export const encodeList = actions => actions.map(encodeAction);
export const actionCount = value => eventItems(value).length;
export function newAction(kind) {
  if (kind==='if') return {kind,condition:'',then:[],else:null,extra:{}};
  if (kind==='if-all'||kind==='if-one') return {kind,condition:[''],then:[],else:null,extra:{}};
  if (kind==='oneOf') return {kind,cases:[{condition:'',actions:[],extra:{}}]};
  if (kind==='weights') return {kind,branches:[{weight:'1',actions:[]}]};
  if (kind==='raw') return {kind,value:{custom_command:''}};
  return {kind:'command',key:kind,value:''};
}
export function validateList(actions, path='') {
  const errors=[];
  actions.forEach((item,index)=>{
    const at=`${path}/${index}`;
    if (['if','if-all','if-one'].includes(item.kind)) {
      if (item.kind==='if' && typeof item.condition!=='string') errors.push([at,'Условие должно быть строкой']);
      if (item.kind!=='if' && (!Array.isArray(item.condition)||item.condition.some(v=>typeof v!=='string'))) errors.push([at,'Нужен список строк условий']);
      errors.push(...validateList(item.then,at+'/then'),...validateList(item.else??[],at+'/else'));
    }
    if (item.kind==='weights') {
      const keys=item.branches.map(v=>v.weight);
      if (keys.some(v=>!v.trim())) errors.push([at,'Вес не может быть пустым']);
      if (new Set(keys).size!==keys.length) errors.push([at,'Веса должны быть уникальны']);
      item.branches.forEach((v,i)=>errors.push(...validateList(v.actions,`${at}/branches/${i}`)));
    }
    if (item.kind==='oneOf') item.cases.forEach((v,i)=>{if(v.actions)errors.push(...validateList(v.actions,`${at}/cases/${i}`));});
  });
  return errors;
}
