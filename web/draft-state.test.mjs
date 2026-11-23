import assert from 'node:assert/strict';
import test from 'node:test';

import {clearDraft, readDraft, saveDraft} from './draft-state.js';

function storage(values = new Map()) {
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    }
  };
}

test('drafts round-trip by item and can be cleared', () => {
  const previous = globalThis.localStorage;
  globalThis.localStorage = storage();
  try {
    saveDraft('int-stack', 'class IntStack {}');
    assert.equal(readDraft('int-stack'), 'class IntStack {}');
    assert.equal(readDraft('other-item'), null);

    clearDraft('int-stack');
    assert.equal(readDraft('int-stack'), null);
  } finally {
    globalThis.localStorage = previous;
  }
});

test('storage failures do not interrupt practice', () => {
  const previous = globalThis.localStorage;
  globalThis.localStorage = {
    getItem() { throw new Error('storage unavailable'); },
    setItem() { throw new Error('storage unavailable'); },
    removeItem() { throw new Error('storage unavailable'); }
  };
  try {
    assert.doesNotThrow(() => saveDraft('int-stack', 'draft'));
    assert.doesNotThrow(() => readDraft('int-stack'));
    assert.doesNotThrow(() => clearDraft('int-stack'));
  } finally {
    globalThis.localStorage = previous;
  }
});
