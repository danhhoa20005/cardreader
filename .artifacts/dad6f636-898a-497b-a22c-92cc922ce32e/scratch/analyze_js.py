import re

with open('js_bundle.txt', 'r', encoding='utf-8') as f:
    content = f.read()

keywords = ['login', 'dangnhap', 'tai_khoan', 'mat_khau', 'username', 'password', 'dangxuat', 'logout', 'menu', 'hamburger']
for kw in keywords:
    matches = [m.start() for m in re.finditer(kw, content, re.IGNORECASE)]
    print(f"Keyword '{kw}': {len(matches)} matches")
    for pos in matches[:3]:
        snippet = content[max(0, pos-100):min(len(content), pos+100)]
        print(f"  Snippet at {pos}: {repr(snippet)}")
