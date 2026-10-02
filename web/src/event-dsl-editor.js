import {decodeList, encodeList, copyValue, newAction, validateList} from './event-dsl-model.js';
import {formatActions} from './command-yaml.js';
const esc = value => String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const labels={bc:'Broadcast',broadcast:'Broadcast',message:'Message',console:'Console',sound:'Sound',set_value:'Set value',if:'IF','if-all':'IF ALL','if-one':'IF ANY',oneOf:'ONE OF',weights:'Weighted choice'};
const types=['bc','message','console','sound','set_value','if','if-all','if-one','oneOf','weights','raw'];
const encoded = path => path.join('/');
const clone = value => copyValue(value);
function find(root,path) { return path.reduce((value,key)=>value[key],root); }
function rowButtons() { return '<button type="button" data-op="duplicate" title="Дублировать">⧉</button><button type="button" data-op="up" title="Выше">↑</button><button type="button" data-op="down" title="Ниже">↓</button><button type="button" data-op="delete" title="Удалить">×</button>'; }
export class EventDslEditor {
  constructor(host,{value=[],onChange,parseYaml,descriptors={},compact=false}={}) {
    this.host=host;this.actions=decodeList(value);this.onChange=onChange;this.parseYaml=parseYaml;this.descriptors=descriptors;this.compact=compact;this.mode='visual';this.expanded=new Set();this.draft='';this.error='';this.render();
  }
  list(path,depth=0) {
    const items=find(this.actions,path);
    return `<div class="edsl-list" data-list="${encoded(path)}">${items.map((item,index)=>this.card(item,[...path,index],depth)).join('')}<div class="edsl-add"><button type="button" data-op="menu" data-path="${encoded(path)}">+ Добавить действие</button></div></div>`;
  }
  card(item,path,depth) {
    const ref=encoded(path), complex=['if','if-all','if-one','oneOf','weights'].includes(item.kind), open=this.expanded.has(ref)||depth===0&&item.kind==='weights';
    const descriptor=item.kind==='command'?this.descriptors[item.key]:null;
    const label=item.kind==='raw'?'Raw action':descriptor?.label??labels[item.kind==='command'?item.key:item.kind]??`Unknown: ${item.key}`;
    let summary='';
    if(item.kind==='command') summary=typeof item.value==='string'?item.value:JSON.stringify(item.value);
    if(item.kind==='raw') summary=JSON.stringify(item.value);
    if(['if','if-all','if-one'].includes(item.kind)) summary=`${Array.isArray(item.condition)?item.condition.join(' · '):item.condition} · ${item.then.length} then${item.else===null?'':` · ${item.else.length} else`}`;
    if(item.kind==='weights') summary=`${item.branches.length} variants`;
    if(item.kind==='oneOf') summary=`${item.cases.length} cases`;
    const inline=item.kind==='command'&&typeof item.value==='string'&&!descriptor?.render;
    const title=`<div class="edsl-head"><span class="edsl-grip" draggable="true" data-drag="${ref}" title="Перетащить">⋮⋮</span><button type="button" class="edsl-title" data-op="expand" data-path="${ref}">${complex?(open?'▾ ':'▸ '):''}${esc(label)}</button>${inline?`<div class="edsl-inline">${this.simple(item,path,descriptor)}</div>`:`<span class="edsl-summary">${esc(summary)}</span>`}<div class="edsl-tools">${rowButtons()}</div></div>`;
    let body='';
    if(complex&&open) body=this.complex(item,path,depth);
    else if(!complex&&!inline) body=`<div class="edsl-detail">${this.simple(item,path,descriptor)}</div>`;
    return `<div class="edsl-card${complex?' edsl-complex':''}" data-card="${ref}">${title}${body}</div>`;
  }
  simple(item,path,descriptor) {
    const ref=encoded(path);
    if(item.kind==='raw') return `<textarea data-value="${ref}" data-format="json" rows="4" aria-label="Raw action JSON">${esc(JSON.stringify(item.value,null,2))}</textarea><small class="edsl-local-error"></small>`;
    if(descriptor?.render) return descriptor.render(item,ref,esc);
    if(item.key==='set_value'&&typeof item.value==='string') {
      const match=item.value.match(/^(\S+)(?:\s+([\s\S]*))?$/);
      return `<div class="edsl-pair"><input data-set-key="${ref}" value="${esc(match?.[1]??'')}" placeholder="key" aria-label="Имя значения"><input data-set-value="${ref}" value="${esc(match?.[2]??'')}" placeholder="value" aria-label="Значение"></div>`;
    }
    if(typeof item.value==='string') return `<textarea data-value="${ref}" rows="${Math.min(10,Math.max(1,item.value.split('\n').length))}" aria-label="${esc(item.key)}" placeholder="${esc(item.key)}">${esc(item.value)}</textarea>`;
    return `<textarea data-value="${ref}" data-format="json" rows="3" aria-label="Значение ${esc(item.key)}">${esc(JSON.stringify(item.value,null,2))}</textarea><small class="edsl-local-error"></small>`;
  }
  complex(item,path,depth) {
    const ref=encoded(path);
    if(['if','if-all','if-one'].includes(item.kind)) {
      const conditions=item.kind==='if'?`<input data-condition="${ref}" value="${esc(item.condition)}" placeholder="Условие">`:`<div>${(Array.isArray(item.condition)?item.condition:[]).map((v,i)=>`<div class="edsl-condition"><input data-condition="${ref}" data-index="${i}" value="${esc(v)}" placeholder="Условие"><button data-op="remove-condition" data-path="${ref}" data-index="${i}">×</button></div>`).join('')}<button data-op="add-condition" data-path="${ref}">+ Условие</button></div>`;
      return `<div class="edsl-body"><div class="edsl-condition-block">${conditions}</div><div class="edsl-branch-label">THEN</div>${this.list([...path,'then'],depth+1)}<div class="edsl-branch-label">ELSE ${item.else===null?`<button data-op="add-else" data-path="${ref}">+ Добавить</button>`:`<button data-op="remove-else" data-path="${ref}">Убрать</button>`}</div>${item.else===null?'':this.list([...path,'else'],depth+1)}</div>`;
    }
    if(item.kind==='weights') return `<div class="edsl-body">${item.branches.map((branch,i)=>`<div class="edsl-variant" data-group="${encoded([...path,'branches',i])}"><div class="edsl-group-head"><span class="edsl-grip" draggable="true" data-group-drag="${encoded([...path,'branches',i])}">⋮⋮</span><label>Вес <input data-weight="${ref}" data-index="${i}" value="${esc(branch.weight)}"></label>${rowButtons()}</div>${this.list([...path,'branches',i,'actions'],depth+1)}</div>`).join('')}<button data-op="add-variant" data-path="${ref}">+ Вариант</button></div>`;
    return `<div class="edsl-body">${item.cases.map((entry,i)=>`<div class="edsl-variant" data-group="${encoded([...path,'cases',i])}"><div class="edsl-group-head"><span class="edsl-grip" draggable="true" data-group-drag="${encoded([...path,'cases',i])}">⋮⋮</span><label>${i+1}. ${entry.condition===undefined?'OTHERWISE':'IF'}</label>${entry.condition===undefined?'':`<input data-case-condition="${ref}" data-index="${i}" value="${esc(entry.condition)}">`}<button data-op="toggle-fallback" data-path="${ref}" data-index="${i}" title="Условие или fallback">⇄</button>${rowButtons()}</div>${entry.actions?this.list([...path,'cases',i,'actions'],depth+1):`<textarea data-case-raw="${ref}" data-index="${i}">${esc(JSON.stringify(entry.raw,null,2))}</textarea>`}</div>`).join('')}<button data-op="add-case" data-path="${ref}">+ Case</button></div>`;
  }
  render() {
    this.host.classList.add('edsl-editor');if(this.compact)this.host.classList.add('edsl-compact');
    this.host.innerHTML=`<div class="edsl-toolbar"><b>Действия</b><div><button data-op="visual" class="${this.mode==='visual'?'active':''}">Visual</button><button data-op="yaml" class="${this.mode==='yaml'?'active':''}">YAML</button></div></div>${this.mode==='yaml'?`<textarea class="edsl-yaml" spellcheck="false" rows="12">${esc(this.draft)}</textarea><button data-op="apply-yaml">Применить YAML</button><small class="edsl-error">${esc(this.error)}</small>`:this.list([])}`;
    this.bind();
  }
  emit() { const errors=validateList(this.actions);if(errors.length){const [path,message]=errors[0];this.error=message;this.render();const card=this.host.querySelector(`[data-card="${path}"]`);(card??this.host).insertAdjacentHTML('beforeend',`<small class="edsl-local-error">${esc(message)}</small>`);return;} this.error='';this.onChange(encodeList(this.actions));this.render(); }
  bind() {
    this.host.onclick=event=>{const button=event.target.closest('[data-op]');if(!button||!this.host.contains(button))return;const op=button.dataset.op,path=(button.dataset.path??button.closest('[data-card],[data-group]')?.dataset.card??button.closest('[data-group]')?.dataset.group??'').split('/').filter(Boolean).map(v=>/^\d+$/.test(v)?Number(v):v);
      if(op==='yaml'){this.draft=formatActions(encodeList(this.actions));this.mode='yaml';this.error='';this.render();return;}
      if(op==='visual'){this.applyYaml();return;}
      if(op==='apply-yaml'){this.applyYaml();return;}
      if(op==='menu'){const menu=button.nextElementSibling;if(menu?.classList.contains('edsl-menu')){menu.remove();return;}const choices=[...types,...Object.keys(this.descriptors).filter(k=>!types.includes(k))];button.insertAdjacentHTML('afterend',`<div class="edsl-menu">${choices.map(type=>`<button type="button" data-op="add" data-type="${esc(type)}" data-path="${encoded(path)}">${esc(type==='raw'?'Raw / unknown':this.descriptors[type]?.label??labels[type]??type)}</button>`).join('')}</div>`);return;}
      try {
        if(op==='add'){find(this.actions,path).push(this.descriptors[button.dataset.type]?.create?.()??newAction(button.dataset.type));this.emit();return;}
        if(op==='expand'){const ref=encoded(path);this.expanded.has(ref)?this.expanded.delete(ref):this.expanded.add(ref);this.render();return;}
        const group=button.closest('[data-group]');
        if(group&&['up','down','delete','duplicate'].includes(op)){const p=group.dataset.group.split('/').map(v=>/^\d+$/.test(v)?Number(v):v),index=p.at(-1),list=find(this.actions,p.slice(0,-1));this.move(list,index,op);this.emit();return;}
        if(['up','down','delete','duplicate'].includes(op)){const index=path.at(-1),list=find(this.actions,path.slice(0,-1));this.move(list,index,op);this.emit();return;}
        const item=find(this.actions,path),index=Number(button.dataset.index);
        if(op==='add-condition') item.condition.push('');
        if(op==='remove-condition') item.condition.splice(index,1);
        if(op==='add-else') item.else=[];
        if(op==='remove-else') item.else=null;
        if(op==='add-variant'){let n=1;while(item.branches.some(v=>v.weight===String(n)))n++;item.branches.push({weight:String(n),actions:[]});}
        if(op==='add-case') item.cases.push({condition:'',actions:[],extra:{}});
        if(op==='toggle-fallback'){const entry=item.cases[index];entry.condition=entry.condition===undefined?'':undefined;}
        this.expanded.add(encoded(path));this.emit();
      }catch(error){this.showError(button,error.message);}
    };
    this.host.onchange=event=>{const input=event.target;if(input.classList.contains('edsl-yaml')){this.draft=input.value;return;}const ref=input.dataset.value??input.dataset.setKey??input.dataset.setValue??input.dataset.condition??input.dataset.weight??input.dataset.caseCondition??input.dataset.caseRaw??input.dataset.actionPath;if(ref===undefined)return;
      const path=ref.split('/').filter(Boolean).map(v=>/^\d+$/.test(v)?Number(v):v),item=find(this.actions,path);
      try {
        if(input.dataset.value!==undefined&&input.dataset.actionField===undefined)item.value=input.dataset.format==='json'?JSON.parse(input.value):input.value;
        if(input.dataset.setKey!==undefined||input.dataset.setValue!==undefined){const key=this.host.querySelector(`[data-set-key="${ref}"]`).value.trim(),value=this.host.querySelector(`[data-set-value="${ref}"]`).value;item.value=`${key} ${value}`;}
        if(input.dataset.condition!==undefined){if(input.dataset.index===undefined)item.condition=input.value;else item.condition[Number(input.dataset.index)]=input.value;}
        if(input.dataset.weight!==undefined)item.branches[Number(input.dataset.index)].weight=input.value.trim();
        if(input.dataset.caseCondition!==undefined)item.cases[Number(input.dataset.index)].condition=input.value;
        if(input.dataset.caseRaw!==undefined)item.cases[Number(input.dataset.index)].raw=JSON.parse(input.value);
        if(input.dataset.actionField!==undefined)this.descriptors[item.key]?.update?.(item,input.dataset.actionField,input.value);
        this.emit();
      }catch(error){this.showError(input,error.message);}
    };
    this.host.oninput=event=>{if(event.target.classList.contains('edsl-yaml'))this.draft=event.target.value;if(event.target.tagName==='TEXTAREA'&&!event.target.classList.contains('edsl-yaml')){event.target.style.height='auto';event.target.style.height=`${event.target.scrollHeight}px`;}};
    this.host.ondragstart=event=>{const grip=event.target.closest('[data-drag],[data-group-drag]');if(!grip)return;this.dragPath=grip.dataset.drag??grip.dataset.groupDrag;event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',this.dragPath);};
    this.host.ondragover=event=>{if(event.target.closest('[data-card],[data-group]'))event.preventDefault();};
    this.host.ondrop=event=>{const target=event.target.closest('[data-card],[data-group]');if(!target||!this.dragPath)return;event.preventDefault();const a=this.dragPath.split('/'),b=(target.dataset.card??target.dataset.group).split('/');this.dragPath=null;if(a.length!==b.length||a.slice(0,-1).join('/')!==b.slice(0,-1).join('/'))return;const list=find(this.actions,a.slice(0,-1).map(v=>/^\d+$/.test(v)?Number(v):v)),from=Number(a.at(-1)),to=Number(b.at(-1));list.splice(to,0,list.splice(from,1)[0]);this.emit();};
  }
  move(list,index,op){if(op==='delete')list.splice(index,1);if(op==='duplicate')list.splice(index+1,0,clone(list[index]));if(op==='up'&&index>0)[list[index-1],list[index]]=[list[index],list[index-1]];if(op==='down'&&index<list.length-1)[list[index+1],list[index]]=[list[index],list[index+1]];}
  showError(element,message){let error=element.closest('.edsl-card,.edsl-variant')?.querySelector('.edsl-local-error');if(!error){error=document.createElement('small');error.className='edsl-local-error';element.parentElement.append(error);}error.textContent=message;}
  async applyYaml(){if(this.mode!=='yaml'){this.mode='visual';this.render();return;}const draft=this.draft,request=Symbol();this.yamlRequest=request;try{const values=await this.parseYaml(draft);if(this.yamlRequest!==request||this.draft!==draft)return;const next=decodeList(values);const errors=validateList(next);if(errors.length)throw new Error(errors[0][1]);this.actions=next;this.onChange(encodeList(next));this.mode='visual';this.error='';this.render();}catch(error){if(this.yamlRequest!==request||this.draft!==draft)return;this.error=error.message;this.host.querySelector('.edsl-error').textContent=this.error;}}
}
export function makeDescriptor(){return {label:'Создать аирдроп',create:()=>({kind:'command',key:'make',value:{loot_table:'{value:loot_table}',type:'{value:type}'}}),update:(item,field,value)=>{item.value[field]=value;},render:(item,ref,esc)=>{if(!item.value||typeof item.value!=='object'||Array.isArray(item.value))return `<textarea data-value="${ref}" data-format="json">${esc(JSON.stringify(item.value,null,2))}</textarea>`;return `<div class="edsl-make">${[...new Set(['loot_table','type',...Object.keys(item.value)])].map(key=>`<label>${esc(key)}<input data-action-field="${esc(key)}" data-action-path="${ref}" value="${esc(item.value[key]??'')}"></label>`).join('')}</div>`;}};}
