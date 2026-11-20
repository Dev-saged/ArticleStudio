/* محرك وصفات الواجهة: يقرأ وصفة JSON ويطبّق خطواتها داخل صفحة المنصة، ويبلّغ Kotlin عبر AS.report. */
(function () {
  if (window.__as) return;
  var rnd = function (a, b) { return a + Math.random() * (b - a); };
  var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
  var norm = function (s) { return (s || '').replace(/\s+/g, ' ').trim(); };
  var layoutless = function () { var r = document.documentElement.getBoundingClientRect(); return !r || (r.width === 0 && r.height === 0); };
  var visible = function (el) {
    if (!el) return false;
    var cs = window.getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || el.hidden) return false;
    if (layoutless()) return true;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  var enabled = function (el) { return !el.disabled && el.getAttribute('aria-disabled') !== 'true'; };
  var CLICKABLE = 'button,[role=button],a,input[type=submit],input[type=button],[role=link],[role=menuitem]';
  var txt = function (el) { return el.innerText !== undefined ? el.innerText : el.textContent; };
  var label = function (el) { return norm(txt(el) || el.value || el.getAttribute('aria-label') || el.getAttribute('title') || ''); };

  function find(step, pool) {
    var sels = [].concat(step.sel || []);
    for (var i = 0; i < sels.length; i++) {
      var list; try { list = document.querySelectorAll(sels[i]); } catch (e) { list = []; }
      for (var j = 0; j < list.length; j++) if (visible(list[j])) return list[j];
    }
    if (step.text) {
      var re = new RegExp(step.text, 'i');
      var all = document.querySelectorAll(pool || CLICKABLE);
      for (var k = 0; k < all.length; k++) if (visible(all[k]) && re.test(label(all[k])) && label(all[k]).length < 60) return all[k];
    }
    return null;
  }

  async function waitFor(step, ms, needEnabled) {
    var end = Date.now() + (ms || step.ms || 20000);
    while (Date.now() < end) {
      var el = find(step, step.pool);
      if (el && (!needEnabled || enabled(el))) return el;
      await sleep(250);
    }
    return null;
  }

  function setText(el, value) {
    el.focus();
    var tag = el.tagName;
    if (tag === 'TEXTAREA' || tag === 'INPUT') {
      var proto = tag === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return;
    }
    var sel = window.getSelection(); var range = document.createRange();
    range.selectNodeContents(el); sel.removeAllRanges(); sel.addRange(range);
    var ok = false;
    try { el.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: value })); } catch (e) {}
    try { ok = document.execCommand('insertText', false, value); } catch (e) {}
    if (!ok || norm(txt(el)).length === 0) {
      // محررات Draft/Lexical/Quill تقبل اللصق حين ترفض الإدراج المباشر
      try {
        var dt = new DataTransfer(); dt.setData('text/plain', value);
        el.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: dt }));
      } catch (e) {}
    }
    if (norm(txt(el)).length === 0) {
      el.textContent = value;
      el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }));
    }
  }

  function click(el) {
    try { el.scrollIntoView({ block: 'center' }); } catch (e) {}
    ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(function (t) {
      el.dispatchEvent(new MouseEvent(t, { bubbles: true, cancelable: true, view: window }));
    });
    el.click();
  }

  function sub(s, v) {
    return String(s).replace(/\{(\w+)\}/g, function (m, k) { return v[k] !== undefined ? v[k] : m; });
  }

  function diag() {
    var b = [], all = document.querySelectorAll(CLICKABLE);
    for (var i = 0; i < all.length && b.length < 25; i++) if (visible(all[i])) { var l = label(all[i]); if (l) b.push(l.slice(0, 30)); }
    var ed = document.querySelectorAll('textarea,[contenteditable=true],[role=textbox],input[type=text]').length;
    return JSON.stringify({ url: location.href.slice(0, 120), title: (document.title || '').slice(0, 60), buttons: b, editors: ed });
  }

  function loggedOut(login) {
    if (!login) return false;
    if (login.urlRegex && new RegExp(login.urlRegex, 'i').test(location.href)) return true;
    if (login.selectors) for (var i = 0; i < login.selectors.length; i++) { try { if (document.querySelector(login.selectors[i])) return true; } catch (e) {} }
    return false;
  }

  var report = function (o) { try { window.AS.report(JSON.stringify(o)); } catch (e) {} };

  async function run(recipe, vars) {
    try {
      await sleep(rnd(600, 1200));
      if (loggedOut(recipe.login)) return report({ s: 'fail', code: 'login_required', msg: 'الجلسة منتهية', diag: diag() });
      var steps = recipe.steps || [];
      for (var i = 0; i < steps.length; i++) {
        var st = steps[i];
        if (st.op === 'sleep') { await sleep(st.ms || 500); continue; }
        if (st.op === 'waitGone') {
          var end = Date.now() + (st.ms || 15000), gone = false;
          while (Date.now() < end) { if (!find(st)) { gone = true; break; } await sleep(250); }
          if (!gone && !st.optional) return report({ s: 'fail', code: 'ui_changed', msg: 'لم يختفِ العنصر المتوقع ' + (st.name || ''), diag: diag() });
          continue;
        }
        if (st.op === 'attach') {
          var inp = null, until = Date.now() + (st.ms || 20000);
          while (!inp && Date.now() < until) {
            var ss = [].concat(st.sel || ['input[type=file]']);
            for (var q = 0; q < ss.length && !inp; q++) { try { inp = document.querySelector(ss[q]); } catch (e) {} }
            if (!inp) await sleep(250);
          }
          if (!inp) { if (st.optional) continue; return report({ s: 'fail', code: 'ui_changed', msg: 'لم أجد حقل رفع الصورة', diag: diag() }); }
          if (!vars.image_b64) return report({ s: 'fail', code: 'ui_changed', msg: 'لا صورة للمقال', diag: diag() });
          var bin = atob(vars.image_b64), u8 = new Uint8Array(bin.length);
          for (var b = 0; b < bin.length; b++) u8[b] = bin.charCodeAt(b);
          var dt = new DataTransfer(); dt.items.add(new File([u8], vars.image_name || 'cover.jpg', { type: 'image/jpeg' }));
          inp.files = dt.files;
          inp.dispatchEvent(new Event('input', { bubbles: true })); inp.dispatchEvent(new Event('change', { bubbles: true }));
          continue;
        }
        var el = await waitFor(st, st.ms, st.op === 'click');
        if (!el) {
          if (loggedOut(recipe.login)) return report({ s: 'fail', code: 'login_required', msg: 'الجلسة منتهية', diag: diag() });
          if (st.optional) continue;
          return report({ s: 'fail', code: 'ui_changed', msg: 'لم أجد عنصر ' + (st.name || st.op), diag: diag() });
        }
        await sleep(rnd(350, 1100));
        if (st.op === 'type') {
          var cur = norm(el.value !== undefined ? el.value : txt(el));
          if (!(st.ifEmpty && cur.length > 0)) setText(el, sub(st.value, vars));
        } else if (st.op === 'click') {
          if (st.final) report({ s: 'clicked_final' });
          click(el);
        }
      }
      report({ s: 'ok' });
    } catch (e) {
      report({ s: 'fail', code: 'ui_changed', msg: String(e && e.message || e).slice(0, 120), diag: diag() });
    }
  }

  window.__as = { run: run, diag: diag, loggedOut: loggedOut, find: find };
})();
