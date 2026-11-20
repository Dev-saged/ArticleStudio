const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const ROOT = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'social');
const lib = fs.readFileSync(path.join(ROOT, 'automation.js'), 'utf8');
const recipes = JSON.parse(fs.readFileSync(path.join(ROOT, 'recipes.json'), 'utf8'));

function page(html, url) {
  const dom = new JSDOM(html, { url, runScripts: 'outside-only', pretendToBeVisual: true });
  const w = dom.window;
  w.HTMLElement.prototype.focus = function () {};
  w.document.execCommand = function (cmd, _ui, val) { if (w.__target) w.__target.textContent = val; return !!w.__target; };
  w.eval = w.eval;
  const reports = [];
  w.AS = { report: (s) => reports.push(JSON.parse(s)) };
  w.eval(lib);
  return { w, reports };
}
const sub = (s, v) => s.replace(/\{(\w+)\}/g, (m, k) => (v[k] !== undefined ? v[k] : m));
const vars = { text: 'عنوان قوي\n\nhttps://b.example/p/1.html', hook: 'عنوان قوي', text_enc: 'x', url_enc: 'y' };

async function run(w, recipe, v) {
  w.__as.run(recipe, v);
  for (let i = 0; i < 400; i++) { await new Promise((r) => setTimeout(r, 50)); const done = w.__reports?.length; if (done) break; }
}
async function runAndWait(p, recipe, v = vars) {
  p.w.__as.run(recipe, v);
  for (let i = 0; i < 600 && !p.reports.some((r) => r.s === 'ok' || r.s === 'fail'); i++) await new Promise((r) => setTimeout(r, 50));
  return p.reports;
}
const fast = (r) => ({ ...r, steps: r.steps.map((s) => ({ ...s, ms: 1500 })) });

