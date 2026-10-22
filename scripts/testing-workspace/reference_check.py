#!/usr/bin/env python3
"""Compare supplied prototype geometry/tokens with local ORDER-101; no live requests."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import json
from pathlib import Path
import threading

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
REFERENCE = ROOT / 'docs/quality/testing-workspace/reference/approved-prototype.html'
OUT = ROOT / 'verification/runs/20261022-WORKSPACE-REDESIGN'


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


def metrics(page):
    return page.evaluate('''() => {
      const css=(q,p)=>getComputedStyle(document.querySelector(q))[p];
      return {mainWidth:document.querySelector('.simple').getBoundingClientRect().width,
        background:css('body','backgroundColor'),font:css('body','fontFamily'),
        headerBackground:css('.header','backgroundColor'),headerRadius:css('.header','borderRadius'),
        headerPadding:css('.header','padding'),titleSize:css('.header h1','fontSize'),
        cardRadius:css('.cardx','borderRadius'),cardBackground:css('.cardx','backgroundColor'),
        stepRadius:css('.visible-step','borderRadius'),stepBackground:css('.vs-top','backgroundColor'),
        rtl:document.documentElement.dir};
    }''')


def run():
    OUT.mkdir(parents=True, exist_ok=True)
    shots = OUT / 'screenshots'
    shots.mkdir(exist_ok=True)
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(HERE)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url=f'http://127.0.0.1:{server.server_port}/#/case/E2E-ORDER-101'
    comparisons=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(headless=True)
        page=browser.new_page()
        # The reference bytes are untouched. Both views use the same licensed local font files.
        font_css='''@font-face{font-family:Cairo;src:url(https://fonts.gstatic.com/local-arabic.woff2);font-weight:400 800}
@font-face{font-family:Cairo;src:url(https://fonts.gstatic.com/local-latin.woff2);font-weight:400 800;unicode-range:U+0000-00FF}'''
        page.route('https://fonts.googleapis.com/**',lambda r:r.fulfill(status=200,content_type='text/css',body=font_css))
        page.route('https://fonts.gstatic.com/**',lambda r:r.fulfill(status=200,content_type='font/woff2',body=(HERE/'fonts'/('cairo-latin.woff2' if 'latin' in r.request.url else 'cairo-arabic.woff2')).read_bytes()))
        for width,label in [(1440,'desktop'),(390,'mobile'),(320,'small-mobile')]:
            page.set_viewport_size({'width':width,'height':1000})
            page.goto(REFERENCE.as_uri());page.evaluate('document.fonts.ready')
            expected=metrics(page)
            page.screenshot(path=str(shots/f'reference-{label}.png'))
            page.goto(url);page.locator('#execute').wait_for();page.evaluate('document.fonts.ready')
            actual=metrics(page)
            assert actual==expected,(label,expected,actual)
            assert page.locator('.visible-step .vs-body').evaluate_all('(nodes)=>nodes.every(n=>n.checkVisibility())')
            assert page.locator('.step').count()==10
            assert page.locator('.visible-step').count()==19
            assert page.locator('details').count()==1 # optional source references only
            assert page.locator('details').inner_text().startswith('مراجع المصدر')
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            page.screenshot(path=str(shots/f'after-{label}.png'))
            if label!='small-mobile':
                page.locator('[data-canonical="2"]').screenshot(path=str(shots/f'after-{label}-checkout.png'))
            comparisons.append({'viewport':width,'prototype':expected,'implementation':actual,'tokens_and_geometry':'PASS','canonical_steps':10,'visible_flow_steps':19})
        browser.close()
    server.shutdown()
    result={'reference_sha256':hashlib.sha256(REFERENCE.read_bytes()).hexdigest(),'comparison':'Exact computed visual tokens/geometry; semantic content differs intentionally. No claim of pixel-identical page.','font_control':'Cairo 400–800 identical self-hosted subsets supplied to reference import during offline comparison','comparisons':comparisons,'live_application_verification':'NOT_RUN'}
    (OUT/'visual-comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print('PASS: prototype geometry, Cairo, colors, borders and RTL match at1440/390/320; 19 visible flow steps retain10 canonical steps. Screenshots saved. No live product checks.')


if __name__=='__main__':
    run()
