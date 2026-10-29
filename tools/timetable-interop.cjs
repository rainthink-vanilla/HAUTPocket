const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const original = path.resolve(__dirname, '../../univ-timetable-public');
const ts = require(path.join(original, 'node_modules/typescript'));
require.extensions['.ts'] = (module, filename) => {
  const result = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  });
  module._compile(result.outputText, filename);
};
// Execute the actual source parser/exporter; no changes to the original project's files.
const { buildTimetableMarkdown, parseTimetableFile } = require(path.join(original, 'src/utils/timetableFile.ts'));
const target = path.resolve(__dirname, '../app/src/test/resources/univ-timetable-export.md');
if (process.argv.includes('--generate')) {
  const text = buildTimetableMarkdown({
    meta: {school: '河南工业大学', semester: '互通测试学期', semesterStart: '2026-09-07', totalWeeks: 20, scrapedAt: '2026-10-27T00:00:00.000Z'},
    courses: [
      {name: '互通测试线代', teacher: '合成教师甲', location: '莲花街 测试教室', dayOfWeek: 2, startPeriod: 1, endPeriod: 2, weeks: [1,3,5], note: '合成备注'},
      {name: '互通测试英语', teacher: '', location: '嵩山路 测试教室', dayOfWeek: 7, startPeriod: 5, endPeriod: 6, weeks: [2,4], note: ''}
    ],
    periods: [{index:1,start:'08:30',end:'09:15'},{index:2,start:'09:20',end:'10:05'},{index:5,start:'14:00',end:'14:45'},{index:6,start:'14:50',end:'15:35'}]
  });
  assert.equal(parseTimetableFile(text).courses.length, 2);
  fs.mkdirSync(path.dirname(target), {recursive:true});
  fs.writeFileSync(target, text);
  console.log('Generated fixture using the actual Univ Timetable exporter: 2 courses, metadata and 4 period times.');
} else {
  const exported = path.resolve(__dirname, '../app/build/interop/haut-export.json');
  const text = fs.readFileSync(exported, 'utf8');
  const parsed = parseTimetableFile(text);
  assert.equal(parsed.courses.length, 3);
  assert.equal(parsed.meta.semesterStart, '2026-09-07');
  assert.equal(parsed.meta.totalWeeks, 20);
  assert.equal(parsed.periods.length, 4);
  assert.equal(parsed.courses.filter(c => c.name === '互通测试线代').length, 2);
  assert.ok(parsed.courses.some(c => c.name === '互通测试线代' && c.startPeriod === 5 && c.endPeriod === 5));
  assert.ok(parsed.courses.some(c => c.note === '合成备注'));
  assert.ok(parsed.courses.some(c => c.dayOfWeek === 7));
  assert.ok(!text.includes('roomverify') && !text.includes('onlineRooms') && !text.includes('studentId'));
  console.log('PASS: HAUT export read by actual Univ Timetable parser; 3 arrangements, notes, calendar and 4 period times preserved.');
}
