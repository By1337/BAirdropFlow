import test from 'node:test';
import assert from 'node:assert/strict';
import {highlightYaml} from '../src/yaml-highlight.js';

test('highlights YAML commands and escapes markup in code text', () => {
  const html = highlightYaml("- setblock: '{x} {y} {z} stone'\n- console: '<script>' # comment");
  assert.match(html, /yaml-key">setblock/);
  assert.match(html, /yaml-string">&#39;\{x\} \{y\} \{z\} stone&#39;/);
  assert.match(html, /yaml-comment"># comment/);
  assert.ok(!html.includes('<script>'));
});
