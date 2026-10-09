import {EditorState, EditorSelection, Prec, Transaction} from '@codemirror/state';
import {EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter, drawSelection, rectangularSelection, tooltips} from '@codemirror/view';
import {defaultKeymap, history, historyKeymap, isolateHistory, indentMore, indentLess, indentSelection, copyLineDown, deleteLine, moveLineUp, moveLineDown, toggleComment, insertBlankLine, redo} from '@codemirror/commands';
import {indentUnit, bracketMatching, foldGutter, foldKeymap, syntaxHighlighting, HighlightStyle} from '@codemirror/language';
import {yaml} from '@codemirror/lang-yaml';
import {autocompletion, completionKeymap, startCompletion, acceptCompletion, closeBrackets, closeBracketsKeymap, snippetCompletion} from '@codemirror/autocomplete';
import {searchKeymap, highlightSelectionMatches, openSearchPanel} from '@codemirror/search';
import {tags} from '@lezer/highlight';
import {completionAt, inLiteralBlock} from './event-dsl-completion.js';

export const yamlLanguageExtensions = [yaml(), indentUnit.of('  '), EditorState.tabSize.of(2)];

export function yamlNewline(doc, pos) {
  const start=doc.lastIndexOf('\n',pos-1)+1, line=doc.slice(start,pos);
  const indent=line.match(/^ */)[0], sequence=/^ *-\s/.test(line);
  if(inLiteralBlock(doc,pos)||/^ *#/.test(line))return '\n'+indent;
  if (/:\s*(?:[|>][+-]?)?\s*(?:#.*)?$/.test(line)) return '\n'+indent+' '.repeat(sequence?4:2);
  if (/^ *-\s+(?:if|if-all|if-one):/.test(line)) return '\n'+indent+'  ';
  if (sequence && !doc.slice(pos,doc.indexOf('\n',pos)<0?doc.length:doc.indexOf('\n',pos)).trim()) return '\n'+indent+'- ';
  return '\n'+indent;
}
export function insertYamlNewline({state,dispatch}) {
  dispatch(state.update(state.changeByRange(range=>{
    const insert=yamlNewline(state.doc.toString(),range.from);
    return {changes:{from:range.from,to:range.to,insert},range:EditorSelection.cursor(range.from+insert.length)};
  }),{scrollIntoView:true,userEvent:'input'}));
  return true;
}
const darcula = HighlightStyle.define([
  {tag:[tags.string,tags.content], color:'#6aab73'}, {tag:[tags.propertyName,tags.attributeName], color:'#c9a7eb'},
  {tag:[tags.number,tags.bool,tags.null], color:'#6897bb'}, {tag:tags.comment, color:'#808080',fontStyle:'italic'},
  {tag:[tags.punctuation,tags.meta], color:'#cc7832'}, {tag:tags.keyword, color:'#cc7832'}
]);
const theme = EditorView.theme({
  '&':{color:'#bcbec4',backgroundColor:'#2b2d30',fontSize:'13px',height:'100%'},
  '.cm-scroller':{fontFamily:'"JetBrains Mono", ui-monospace, SFMono-Regular, Consolas, monospace',lineHeight:'1.6',overflow:'auto'},
  '.cm-content':{padding:'8px 0',minHeight:'100%',caretColor:'#ffffff'},
  '.cm-cursor':{borderLeftColor:'#fff'}, '.cm-gutters':{backgroundColor:'#2b2d30',color:'#6f737a',borderRight:'1px solid #393b40'},
  // The selection layer sits behind the text; an opaque active line hides it.
  '.cm-activeLine':{backgroundColor:'#ffffff08'},
  '.cm-activeLineGutter':{backgroundColor:'#323437'},
  '&.cm-editor .cm-selectionLayer .cm-selectionBackground':{backgroundColor:'#3b526d'},
  '&.cm-editor.cm-focused > .cm-scroller > .cm-selectionLayer.cm-layer .cm-selectionBackground':{backgroundColor:'#436fa8'},
  '.cm-tooltip':{backgroundColor:'#393b40',color:'#bcbec4',border:'1px solid #55575d'},
  '.cm-tooltip-autocomplete > ul > li[aria-selected]':{backgroundColor:'#355880',color:'#fff'},
  '.cm-searchMatch':{backgroundColor:'#685b2b'}, '.cm-searchMatch-selected':{backgroundColor:'#987c30'},
  '.cm-panels':{backgroundColor:'#393b40',color:'#bcbec4'}, '.cm-foldPlaceholder':{backgroundColor:'#393b40',borderColor:'#55575d',color:'#bcbec4'}
},{dark:true});

// Overrides precede standard bindings so Ctrl+Y deletes a line, as in IntelliJ.
export const intellijKeymap = [
  {key:'Enter',run:view=>acceptCompletion(view)||insertYamlNewline(view)},
  {key:'Mod-d',run:copyLineDown,preventDefault:true}, {key:'Mod-y',run:deleteLine,preventDefault:true},
  {key:'Mod-/',run:toggleComment}, {key:'Shift-Alt-ArrowUp',run:moveLineUp}, {key:'Shift-Alt-ArrowDown',run:moveLineDown},
  {key:'Ctrl-Shift-ArrowUp',run:moveLineUp}, {key:'Ctrl-Shift-ArrowDown',run:moveLineDown},
  {key:'Mod-Alt-l',run:indentSelection,preventDefault:true}, {key:'Mod-Enter',run:insertBlankLine},
  {key:'Mod-Shift-z',run:redo}, {key:'Ctrl-Space',run:startCompletion},
  {key:'Mod-f',run:openSearchPanel},
  {key:'Tab',run:view=>acceptCompletion(view)||indentMore(view)}, {key:'Shift-Tab',run:indentLess}
];

export class YamlEditor {
  constructor(host,{value='',onChange=()=>{},onBlur=()=>{},context=()=>({}),label='Команды EventDSL в YAML'}={}) {
    this.extensions=[
      ...yamlLanguageExtensions, theme, syntaxHighlighting(darcula), history(),
      EditorState.transactionExtender.of(tr=>tr.docChanged && ['input','input.copyline','input.paste','delete.line','move.line'].includes(tr.annotation(Transaction.userEvent)) ? {annotations:isolateHistory.of('full')} : null),
      lineNumbers(), foldGutter(), bracketMatching(),
      drawSelection(), rectangularSelection(), highlightActiveLine(), highlightActiveLineGutter(), highlightSelectionMatches(), closeBrackets(), tooltips({parent:document.body}),
      autocompletion({override:[completion=>{
        const result=completionAt(completion.state.doc.toString(),completion.pos,{...context(),explicit:completion.explicit});
        if (!result || !result.options.length) return null;
        return {...result,options:result.options.map(item=>item.snippet ? snippetCompletion(item.snippet,item) : item)};
      }]}),
      Prec.highest(keymap.of(intellijKeymap)),
      keymap.of([...completionKeymap,...closeBracketsKeymap,...historyKeymap,...searchKeymap,...foldKeymap,...defaultKeymap]),
      EditorView.contentAttributes.of({'aria-label':label,spellcheck:'false'}),
      EditorView.updateListener.of(update=>{if(update.docChanged)onChange(update.state.doc.toString());}),
      EditorView.domEventHandlers({blur:()=>{queueMicrotask(()=>{if(!this.view.hasFocus)onBlur();});}})
    ];
    this.view = new EditorView({parent:host,state:EditorState.create({doc:value,extensions:this.extensions})});
  }
  get value(){return this.view.state.doc.toString();}
  setValue(value){this.view.setState(EditorState.create({doc:value,extensions:this.extensions}));}
  mount(host){host.append(this.view.dom);this.view.requestMeasure();}
  destroy(){this.view.destroy();}
}
