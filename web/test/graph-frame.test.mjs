import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createContext, runInContext} from 'node:vm';

const source=readFileSync(new URL('../src/app.js',import.meta.url),'utf8');
const functions=['cancelGraphFrame','scheduleGraphFrame'].map(name=>{
  const start=source.indexOf(`function ${name}(`);
  return source.slice(start,source.indexOf('\nfunction ',start+1));
}).join('\n');
function scheduler() {
  const frames=new Map(), views=[];let id=0;
  const context=createContext({
    requestAnimationFrame:callback=>{frames.set(++id,callback);return id;},
    cancelAnimationFrame:key=>frames.delete(key),
    drawMinimap() {}, $:()=>({}),
    graphRenderer:{scene:{isConnected:true},viewport:view=>views.push({...view}),box() {},preview() {}}
  });
  runInContext(`let graphFrame=null, graphPointer=null, graphView={x:0,y:0,zoom:1}, drag=null, linking=null;
    const selectedNodes=new Set(); ${functions}`,context);
  return {frames,views,run:code=>runInContext(code,context),flush(){const pending=[...frames.values()];frames.clear();pending.forEach(callback=>callback());}};
}

test('a burst of input events renders only the latest viewport once per frame',()=>{
  const s=scheduler();
  s.run('for (let i=0;i<100;i++) {graphView.x=i; scheduleGraphFrame();}');
  assert.equal(s.frames.size,1);
  assert.equal(s.views.length,0);
  s.flush();
  assert.deepEqual(s.views,[{x:99,y:0,zoom:1}]);
  s.run('graphView.zoom=.5; scheduleGraphFrame();');
  s.flush();
  assert.deepEqual(s.views[1],{x:99,y:0,zoom:.5});
});

test('full redraw / cancellation clears pending work and detached graphs are never updated',()=>{
  const s=scheduler();
  s.run('scheduleGraphFrame({clientX:10,clientY:20}); cancelGraphFrame();');
  assert.equal(s.frames.size,0);
  assert.equal(s.run('graphPointer'),null);
  s.run('scheduleGraphFrame(); graphRenderer.scene.isConnected=false;');
  s.flush();
  assert.equal(s.views.length,0);
  s.run('graphRenderer.scene.isConnected=true; scheduleGraphFrame();');
  s.flush();
  assert.equal(s.views.length,1);
});
