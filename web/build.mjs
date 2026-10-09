import { mkdir, copyFile } from 'node:fs/promises';
import { build } from 'esbuild';
const output = new URL('./dist/bairdropflow/', import.meta.url);
await mkdir(output, { recursive: true });
await build({entryPoints:[new URL('./src/app.js',import.meta.url).pathname],bundle:true,format:'esm',target:['es2022'],minify:true,sourcemap:true,outfile:new URL('app.js',output).pathname});
for(const name of ['index.html','style.css']) await copyFile(new URL(`./src/${name}`, import.meta.url), new URL(name, output));
console.log('Built web/dist/bairdropflow (CodeMirror bundled locally; no CDN).');