test('كل وصفة تحتوي رابطاً وخطوات ونقطة نهاية نهائية', () => {
  for (const [id, pl] of Object.entries(recipes.platforms)) {
    assert.ok(pl.variants.length >= 1, id);
    for (const v of pl.variants) {
      assert.match(v.url, /^https:\/\//, id);
      assert.ok(v.steps.some((s) => s.op === 'click' && s.final), id + ' بلا نقرة نهائية');
      assert.ok(v.login && (v.login.urlRegex || v.login.selectors), id + ' بلا كشف دخول');
      for (const s of v.steps) assert.ok(['type', 'click', 'sleep', 'waitGone', 'attach'].includes(s.op), id + ' op');
    }
  }
});

test('X: يكتب في المحرر الفارغ وينقر زر النشر المفعّل', async () => {
  const p = page('<div role="textbox" data-testid="tweetTextarea_0" contenteditable="true"></div><button data-testid="tweetButton">Post</button>', 'https://x.com/intent/post');
  let clicked = 0; p.w.document.querySelector('button').addEventListener('click', () => clicked++);
  p.w.__target = p.w.document.querySelector('[role=textbox]');
  const r = await runAndWait(p, fast(recipes.platforms.x.variants[0]));
  assert.equal(r.at(-1).s, 'ok'); assert.ok(r.some((x) => x.s === 'clicked_final')); assert.equal(clicked, 1);
  assert.match(p.w.document.querySelector('[role=textbox]').textContent, /عنوان قوي/);
});

test('X: لا يعيد الكتابة إذا كان المحرر معبّأ مسبقاً (intent)', async () => {
  const p = page('<div role="textbox" data-testid="tweetTextarea_0" contenteditable="true">نص جاهز</div><button data-testid="tweetButton">Post</button>', 'https://x.com/intent/post');
  const r = await runAndWait(p, fast(recipes.platforms.x.variants[0]));
  assert.equal(r.at(-1).s, 'ok'); assert.equal(p.w.document.querySelector('[role=textbox]').textContent, 'نص جاهز');
});

test('الزر المعطّل لا يُنقر ويفشل بسبب ui_changed مع تشخيص', async () => {
  const p = page('<div role="textbox" data-testid="tweetTextarea_0" contenteditable="true"></div><button data-testid="tweetButton" disabled>Post</button>', 'https://x.com/intent/post');
  p.w.__target = p.w.document.querySelector('[role=textbox]');
  const r = await runAndWait(p, fast(recipes.platforms.x.variants[0]));
  const last = r.at(-1); assert.equal(last.s, 'fail'); assert.equal(last.code, 'ui_changed');
  assert.ok(JSON.parse(last.diag).buttons.includes('Post'));
});

test('صفحة الدخول تُكتشف قبل أي خطوة', async () => {
  const p = page('<input type="password" name="pass">', 'https://m.facebook.com/login/?next=x');
  const r = await runAndWait(p, fast(recipes.platforms.facebook.variants[0]));
  assert.equal(r.at(-1).code, 'login_required'); assert.ok(!r.some((x) => x.s === 'clicked_final'));
});

test('فيسبوك: نموذج المشاركة بنص عربي في الزر', async () => {
  const p = page('<form><textarea name="xc_message"></textarea><input type="submit" name="share" value="نشر"></form>', 'https://m.facebook.com/sharer.php?u=y');
  let clicked = 0; p.w.document.querySelector('input[type=submit]').addEventListener('click', (e) => { e.preventDefault(); clicked++; });
  const r = await runAndWait(p, fast(recipes.platforms.facebook.variants[0]));
  assert.equal(r.at(-1).s, 'ok'); assert.equal(clicked, 1);
  assert.equal(p.w.document.querySelector('textarea').value, 'عنوان قوي');
});

test('لينكدإن: المحرر والزر بالنص عند غياب الصنف', async () => {
  const p = page('<div class="ql-editor" contenteditable="true"></div><button>Post</button>', 'https://www.linkedin.com/feed/');
  let clicked = 0; p.w.document.querySelector('button').addEventListener('click', () => clicked++);
  p.w.__target = p.w.document.querySelector('.ql-editor');
  const r = await runAndWait(p, fast(recipes.platforms.linkedin.variants[0]));
  assert.equal(r.at(-1).s, 'ok'); assert.equal(clicked, 1);
});

test('ثريدز: لا يطابق أزراراً نصها أطول من 60 حرفاً', async () => {
  const p = page('<div contenteditable="true"></div><button>Post and share this with everyone you know right now please do it</button>', 'https://www.threads.com/intent/post');
  p.w.__target = p.w.document.querySelector('[contenteditable]');
  const r = await runAndWait(p, fast(recipes.platforms.threads.variants[0]));
  assert.equal(r.at(-1).s, 'fail');
});

test('العناصر المخفية لا تُنقر', async () => {
  const p = page('<div role="textbox" data-testid="tweetTextarea_0" contenteditable="true"></div><button data-testid="tweetButton" style="display:none">Post</button>', 'https://x.com/intent/post');
  p.w.__target = p.w.document.querySelector('[role=textbox]');
  const r = await runAndWait(p, fast(recipes.platforms.x.variants[0]));
  assert.equal(r.at(-1).s, 'fail');
});

test('استبدال المتغيرات', () => { assert.equal(sub('a={text_enc}&u={url_enc}', vars), 'a=x&u=y'); });

test('إنستغرام: يرفع الصورة في حقل الملف ثم التالي ثم الوصف ثم مشاركة', async () => {
  const p = page('<input type="file" style="display:none"><button>Next</button><textarea aria-label="Write a caption..."></textarea><button>Share</button>', 'https://www.instagram.com/');
  const inp = p.w.document.querySelector('input[type=file]');
  Object.defineProperty(inp, 'files', { writable: true, value: null });
  p.w.DataTransfer = class { constructor() { this._f = []; this.items = { add: (f) => this._f.push(f) }; } get files() { return this._f; } };
  let shared = 0; p.w.document.querySelectorAll('button')[1].addEventListener('click', () => shared++);
  const v = { ...vars, image_b64: Buffer.from('jpegbytes').toString('base64'), image_name: '7.jpg' };
  const r = await runAndWait(p, fast(recipes.platforms.instagram.variants[0]), v);
  assert.equal(r.at(-1).s, 'ok'); assert.equal(shared, 1);
  assert.equal(inp.files.length, 1); assert.equal(inp.files[0].name, '7.jpg');
  assert.match(p.w.document.querySelector('textarea').value, /عنوان قوي/);
});

test('إنستغرام: بلا صورة يفشل بوضوح ولا ينقر مشاركة', async () => {
  const p = page('<input type="file"><button>Next</button><textarea></textarea><button>Share</button>', 'https://www.instagram.com/');
  Object.defineProperty(p.w.document.querySelector('input'), 'files', { writable: true, value: null });
  const r = await runAndWait(p, fast(recipes.platforms.instagram.variants[0]), vars);
  assert.equal(r.at(-1).s, 'fail'); assert.match(r.at(-1).msg, /صورة/); assert.ok(!r.some((x) => x.s === 'clicked_final'));
});
