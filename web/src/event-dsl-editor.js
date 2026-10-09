import {YamlEditor} from './yaml-editor.js';
import {formatActions} from './command-yaml.js';
const list = value => value==null ? [] : Array.isArray(value) ? value : [value];
export class EventDslEditor {
  constructor(host,{value=[],onChange,parseYaml,context=()=>({}),compact=false,onDraft=()=>{},onError=()=>{}}={}) {
    this.onChange=onChange;this.parseYaml=parseYaml;this.onError=onError;this.dirty=false;this.base=JSON.stringify(list(value));
    this.element=document.createElement('div');this.element.className=`edsl-yaml-editor${compact?' edsl-yaml-compact':''}`;
    this.element.innerHTML=`<div class="edsl-toolbar"><b>${compact?'YAML':'Команды · YAML'}</b><div><button type="button" data-expand-yaml title="Развернуть редактор">⛶</button>${compact?'':'<button type="button" data-apply-yaml>Применить</button>'}</div></div><div class="yaml-editor-surface"></div><small class="edsl-error" role="status"></small>${compact?'':'<details class="yaml-help"><summary>Бинды IntelliJ и примеры YAML</summary><p>Ctrl+D — дублировать · Ctrl+Y — удалить строку · Ctrl+/ — комментарий · Ctrl+Z / Ctrl+Shift+Z — отмена / повтор · Shift+Alt+↑/↓ или Ctrl+Shift+↑/↓ — переместить · Ctrl+Alt+L — отступы · Ctrl+F — поиск · Ctrl+Space — подсказки · Tab / Shift+Tab — отступ · Ctrl+Enter — новая строка. На macOS вместо Ctrl — ⌘ (подсказки: Ctrl+Space).</p><pre>- nearby: 10 [sound] block.note_block.pling 1 1\n- if: "условие"\n  do:\n    - message: "Сообщение"\n  else:\n    - console: "say Другое сообщение"\n- weights:\n    "10":\n      - message: "Первый вариант"\n    "20":\n      - message: "Второй вариант"</pre></details>'}`;
    this.error=this.element.querySelector('.edsl-error');
    this.editor=new YamlEditor(this.element.querySelector('.yaml-editor-surface'),{value:formatActions(list(value)),context,
      onChange:draft=>{this.dirty=true;onDraft(draft);},onBlur:()=>{this.commit().catch(()=>{});}});
    this.element.querySelector('[data-apply-yaml]')?.addEventListener('click',()=>this.commit().catch(()=>{}));
    this.element.querySelector('[data-expand-yaml]').addEventListener('click',()=>this.expand());
    this.element.addEventListener('keydown',event=>{if(event.key==='Escape'&&this.expanded&&!event.defaultPrevented){event.preventDefault();event.stopPropagation();this.expand();}});
    this.mount(host);
  }
  get value(){return this.editor.value;}
  mount(host){this.host=host;if(!this.expanded)host.append(this.element);this.editor.view.requestMeasure();}
  expand(){
    this.expanded=!this.expanded;this.element.classList.toggle('yaml-editor-expanded',this.expanded);
    const button=this.element.querySelector('[data-expand-yaml]');button.textContent=this.expanded?'Закрыть ⛶':'⛶';
    (this.expanded?document.body:this.host).append(this.element);this.editor.view.requestMeasure();this.editor.view.focus();
  }
  sync(value){
    const next=JSON.stringify(list(value));
    if (this.dirty || this.base===next) return;
    this.base=next;
    // Recreate the state when the graph's own undo or an external edit changes the source.
    this.editor.setValue(formatActions(list(value)));
  }
  async commit(){
    if (!this.dirty || this.destroyed) return;
    const draft=this.value;
    if (this.pending?.draft===draft) return this.pending.promise;
    const promise=(async()=>{
      try {
        const values=await this.parseYaml(draft);
        if(this.destroyed)return;
        if(this.value!==draft)return this.commit();
        this.onChange(values);
        this.base=JSON.stringify(values);this.dirty=false;this.error.textContent='';this.onError(null);
      } catch(error) {
        if(this.destroyed||this.value!==draft)return;
        this.error.textContent=error.message;this.onError(error);throw error;
      }
    })();
    this.pending={draft,promise};
    try {await promise;} finally {if(this.pending?.promise===promise)this.pending=null;}
  }
  destroy(){this.destroyed=true;this.editor.destroy();this.element.remove();}
}
