import io, sys

# Reverse double-encoding: real utf-8 bytes were wrongly decoded as gbk and
# re-saved as utf-8. Reverse = encode source as gb18030 -> decode utf-8.
# Source '?' is a literal byte left by the bad transcode; keep it as '?' and
# decode the runs between '?'s. If a `'` was swallowed at a '?' boundary, the
# line has an odd quote count -> re-insert one.

def _dec(run):
    if not run:
        return ''
    try:
        return run.encode('gb18030').decode('utf-8')
    except Exception:
        return run.encode('gb18030', 'replace').decode('utf-8', 'replace')


def fix_line(line):
    out = []
    buf = ''
    for ch in line:
        if ch == '?':
            out.append(_dec(buf)); buf = ''
            out.append('?')
        else:
            buf += ch
    out.append(_dec(buf))
    res = ''.join(out)
    # A lost closing quote manifests as an odd apostrophe count. The lost quote
    # immediately followed a '?' (the '?' is the last surviving byte of the
    # string's final char). Re-insert the quote right after that '?'.
    if res.count("'") % 2 == 1 and '?' in res:
        idx = res.rfind('?')
        res = res[:idx + 1] + "'" + res[idx + 1:]
    elif res.count("'") % 2 == 1:
        res = res + "'"
    return res


def recover(text):
    return '\n'.join(fix_line(l) for l in text.split('\n'))


MARKERS = '瀵矤鐢锛鐨閰閫鏂璇濡閽鍔绔鏌'


def is_mojibake(text):
    return any(c in text for c in MARKERS)


def main(paths):
    for p in paths:
        text = io.open(p, 'rb').read().decode('utf-8')
        if not is_mojibake(text):
            print('clean:', p); continue
        io.open(p, 'w', encoding='utf-8', newline='').write(recover(text))
        print('fixed:', p)


if __name__ == '__main__':
    main(sys.argv[1:])
