import { mkdir, copyFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
const output = new URL('./dist/bairdropflow/', import.meta.url);
await mkdir(output, { recursive: true });
for (const name of ['app.js', 'model.js', 'command-yaml.js', 'spawner.js']) execFileSync(process.execPath, ['--check', new URL(`./src/${name}`, import.meta.url).pathname]);
for (const name of ['index.html', 'style.css', 'app.js', 'model.js', 'command-yaml.js', 'spawner.js']) await copyFile(new URL(`./src/${name}`, import.meta.url), new URL(name, output));
console.log('Built web/dist/bairdropflow (static assets, no runtime dependencies).');
