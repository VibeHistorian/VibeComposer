/** Musical impact categories shared by the quick panel and inspector across roles. */
export const PART_CONTROL_GROUPS = [
  { id: 'rhythm', label: 'Rhythm', keys: ['speed', 'hitsPerPattern', 'rhythm', 'euclideanPulses', 'patternShift', 'patternFlip', 'customPattern', 'pauseChance', 'fillPauses', 'humanize'] },
  { id: 'pitch', label: 'Pitch', keys: ['transpose', 'chordNoteChoices', 'octaveInterval', 'octaves', 'voicing', 'chordNotesStretch', 'stretchEnabled'] },
  { id: 'structure', label: 'Structure', keys: ['chordSpanFill', 'fillFlip', 'chordSpan', 'patternRepeat', 'melodyPatternOffsets', 'patternJoinMode'] },
  { id: 'shape', label: 'Shape', keys: ['noteVariation', 'maxBlockChange', 'blockJump', 'patternFlexible', 'pattern', 'transitionChance', 'transitionSplit', 'strum', 'strumType', 'strumPauseChance'] },
  { id: 'timing', label: 'Timing', keys: ['noteLengthMultiplier', 'noteLengthPercent', 'exceptionChance', 'swingPercent', 'offset', 'feedbackCount', 'feedbackDuration'] },
  { id: 'dynamics', label: 'Dynamics', keys: ['velocityMin', 'velocityMax', 'useCustomVelocities', 'customVelocities', 'accents', 'feedbackVol'] },
  { id: 'other', label: 'Other', keys: [] },
] as const;

export function partControlGroup(key: string): string {
  if (['fillPausesPerChord', 'splitChance', 'startNoteChance'].includes(key)) return 'rhythm';
  if (['arpPatternRotate', 'arpPatternCustom', 'arpContour', 'arpContourChordMode'].includes(key)) return 'shape';
  if (key === 'useOctaveAdjustments') return 'pitch';
  if (['targetDirection', 'tonicNoteTarget', 'modeNoteTarget', 'chordNoteTarget', 'emphasizeKey'].includes(key)) return 'pitch';
  if (['maxDirectionChanges', 'newBlocksChance'].includes(key)) return 'shape';
  if (key === 'ghosts') return 'dynamics';
  return PART_CONTROL_GROUPS.find(group => (group.keys as readonly string[]).includes(key))?.id ?? 'other';
}
