import { ARRANGED_PARTS, type ArrangedPart } from '../music/part-role';

/** Match Java score blending, using the independent track position within its role. */
export function trackColor(role: ArrangedPart, index: number, count: number,
  token: (name: string) => string, suffix = '-strong'): string {
  const base = token('--role-' + role + suffix);
  if (index <= 0) return base;
  const next = ARRANGED_PARTS[(ARRANGED_PARTS.indexOf(role) + 1) % ARRANGED_PARTS.length];
  const target = token('--role-' + next + suffix);
  const weight = index / Math.max(index, count) / 1.5;
  return '#' + [1, 3, 5].map(offset => Math.round(
    parseInt(base.slice(offset, offset + 2), 16) * (1 - weight)
    + parseInt(target.slice(offset, offset + 2), 16) * weight)
    .toString(16).padStart(2, '0')).join('') + base.slice(7);
}
