function storage() {
  try {
    return globalThis.localStorage;
  } catch (_) {
    return null;
  }
}

function key(id) {
  return `trainer:draft:${id}`;
}

export function saveDraft(id, value) {
  try {
    storage()?.setItem(key(id), value);
  } catch (_) {
    // Drafts are an enhancement; private browsing must not block practice.
  }
}

export function readDraft(id) {
  try {
    return storage()?.getItem(key(id)) ?? null;
  } catch (_) {
    return null;
  }
}

export function clearDraft(id) {
  try {
    storage()?.removeItem(key(id));
  } catch (_) {
    // Ignore storage restrictions.
  }
}
