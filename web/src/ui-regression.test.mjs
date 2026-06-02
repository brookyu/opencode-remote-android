import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const app = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8')
const icons = readFileSync(new URL('./Icons.tsx', import.meta.url), 'utf8')

const refreshButton = app.match(/<button onClick=\{refreshSessionsWithIndicator\}[\s\S]*?\{t\('sessions\.refresh'\)\}[\s\S]*?<\/button>/)
assert.ok(refreshButton, 'sessions refresh button should call refreshSessionsWithIndicator')
assert.ok(refreshButton[0].includes('RefreshIcon'), 'idle sessions refresh button should render a non-spinning RefreshIcon')
assert.ok(refreshButton[0].includes('refreshingSessions ? <LoadingIcon'), 'refresh button should spin only during an active manual refresh')

assert.ok(app.includes('messageScrollSignature'), 'conversation auto-scroll should react to message content changes, not only message count')
assert.ok(app.includes('onScroll={handleMessagesScroll}'), 'messages pane should track whether the user is already near the bottom')
assert.ok(app.includes("t('detail.jumpToLatest')"), 'detail view should expose a jump-to-latest button when scrolled away from output')

assert.match(icons, /export const RefreshIcon/, 'RefreshIcon should exist for idle refresh UI')

console.log('ui regression tests passed')
