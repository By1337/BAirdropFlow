export function splitVector(value) {
  return String(value ?? '').split(';');
}

export function joinVector(parts, fractional) {
  const pattern = fractional ? /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/ : /^[+-]?\d+$/;
  if (parts.length !== 3 || parts.some(part => {
    const text = String(part).trim();
    const number = Number(text);
    return !pattern.test(text) || !Number.isFinite(number) || (!fractional && (number < -2147483648 || number > 2147483647));
  })) {
    throw new Error(fractional ? 'Введите числовые координаты X, Y и Z' : 'Введите целые координаты X, Y и Z');
  }
  return parts.map(part => String(part).trim()).join(';');
}
