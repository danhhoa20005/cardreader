import urllib.request
import re

html = urllib.request.urlopen('http://diemdanhhs.com').read().decode('utf-8')
m = re.search(r'src="([^"]+index-[^"]+\.js)"', html)
if m:
    js_url = 'http://diemdanhhs.com' + m.group(1)
    print('JS URL:', js_url)
    js_content = urllib.request.urlopen(js_url).read().decode('utf-8')
    with open('js_bundle.txt', 'w', encoding='utf-8') as f:
        f.write(js_content)
    print('Saved JS bundle, length:', len(js_content))
else:
    print('JS not found in html:', html)
