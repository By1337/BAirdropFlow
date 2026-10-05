export function splitVector(value) {
  return String(value ?? '').split(';');
}

export function joinVector(parts, fractional) {
  return parts.map(part => String(part).trim()).join(';');
}
