#!/usr/bin/env python3
"""Cherche les textes affichés qui n'ont pas de version anglaise.

Chaque texte de l'interface doit passer par L.s("français", "english"), L.f(...) ou L.n(...).
Ce script liste les chaînes Java qui ressemblent à du texte et ne sont dans aucun de ces appels
(ni dans un appel à Log, ni dans la liste des exceptions ci-dessous). Code de sortie 1 s'il en trouve.
Usage : python3 tools/check_i18n.py
"""
import glob, os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC = os.path.join(ROOT, 'src', 'fr', 'agenda', 'manuscrit')

# chaînes techniques ou identiques dans les deux langues
ALLOWED = {
    'OK', 'Français', 'English', 'Langue · Language', 'Lasso', 'Emoji', 'Maths', 'EXAM', 'IMPORTANT',
    'DS', 'TP', 'RENDU', 'TEST', 'LAB', 'DUE', '1er', ' et ', ' – ', '  ·  ', ' · ', ' & ', ', ',
    'sans-serif', 'sans-serif-medium', 'agenda', 'agenda-google', 'agenda-scan', 'AgendaCal', 'AgendaStore',
    'AgendaWidget', '#contacts@', '#weather@', ' IN (', 'package:', 'appWidgetSizes', '.ink', '.tmp',
    '%04d.ink', '%04d-%02d-%02d', '%d:%02d', '%dh%02d', 'fr', 'h', ':', 'S', 'W', '+ Emoji',
}
# fichiers ou tableaux déjà bilingues
SKIP_LINE = re.compile(r'(DAY_NAMES|DAY_SHORT|DAY_LETTER|MONTHS|MONTHS_SHORT|STAMP_TEXT)(_EN)?\s*=')
CALLS = ('L.s(', 'L.f(', 'L.n(', 'Log.w(', 'Log.e(', 'Log.d(', 'Log.i(')
STR = re.compile(r'"((?:[^"\\]|\\.)*)"')


def protected_spans(text):
    """Intervalles couverts par un appel L.s(...), L.f(...), L.n(...) ou Log.x(...)."""
    spans = []
    for call in CALLS:
        start = 0
        while True:
            i = text.find(call, start)
            if i < 0:
                break
            j, depth, in_str = i + len(call), 1, False
            while j < len(text) and depth:
                ch = text[j]
                if in_str:
                    if ch == '\\':
                        j += 1
                    elif ch == '"':
                        in_str = False
                elif ch == '"':
                    in_str = True
                elif ch == '(':
                    depth += 1
                elif ch == ')':
                    depth -= 1
                j += 1
            spans.append((i, j))
            start = i + len(call)
    return spans


def looks_like_text(s):
    if s in ALLOWED or not re.search(r'[A-Za-zÀ-ÿ]{2,}', s):
        return False
    if re.fullmatch(r'(\\u[0-9A-Fa-f]{4})+', s):  # emoji écrits en \\uXXXX
        return False
    if re.fullmatch(r'[a-z][A-Za-z0-9_.]*', s):  # clé de préférence, nom technique
        return False
    return True


def main():
    problems = []
    for path in sorted(glob.glob(os.path.join(SRC, '*.java'))):
        text = open(path, encoding='utf-8').read()
        spans = protected_spans(text)
        line_starts = [0] + [m.end() for m in re.finditer('\n', text)]
        for m in STR.finditer(text):
            pos = m.start()
            line_no = next(k for k in range(len(line_starts) - 1, -1, -1) if line_starts[k] <= pos) + 1
            line = text.splitlines()[line_no - 1]
            stripped = line.strip()
            if stripped.startswith(('//', '*', '/*')) or SKIP_LINE.search(line):
                continue
            # suite d'un tableau bilingue de Days (lignes qui commencent par une chaîne)
            if os.path.basename(path) == 'Days.java' and stripped.startswith('"'):
                continue
            # chaîne dans un commentaire de fin de ligne
            col = pos - line_starts[line_no - 1]
            code = re.sub(r'"(?:[^"\\]|\\.)*"', lambda q: 'x' * len(q.group(0)), line)
            cut = code.find('//')
            if 0 <= cut < col:
                continue
            if any(a <= pos < b for a, b in spans):
                continue
            if looks_like_text(m.group(1)):
                problems.append('%s:%d: "%s"' % (os.path.basename(path), line_no, m.group(1)))
    for p in problems:
        print(p)
    print('%d texte(s) sans traduction' % len(problems) if problems else 'Traductions : tout est en deux langues.')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
