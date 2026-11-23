import { api } from './api.js';
import { clearDraft, readDraft, saveDraft } from './draft-state.js';
import { renderTheory } from './markdown.js';

let catalog;
let current;
let thinkAt = 0;
let retryReady = false;
let view = 'dashboard';
let bookmarks = new Set();
const $ = id => document.getElementById(id);

function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
}

function addButton(parent, text, handler, secondary = false, extraClass = '') {
  const button = node('button', text, secondary ? 'secondary' : '');
  if (extraClass) button.classList.add(extraClass);
  button.addEventListener('click', handler);
  parent.append(button);
  return button;
}

function showError(error) {
  const result = $('result');
  if (result) {
    result.textContent = error.message || String(error);
    result.className = 'bad';
  } else {
    $('work').replaceChildren(node('p', error.message || String(error), 'bad'));
  }
}

function showResult(text, className) {
  const result = $('result');
  result.textContent = text;
  result.className = className || '';
}

function setSessionStep(step) {
  if (!current) return Promise.resolve();
  return api('/api/session', {
    method: 'POST',
    body: JSON.stringify({id: current.id, step})
  }).catch(() => {});
}

function masteredConcepts() {
  return new Set((catalog.concepts || [])
      .filter(entry => entry.status === 'mastered')
      .map(entry => entry.concept));
}

function missingPrerequisites(item) {
  const masteredItems = new Set(catalog.mastered || []);
  const mastered = masteredConcepts();
  return (item.prerequisites || []).filter(
      prerequisite => !masteredItems.has(prerequisite) && !mastered.has(prerequisite));
}

function renderSession() {
  const target = $('session-content');
  target.replaceChildren();
  target.append(node('p', 'Learn → recall → practice → review', 'muted'));
  const next = catalog.next;
  if (!next || next.kind === 'complete') {
    target.append(node('p', next?.reason || 'No next action is available.', 'muted'));
    return;
  }
  target.append(node('strong', next.title));
  target.append(node('p', next.reason, 'muted'));
  target.append(node('p', `${next.estimatedMinutes} minutes · ${next.step}`, 'muted'));
  addButton(target, `Continue: ${next.title}`, () => openItem(next.item, next.step), false, 'session-primary');
}

function renderPlanner() {
  const planner = $('planner');
  planner.replaceChildren();
  if (catalog.planner.routine) {
    catalog.planner.routine.forEach(step => {
      planner.append(node('div', `${step.label} · ${step.minutes} min`));
    });
  }
  planner.append(node('p', catalog.planner.target));
}

function renderDue() {
  const due = $('due');
  due.replaceChildren();
  if (catalog.due.length) {
    catalog.due.forEach(entry => {
      addButton(due, entry.item, () => openItem(entry.item, 'teach'), false, 'item');
    });
  } else {
    due.append(node('span', 'None today.', 'muted'));
  }
}

function renderModules() {
  const masteredItems = new Set(catalog.mastered || []);
  const modules = $('modules');
  modules.replaceChildren();
  catalog.modules.forEach(module => {
    const items = catalog.items.filter(item => item.module === module.id);
    const gradable = items.filter(item => item.type !== 'lesson');
    const ready = gradable.filter(item => missingPrerequisites(item).length === 0).length;
    modules.append(node('h3', `${module.title} · ${ready}/${gradable.length} ready`));
    items.forEach(item => {
      const missing = missingPrerequisites(item);
      const marker = masteredItems.has(item.id) ? '✓ '
          : missing.length ? 'Preview · ' : 'Next · ';
      const label = `${marker}${item.title}`;
      addButton(modules, label, () => openItem(item.id), false, 'item');
    });
  });
}

function renderSummary() {
  const target = $('summary');
  target.replaceChildren();
  const summary = catalog.summary || {};
  target.append(node('p',
      `${summary.streakDays || 0}-day streak · ${summary.conceptsMastered || 0} concepts mastered`,
      'muted'));
  const due = (catalog.concepts || [])
      .filter(entry => entry.status === 'review')
      .slice(0, 3)
      .map(entry => entry.concept);
  target.append(node('p', due.length
      ? `Review next: ${due.join(', ')}`
      : 'No concept reviews are due.', 'muted'));
}

