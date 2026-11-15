const KEY = 'margin.session'

export const getSession = () => { try { return JSON.parse(sessionStorage.getItem(KEY)) } catch { return null } }
export const logout = () => { sessionStorage.removeItem(KEY); localStorage.removeItem(KEY) }
let authLost = () => {}
export const onAuthLost = (fn) => { authLost = fn }

function headers() {
  const s = getSession()
  return { 'Content-Type': 'application/json', ...(s ? { Authorization: `Bearer ${s.token}` } : {}) }
}

async function json(res) {
  if (res.ok) return res.status === 204 ? null : res.json().catch(() => null)
  let msg = `Request failed (${res.status})`
  try { msg = (await res.json()).message || msg } catch { /* no body */ }
  if (res.status === 401 && getSession()) {
    logout(); authLost()
    msg = 'Your session expired. Sign in again.'
  }
  throw new Error(msg)
}

async function authenticate(path, body) {
  const session = await fetch(`/api/auth/${path}`, { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then(json)
  sessionStorage.setItem(KEY, JSON.stringify(session))
  return session
}
export const login = (email, password) => authenticate('login', { email, password })
export const register = (email, password, inviteCode) => authenticate('register', { email, password, inviteCode })

export const listRepos = () => fetch('/api/repos', { headers: headers() }).then(json)
export const addRepo = (url) => fetch('/api/repos', { method: 'POST', headers: headers(), body: JSON.stringify({ url }) }).then(json)
export const removeRepo = (id) => fetch(`/api/repos/${id}`, { method: 'DELETE', headers: headers() }).then(json)

/** POSTs a question and reads the server-sent events: step, answer, error. */
export async function ask(repoId, question, history, onEvent) {
  const res = await fetch(`/api/repos/${repoId}/chat`, {
    method: 'POST', headers: headers(), body: JSON.stringify({ question, history }),
  })
  if (!res.ok) await json(res)
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const frames = buf.split(/\r?\n\r?\n/)
    buf = frames.pop()
    for (const frame of frames) {
      let event = 'message', data = ''
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) data += line.slice(5).trim()
      }
      if (data) onEvent(event, JSON.parse(data))
    }
  }
}
