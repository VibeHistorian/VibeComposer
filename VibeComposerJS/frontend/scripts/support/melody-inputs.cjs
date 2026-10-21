// Historical Java fixtures used GUIConfig for inputs that now belong to Melody tracks.
const trackKeys = ['targetDirection', 'tonicNoteTarget', 'modeNoteTarget', 'chordNoteTarget', 'emphasizeKey', 'maxDirectionChanges', 'newBlocksChance', 'fillPausesPerChord'];
function splitMelodyInputs(values = {}) {
  const global = {}, track = {};
  for (const [key, value] of Object.entries(values)) (trackKeys.includes(key) ? track : global)[key] = value;
  return { global, track };
}
function rescopeMelodyProject(project) {
  const { global, track } = splitMelodyInputs(project.melodyGlobal);
  return { ...project, melodyGlobal: global, tracks: project.tracks.map(item => item.role === 'melody'
    ? { ...item, generatorSettings: { ...track, ...item.generatorSettings } } : item) };
}
module.exports = { splitMelodyInputs, rescopeMelodyProject };
