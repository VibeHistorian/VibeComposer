/** Musical impact categories shared by the quick panel and inspector across roles. */
export const PART_CONTROL_GROUPS = [
  { id: 'core', label: 'Core', keys: ['generationEnabled', 'patternSeed', 'speed', 'hitsPerPattern', 'noteLengthMultiplier', 'noteLengthPercent'] },
  { id: 'rhythm', label: 'Rhythm', keys: ['rhythm', 'euclideanPulses', 'patternShift', 'patternFlip', 'customPattern', 'pauseChance', 'exceptionChance', 'swingPercent', 'offset', 'fillPauses', 'feedbackCount', 'feedbackDuration'] },
  { id: 'pitch', label: 'Pitch', keys: ['transpose', 'chordNoteChoices', 'octaveInterval', 'octaves', 'voicing', 'chordNotesStretch', 'stretchEnabled'] },
  { id: 'shape', label: 'Shape', keys: ['noteVariation', 'maxBlockChange', 'blockJump', 'patternFlexible', 'pattern'] },
  { id: 'dynamics', label: 'Dynamics', keys: ['velocityMin', 'velocityMax', 'useCustomVelocities', 'customVelocities', 'accents', 'feedbackVol'] },
  { id: 'structure', label: 'Structure', keys: ['chordSpanFill', 'fillFlip', 'chordSpan', 'patternRepeat', 'melodyPatternOffsets', 'patternJoinMode'] },
  { id: 'other', label: 'Other', keys: [] },
] as const;

export function partControlGroup(key: string): string {
  return PART_CONTROL_GROUPS.find(group => (group.keys as readonly string[]).includes(key))?.id ?? 'other';
}