function renderHome() {
  const summary = catalog.summary || {};
  const backend = catalog.items.filter(entry => entry.track === 'Backend');
  const mastered = new Set(catalog.mastered || []);
  $('work').replaceChildren();
  const hero = node('div', undefined, 'hero');
  const copy = node('div');
  copy.append(node('p', 'YOUR NEXT BEST STEP', 'eyebrow'));
  copy.append(node('h2', 'Build interview confidence, one concept at a time.'));
  copy.append(node('p', 'Start with the recommended session, then use ELI5 and interview mode to make the knowledge yours.', 'objective'));
  hero.append(copy);
  if (catalog.next?.item) addButton(hero, 'Continue learning', () => openItem(catalog.next.item, catalog.next.step));
  $('work').append(hero);
  const metrics = node('div', undefined, 'metric-grid');
  [['Backend concepts', backend.length], ['Mastered', mastered.size], ['Day streak', summary.streakDays || 0]]
    .forEach(([label, value]) => { const card = node('div', undefined, 'metric'); card.append(node('span', label, 'muted'), node('strong', String(value))); metrics.append(card); });
  $('work').append(metrics);
  const section = node('section', undefined, 'learning-card');
  section.append(node('h3', 'Three tiers, one path'));
  ['beginner', 'intermediate', 'experienced'].forEach(tier => {
    const count = backend.filter(entry => entry.tier === tier).length;
    const masteredTier = backend.filter(entry => entry.tier === tier && mastered.has(entry.id)).length;
    const row = node('p', undefined, 'question-card');
    row.append(node('span', tier, `badge ${tier}`), node('span', ` ${masteredTier}/${count} concepts complete`));
    section.append(row);
  });
  $('work').append(section);
  const mock = node('section', undefined, 'learning-card');
  mock.append(node('h3', 'Fast interview check'));
  mock.append(node('p', 'Pick a question and give yourself 60 seconds before revealing the answer.', 'muted'));
  addButton(mock, 'Start mixed mock', () => {
    const candidates = catalog.items.filter(item => item.interview);
    const chosen = candidates[Math.floor(Math.random() * candidates.length)];
    openItem(chosen.id, 'recall');
  });
  $('work').append(mock);
}

function renderLibrary(kind, entries = catalog.items) {
  $('work').replaceChildren();
  $('work').append(node('p', kind === 'eli5' ? 'SIMPLE, ACCURATE EXPLANATIONS' : 'PRACTICE SAYING IT OUT LOUD', 'eyebrow'));
  $('work').append(node('h2', kind === 'eli5' ? 'ELI5 library' : 'Interview questions'));
  $('work').append(node('p', kind === 'eli5'
    ? 'Start simple, then check the precise definition and the limits of the analogy.'
    : 'Use the short answer first. Open the deep answer only after you have tried to explain it.', 'objective'));
  entries.filter(item => item.interview).forEach(item => {
    const card = node('article', undefined, 'question-card');
    card.append(node('span', `${item.track} · ${item.tier}`, `badge ${item.tier}`));
    card.append(node('h3', kind === 'eli5' ? item.title : item.interview.question));
    card.append(node('p', kind === 'eli5' ? item.eli5.analogy : item.interview.shortAnswer));
    const details = node('details');
    details.append(node('summary', kind === 'eli5' ? 'See definition, example, and limits' : 'See deep answer, tips, and follow-ups'));
    details.append(node('p', kind === 'eli5' ? item.eli5.definition : item.interview.deepAnswer));
    details.append(node('p', kind === 'eli5' ? `Example: ${item.eli5.example}` : `Tips: ${item.tips.join(' ')}`, 'muted'));
    details.append(node('p', kind === 'eli5' ? `Limits: ${item.eli5.limits}` : `Follow-ups: ${item.followUps.join(' ')}`, 'muted'));
    card.append(details);
    addButton(card, bookmarks.has(item.id) ? 'Saved' : 'Save', () => toggleBookmark(item.id, card), true);
    addButton(card, 'Practice this', () => openItem(item.id), false);
    $('work').append(card);
  });
}

async function toggleBookmark(id, card) {
  const saved = !bookmarks.has(id);
  await api('/api/bookmarks', {method: 'POST', body: JSON.stringify({item: id, saved})});
  if (saved) bookmarks.add(id); else bookmarks.delete(id);
  renderLibrary(view);
}

async function load(selectId, selectedStep) {
  try {
    catalog = await api('/api/catalog');
    const saved = await api('/api/bookmarks');
    bookmarks = new Set(saved.items || []);
    $('ai').checked = !!catalog.settings.aiDrills;
    renderSession();
    renderPlanner();
    renderDue();
    renderModules();
    renderSummary();

    const fallback = catalog.items.find(item => item.type === 'code-problem') || catalog.items[0];
    const next = selectId || catalog.next?.item || fallback?.id;
    if (view === 'questions' || view === 'eli5') renderLibrary(view);
    else if (view === 'dashboard') renderHome();
    else if (next) openItem(next, selectedStep || catalog.next?.step || 'teach');
  } catch (error) {
    showError(error);
  }
}

function item(id) {
  return catalog.items.find(candidate => candidate.id === id);
}

