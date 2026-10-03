export function renamePlaceholderText(text, oldName, newName) {
  return text.split(`{ctx:${oldName}.`).join(`{ctx:${newName}.`);
}

export function renameNodePlaceholders(value, oldName, newName) {
  if (typeof value === 'string') return renamePlaceholderText(value, oldName, newName);
  if (Array.isArray(value)) return value.map(item => renameNodePlaceholders(item, oldName, newName));
  if (!value || typeof value !== 'object') return value;
  const keys = new Set();
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    const renamed = renamePlaceholderText(key, oldName, newName);
    if (keys.has(renamed)) throw new Error(`Переименование создаёт повторяющийся ключ: ${renamed}`);
    keys.add(renamed);
    return [renamed, renameNodePlaceholders(item, oldName, newName)];
  }));
}

export function nodePlaceholders(nodes, schemas) {
  return Object.entries(nodes).flatMap(([node, config]) => {
    const schema = schemas[config.type] ?? schemas[`default:${config.type}`];
    return Object.entries(schema?.['x-placeholders'] ?? {}).map(([key, info]) => ({
      node, key, ...info, value: `{ctx:${node}.${key}}`
    }));
  });
}
