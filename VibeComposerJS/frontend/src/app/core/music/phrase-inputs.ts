import type { CompositionProject } from '../project/project.model';

function equalExcept(left: object, right: object, excluded: readonly string[]): boolean {
  const keys = Object.keys(left).filter(key => !excluded.includes(key));
  return keys.length === Object.keys(right).filter(key => !excluded.includes(key)).length
    && keys.every(key => (left as Record<string, unknown>)[key] === (right as Record<string, unknown>)[key]);
}

/** Projects are immutable. Mix settings do not affect generated note data. */
export function samePhraseInputs(left: CompositionProject, right: CompositionProject): boolean {
  return left === right || equalExcept(left, right, ['mix', 'tracks'])
    && left.tracks.length === right.tracks.length
    && left.tracks.every((track, index) => equalExcept(track, right.tracks[index], ['mix']));
}