function appendList(parent, values) {
  const list = node('ol');
  values.forEach(value => list.append(node('li', value)));
  parent.append(list);
}

function addEditor(parent, id, labelText, initialValue) {
  const label = node('label', labelText, 'sr-only');
  label.htmlFor = id;
  parent.append(label);
  const editor = node('textarea');
  editor.id = id;
  editor.setAttribute('aria-label', labelText);
  editor.value = initialValue;
  parent.append(editor);
  return editor;
}

function addDraftControls(parent, editor) {
  const draft = readDraft(current.id);
  if (draft === null || draft === editor.value) return;
  const message = node('p', 'A saved draft is available for this item.', 'muted');
  parent.append(message);
  addButton(parent, 'Restore draft', () => {
    editor.value = draft;
    saveDraft(current.id, draft);
    message.textContent = 'Draft restored.';
  }, true);
  addButton(parent, 'Clear draft', () => {
    clearDraft(current.id);
    message.textContent = 'Saved draft cleared.';
  }, true);
}

function openItem(id, sessionStep = 'teach') {
  current = item(id);
  if (!current) return;
  thinkAt = 0;
  retryReady = false;
  void setSessionStep(sessionStep);
  const work = $('work');
  work.replaceChildren();
  const code = current.type === 'code-problem';
  const quiz = current.type === 'quiz' || current.type === 'drill';
  const missing = missingPrerequisites(current);

  work.append(node('p', `${current.module} · ${current.type}`, 'muted'));
  work.append(node('h2', current.title));
  if (current.objective) work.append(node('p', current.objective, 'objective'));
  if (missing.length) {
    work.append(node('p',
        `Preview mode: complete ${missing.join(', ')} before relying on this item.`,
        'muted'));
  }
  work.append(node('p', current.statement || current.body || current.prompt || ''));

  if (current.workedExample) {
    const example = node('details', undefined, 'learning-card');
    example.open = true;
    example.append(node('summary', 'Worked example'));
    appendList(example, current.workedExample);
    work.append(example);
  }
  if (current.recallPrompt) {
    const recall = node('details', undefined, 'learning-card');
    recall.open = true;
    recall.append(node('summary', 'Say it back'));
    recall.append(node('p', current.recallPrompt));
    addButton(recall, 'I can explain this — continue', () => setSessionStep('practice'));
    work.append(recall);
  }

  const theory = node('details', undefined, 'theory');
  theory.append(node('summary', 'Learn first (optional)'));
  const theoryBody = node('div', undefined, 'theory-body');
  renderTheory(current.theoryText || '', theoryBody);
  theory.append(theoryBody);
  work.append(theory);

  if (current.constraints) work.append(node('p', current.constraints.join(' '), 'muted'));
  if (current.complexity) {
    work.append(node('p', `Target: ${current.complexity.time}, ${current.complexity.space}`, 'muted'));
  }
  if (current.think) {
    work.append(node('h3', 'Think'));
    const think = node('p', current.think[0]);
    think.id = 'think';
    work.append(think);
    addButton(work, 'Next step', () => {
      nextThink();
      void setSessionStep('practice');
    });
  }
  if (code && current.solutionC) {
    const reference = node('details', undefined, 'reference-code');
    reference.append(node('summary', 'C solution (unlocked)'));
    reference.append(node('pre', current.solutionC));
    work.append(reference);
  }
  if (code) {
    addButton(work, 'Hint', () => {
      void setSessionStep('practice');
      void hint();
    }, true);
    const hintMessage = node('p', '', 'muted');
    hintMessage.id = 'hint';
    work.append(hintMessage);
    const starter = current.starter || '';
    const editor = addEditor(work, 'code', 'Code solution', starter);
    addDraftControls(work, editor);
    editor.addEventListener('input', () => saveDraft(current.id, editor.value));
    addButton(work, 'Run hidden tests', runCode);
  }
  if (quiz) {
    (current.choices || []).forEach(choice =>
      addButton(work, choice, () => answer(choice), true));
    work.append(node('p', 'Or type the exact answer:'));
    const editor = addEditor(work, 'answer', 'Answer', '');
    addButton(work, 'Check', () => answer(editor.value));
  }
  if (current.type === 'explain') {
    work.append(node('p', current.rubric.join(' · ')));
    addButton(work, 'I covered all four', () => score(5));
    addButton(work, 'I missed one', () => score(3), true);
    addButton(work, 'I could not explain it', () => score(1), true);
  }

  const result = node('pre');
  result.id = 'result';
  result.setAttribute('role', 'status');
  result.setAttribute('aria-live', 'polite');
  work.append(result);
  work.append(node('h3', 'What went wrong?'));
  ['misread', 'wrong pattern', 'edge case', 'bug', 'blanked']
      .forEach(type => addButton(work, type, () => mistake(type), true));
  const status = node('p');
  status.id = 'status';
  status.setAttribute('role', 'status');
  status.setAttribute('aria-live', 'polite');
  work.append(status);
  work.querySelector('textarea, button')?.focus();
}

