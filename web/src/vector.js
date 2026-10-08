export function splitVector(value) {
  return String(value ?? '').split(';');
}

export function joinVector(parts, fractional) {
  const values = parts.map(part => String(part).trim());
  // A placeholder may resolve to an entire vector, rather than one coordinate.
  if (values.length === 3 && /^\{[^{}]+\}$/.test(values[0]) && values[1] === '' && values[2] === '') return values[0];
  return values.join(';');
}
