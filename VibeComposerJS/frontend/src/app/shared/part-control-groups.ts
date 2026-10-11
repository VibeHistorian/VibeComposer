/** Musical impact categories shared by the quick panel and inspector across roles. */
export const PART_CONTROL_GROUPS = [
  { id: 'rhythm', label: 'Rhythm', keys: ['speed', 'hitsPerPattern', 'rhythm', 'euclideanPulses', 'patternShift', 'patternFlip', 'customPattern', 'pauseChance', 'fillPauses', 'humanize'] },
  { id: 'timing', label: 'Timing', keys: ['noteLengthMultiplier', 'noteLengthPercent', 'exceptionChance', 'swingPercent', 'offset', 'feedbackCount', 'feedbackDuration'] },
  { id: 'pitch', label: 'Pitch', keys: ['transpose', 'chordNoteChoices', 'octaveInterval', 'octaves', 'voicing', 'chordNotesStretch', 'stretchEnabled'] },
  { id: 'shape', label: 'Shape', keys: ['noteVariation', 'maxBlockChange', 'blockJump', 'patternFlexible', 'pattern', 'transitionChance', 'transitionSplit', 'strum', 'strumType', 'strumPauseChance'] },
  { id: 'dynamics', label: 'Dynamics', keys: ['velocityMin', 'velocityMax', 'useCustomVelocities', 'customVelocities', 'accents', 'feedbackVol'] },
  { id: 'structure', label: 'Structure', keys: ['chordSpanFill', 'fillFlip', 'chordSpan', 'patternRepeat', 'melodyPatternOffsets', 'patternJoinMode'] },
  { id: 'other', label: 'Other', keys: [] },
] as const;

export function partControlGroup(key: string): string {
  if (['doubledRhythmChance', 'splitChance', 'leadChordsChance', 'startNoteChance'].includes(key)) return 'rhythm';
  if (['maxNoteExceptions', 'noteExceptionChance'].includes(key)) return 'pitch';
  if (['arpPatternRotate', 'arpPatternCustom', 'arpContour', 'arpContourChordMode'].includes(key)) return 'shape';
  if (['useOctaveAdjustments', 'correctMelodyNotes'].includes(key)) return 'pitch';
  if (key === 'ghosts') return 'dynamics';
  return PART_CONTROL_GROUPS.find(group => (group.keys as readonly string[]).includes(key))?.id ?? 'other';
}