function nextThink() {
  thinkAt = Math.min(thinkAt + 1, current.think.length - 1);
  $('think').textContent = current.think[thinkAt];
}

async function hint() {
  try {
    const body = await api('/api/hint', {
      method: 'POST',
      body: JSON.stringify({id: current.id})
    });
    const hintMessage = node('p', `Hint ${body.level}/${body.total}: ${body.hint}`, 'muted');
    $('hint')?.replaceWith(hintMessage);
    hintMessage.id = 'hint';
  } catch (error) {
    showError(error);
  }
}

function testResultText(body) {
  const lines = (body.tests || []).map(test => {
    const line = `${test.passed ? 'PASS' : 'FAIL'} ${test.name}`;
    if (test.passed || !test.feedback) return line;
    return `${line}\n  Concept: ${test.concept || 'general'}\n  Edge case: ${test.edgeCase || 'boundary'}\n  Next: ${test.feedback}`;
  });
  return body.error || lines.join('\n');
}

async function runCode() {
  const editor = $('code');
  const source = editor.value;
  showResult('Running…');
  await setSessionStep('practice');
  try {
    const body = await api('/api/judge', {
      method: 'POST',
      body: JSON.stringify({id: current.id, source})
    });
    const text = testResultText(body);
    if (!body.ok) {
      showResult(`${text}\n\n${body.nextStep || 'Review the feedback and retry.'}`, 'bad');
      await setSessionStep('feedback');
      return;
    }
    clearDraft(current.id);
    const id = current.id;
    if (body.solutionC) item(id).solutionC = body.solutionC;
    await load(id, 'review');
    showResult(`${text}\n\n${body.nextStep || 'Explain the invariant before moving on.'}`, 'ok');
  } catch (error) {
    showError(error);
    await setSessionStep('feedback');
  }
}

async function answer(value) {
  await setSessionStep('recall');
  try {
    const body = await api('/api/check', {
      method: 'POST',
      body: JSON.stringify({id: current.id, answer: value, retry: retryReady})
    });
    let text = (body.correct ? 'Correct. ' : 'Not yet. ') + body.explanation;
    if (body.remediation) text += `\n\nRevisit: ${body.remediation}`;
    if (body.retryPrompt) text += `\nRetry: ${body.retryPrompt}`;
    showResult(text, body.correct ? 'ok' : 'bad');
    retryReady = !body.correct;
    await setSessionStep(body.correct ? 'review' : 'feedback');
  } catch (error) {
    showError(error);
  }
}

async function score(quality) {
  try {
    await api('/api/review', {
      method: 'POST',
      body: JSON.stringify({id: current.id, quality})
    });
    await setSessionStep('complete');
    await load();
  } catch (error) {
    showError(error);
  }
}

async function mistake(type) {
  try {
    const body = await api('/api/mistake', {
      method: 'POST',
      body: JSON.stringify({id: current.id, mistake: type})
    });
    $('status').textContent = 'Logged. Weak spots: ' +
        (body.weakSpots || []).map(entry => `${entry.mistake} (${entry.count})`).join(', ');
  } catch (error) {
    showError(error);
  }
}

document.addEventListener('keydown', event => {
  if (event.ctrlKey && event.key === 'Enter' && document.activeElement?.id === 'code') {
    event.preventDefault();
    void runCode();
  }
});

$('ai').addEventListener('change', async event => {
  try {
    await api('/api/settings', {
      method: 'POST',
      body: JSON.stringify({aiDrills: event.target.checked})
    });
    await load();
  } catch (error) {
    showError(error);
  }
});

document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => {
  view = button.dataset.view;
  document.querySelectorAll('[data-view]').forEach(candidate => candidate.classList.toggle('active', candidate === button));
  if (view === 'dashboard') renderHome();
  if (view === 'questions' || view === 'eli5') renderLibrary(view);
  if (view === 'progress') renderSummary();
}));

$('search').addEventListener('input', event => {
  const term = event.target.value.trim().toLowerCase();
  if (!term) return view === 'questions' || view === 'eli5' ? renderLibrary(view) : undefined;
  const results = catalog.items.filter(item => item.title.toLowerCase().includes(term)
      || item.track.toLowerCase().includes(term)
      || JSON.stringify(item.interview || {}).toLowerCase().includes(term)
      || JSON.stringify(item.eli5 || {}).toLowerCase().includes(term));
  renderLibrary(view === 'eli5' ? 'eli5' : 'questions', results);
});

load();
