#!/usr/bin/env python3
"""Local-only browser regression; never opens or mutates a live application."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import threading

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


def run():
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(HERE)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f'http://127.0.0.1:{server.server_port}/'
    errors = []
    data = json.loads((HERE / 'catalog.json').read_text())
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(url)
        page.locator('.hero').wait_for()
        assert page.locator('html').get_attribute('dir') == 'rtl'
        page.locator('.skip').focus()
        page.keyboard.press('Enter')
        assert page.locator('.hero').is_visible()
        page.goto(url + '#/journeys')
        assert page.locator('section.panel').count() == 9
        page.goto(url + '#/cases')
        page.locator('#search').fill('CFG-001')
        assert page.locator('#cards .card').count() == 1
        page.locator('#search').fill('')
        page.locator('#readiness').select_option('draft')
        page.locator('#result').select_option('NOT_RUN')
        page.reload()
        page.locator('#cards').wait_for()
        assert page.locator('#readiness').input_value() == 'draft'
        assert page.locator('#result').input_value() == 'NOT_RUN'
        for case in data['cases']:
            page.goto(url + '#/case/' + case['id'])
            page.locator('#execute').wait_for()
            assert page.locator('.step').count() == len(case['steps']), case['id']
            assert page.locator('.visible-step').count() == len(case['flow'])+1
            assert page.locator('.visible-step .vs-body').evaluate_all('(nodes)=>nodes.every(n=>n.checkVisibility())')
            assert page.locator('.step .url').count() == len(case['steps'])
            assert page.locator('#record').count() == 1
            assert page.locator('#expected').count() == 1
            # Every in-page link resolves to a rendered target.
            for target in page.locator('[data-scroll]').evaluate_all('(nodes)=>nodes.map(n=>n.dataset.scroll)'):
                assert page.locator('#' + target).count() == 1
        page.goto(url + '#/case/CFG-002')
        page.locator('[data-secret]').first.fill('synthetic-private-old')
        page.locator('[data-secret]').first.fill('synthetic-private-new')
        page.locator('[data-note="reason"]').fill('synthetic-private-old synthetic-private-new')
        page.locator('[data-note="evidence"]').fill('synthetic evidence')
        page.locator('[data-status="PASS"]').click()
        assert page.evaluate("JSON.parse(localStorage.getItem('matbakhy-testing-v1')||'{}')['CFG-002']?.status||'NOT_RUN'") == 'NOT_RUN'
        page.locator('[data-check="step:1"]').check()
        page.locator('[data-status="FAIL"]').click()
        assert 'FAIL' in page.locator('#record-message').inner_text()
        page.locator('[data-status="BLOCKED"]').click()
        stored = page.evaluate("localStorage.getItem('matbakhy-testing-v1')")
        assert 'synthetic-private' not in stored
        page.locator('[data-export]').click()
        preview = page.locator('#export-review pre').inner_text()
        assert 'synthetic-private' not in preview
        assert '[REDACTED]' in preview
        with page.expect_download() as download:
            page.locator('#confirm-export').click()
        assert download.value.suggested_filename.endswith('.json')
        page.reload()
        page.locator('#execute').wait_for()
        assert page.locator('[data-check="step:1"]').is_checked()
        assert page.locator('[data-secret]').first.input_value() == ''
        assert page.locator('[data-note="evidence"]').input_value() == ''
        page.goto(url + '#/case/E2E-ORDER-101')
        page.locator('#execute').wait_for()
        assert page.locator('[data-canonical="2"] .url').get_attribute('href').endswith('#/checkout')
        page.locator('[data-check="setup:preview"]').check()
        page.reload()
        page.locator('#execute').wait_for()
        assert page.locator('[data-check="setup:preview"]').is_checked()
        # Exercise website status validation with synthetic local-only input; this is not a live order run.
        page.locator('[data-status="PASS"]').click()
        assert page.evaluate("JSON.parse(localStorage.getItem('matbakhy-testing-v1'))['E2E-ORDER-101'].status") != 'PASS'
        for box in page.locator('[data-check]').all():
            box.check()
        page.locator('#run-release').fill('a'*40)
        page.locator('#run-env').fill('LOCAL-UI-REGRESSION-ONLY')
        page.locator('[data-note="evidence"]').fill('Synthetic UI validator regression only; no live product execution.')
        page.locator('[data-status="PASS"]').click()
        assert page.evaluate("JSON.parse(localStorage.getItem('matbakhy-testing-v1'))['E2E-ORDER-101'].status") == 'PASS'
        page.locator('[data-check="step:1"]').uncheck()
        assert page.evaluate("JSON.parse(localStorage.getItem('matbakhy-testing-v1'))['E2E-ORDER-101'].status") == 'BLOCKED'
        page.goto(url + '#/case/%')
        page.get_by_role('heading', name='الحالة مش موجودة').wait_for()
        for width in (1440, 390, 320):
            page.set_viewport_size({'width': width, 'height': 844})
            for route in ('#/', '#/cases', '#/case/CFG-001', '#/case/E2E-ORDER-101', '#/results'):
                page.goto(url + route)
                page.wait_for_function('document.querySelector("main h1") !== null')
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), (width, route)
        page.goto(url + '#/')
        page.screenshot(path='/tmp/matbakhy-testing-mobile.png', full_page=True)
        assert not errors, errors
        browser.close()
    server.shutdown()
    print('PASS: 200 scenario routes; J0–J8; search/filter resume; status/prerequisite guard; secret history/export; 320/390/1440px RTL; malformed routes; no browser errors. Product acceptance NOT_RUN.')


if __name__ == '__main__':
    run()
