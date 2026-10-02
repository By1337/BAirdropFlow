const escape = value => value.replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const valueToken = /("(?:\\.|[^"\\])*"|'(?:''|[^'])*'|#[^\n]*|\{[^{}\n]+\}|\b(?:true|false|null|-?\d+(?:\.\d+)?)\b)/g;

function valueHtml(value) {
  let output = '', offset = 0;
  for (const match of value.matchAll(valueToken)) {
    output += escape(value.slice(offset, match.index));
    const token = match[0];
    const kind = token[0] === '#' ? 'comment' : token[0] === '"' || token[0] === "'" ? 'string'
      : token[0] === '{' ? 'placeholder' : /^-?\d/.test(token) ? 'number' : 'literal';
    output += `<span class="yaml-${kind}">${escape(token)}</span>`;
    offset = match.index + token.length;
  }
  return output + escape(value.slice(offset));
}

export function highlightYaml(yaml) {
  return String(yaml ?? '').split('\n').map(line => {
    const match = /^(\s*)(-\s+)?((?:[\w.-]+|"[^"]+"|'[^']+'))(\s*:)(.*)$/.exec(line);
    if (!match) {
      const list = /^(\s*)(-\s+)(.*)$/.exec(line);
      return list ? escape(list[1]) + `<span class="yaml-punctuation">${escape(list[2])}</span>` + valueHtml(list[3]) : valueHtml(line);
    }
    return escape(match[1]) + (match[2] ? `<span class="yaml-punctuation">${escape(match[2])}</span>` : '')
      + `<span class="yaml-key">${escape(match[3])}</span><span class="yaml-punctuation">${escape(match[4])}</span>` + valueHtml(match[5]);
  }).join('\n') + '\n';
}
