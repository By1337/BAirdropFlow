export function renamePlaceholderText(text, oldName, newName) {
  return text.split(`{ctx:${oldName}.`).join(`{ctx:${newName}.`);
}

export function countPlaceholderUses(value, token) {
  if (typeof value === 'string') return value.split(token).length - 1;
  if (Array.isArray(value)) return value.reduce((count, item) => count + countPlaceholderUses(item, token), 0);
  if (!value || typeof value !== 'object') return 0;
  return Object.entries(value).reduce((count, [key, item]) => count + countPlaceholderUses(key, token) + countPlaceholderUses(item, token), 0);
}

export function renameLiteralPlaceholders(value, oldToken, newToken) {
  if (typeof value === 'string') return value.split(oldToken).join(newToken);
  if (Array.isArray(value)) return value.map(item => renameLiteralPlaceholders(item, oldToken, newToken));
  if (!value || typeof value !== 'object') return value;
  const keys = new Set();
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    const renamed = key.split(oldToken).join(newToken);
    if (keys.has(renamed)) throw new Error(`Переименование создаёт повторяющийся ключ: ${renamed}`);
    keys.add(renamed);
    return [renamed, renameLiteralPlaceholders(item, oldToken, newToken)];
  }));
}

export function renameNodePlaceholders(value, oldName, newName) {
  return renameLiteralPlaceholders(value, `{ctx:${oldName}.`, `{ctx:${newName}.`);
}

export function nodePlaceholders(nodes, schemas) {
  return Object.entries(nodes).flatMap(([node, config]) => {
    const schema = schemas[config.type] ?? schemas[`default:${config.type}`];
    return Object.entries(schema?.['x-placeholders'] ?? {}).map(([key, info]) => ({
      node, key, ...info, value: `{ctx:${node}.${key}}`
    }));
  });
}
