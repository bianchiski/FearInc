function node(tag, text) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  return element;
}

function appendInline(parent, text) {
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0;
  for (const match of text.matchAll(pattern)) {
    if (match.index > last) parent.append(document.createTextNode(text.slice(last, match.index)));
    const value = match[0];
    const child = value.startsWith('**')
      ? node('strong', value.slice(2, -2))
      : node('code', value.slice(1, -1));
    parent.append(child);
    last = match.index + value.length;
  }
  if (last < text.length) parent.append(document.createTextNode(text.slice(last)));
}

export function renderTheory(markdown, target) {
  const lines = markdown.split(/\r?\n/);
  let paragraph = [];
  let list = null;
  let codeLines = null;

  const flushParagraph = () => {
    if (!paragraph.length) return;
    const element = node('p');
    paragraph.forEach((line, index) => {
      appendInline(element, line);
      if (index < paragraph.length - 1) element.append(node('br'));
    });
    target.append(element);
    paragraph = [];
  };
  const flushList = () => {
    if (list) target.append(list);
    list = null;
  };
  const flushCode = () => {
    target.append(node('pre', codeLines.join('\n')));
    codeLines = null;
  };

  for (const line of lines) {
    if (codeLines) {
      if (line.startsWith('```')) flushCode();
      else codeLines.push(line);
      continue;
    }
    if (line.startsWith('```')) {
      flushParagraph();
      flushList();
      codeLines = [];
      continue;
    }
    if (!line.trim()) {
      flushParagraph();
      flushList();
      continue;
    }
    const heading = line.match(/^(#{1,3}) (.*)$/);
    if (heading) {
      flushParagraph();
      flushList();
      target.append(node(`h${heading[1].length}`, heading[2]));
      continue;
    }
    const bullet = line.match(/^- (.*)$/);
    if (bullet) {
      flushParagraph();
      if (!list) list = node('ul');
      const item = node('li');
      appendInline(item, bullet[1]);
      list.append(item);
      continue;
    }
    flushList();
    paragraph.push(line);
  }
  if (codeLines) flushCode();
  flushParagraph();
  flushList();
}
