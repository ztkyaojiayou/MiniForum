# -*- coding: utf-8 -*-
import io
p = 'src/main/resources/static/login.html'
s = io.open(p, encoding='utf-8').read()
s = s.replace('<h1>\U0001F510 \u7528\u6237\u7ba1\u7406\u7cfb\u7edf</h1>', '<h1>\u6b22\u8fce\u56de\u6765 \U0001F44B</h1>')
s = s.replace('<div class="subtitle">\u8bf7\u767b\u5f55\u540e\u7ee7\u7eed\u64cd\u4f5c</div>', '<div class="subtitle">\u767b\u5f55\u300c\u52a8\u6001\u5e7f\u573a\u300d\uff0c\u5206\u4eab\u4f60\u7684\u65b0\u9c9c\u4e8b</div>')
io.open(p, 'w', encoding='utf-8').write(s)
print('welcome:', '\u6b22\u8fce\u56de\u6765' in s)
print('manage:', '\u7528\u6237\u7ba1\u7406\u7cfb\u7edf' in s)
