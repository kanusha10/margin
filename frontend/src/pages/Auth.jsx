import { useState } from 'react'
import * as api from '../api'
import { Brand } from '../components/Sidebar'
import PasswordInput from '../components/PasswordInput'
import { go } from '../router'

function strength(pw) {
  let score = 0
  if (pw.length >= 8) score++
  if (pw.length >= 12) score++
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) score++
  return pw ? Math.max(1, score) : 0
}
const LEVELS = ['', 'Weak', 'Fair', 'Good', 'Strong']

/** One page for both forms. `mode` is 'login' or 'register' and comes from the route. */
export default function Auth({ mode, onDone }) {
  const signup = mode === 'register'
  const [f, setF] = useState({ email: '', password: '', confirm: '', inviteCode: '' })
  const [show, setShow] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const level = strength(f.password)

  async function submit(e) {
    e.preventDefault()
    if (signup && f.password !== f.confirm) { setError("The two passwords don't match."); return }
    setBusy(true); setError('')
    try {
      const s = signup ? await api.register(f.email, f.password, f.inviteCode) : await api.login(f.email, f.password)
      onDone(s.email)
    } catch (err) { setError(err.message) }
    setBusy(false)
  }

  function switchMode() {
      setError(''); setShow(false); setShowConfirm(false)
    setF({ ...f, password: '', confirm: '' })
    go(signup ? '/login' : '/register')
  }

  return (
    <div className="auth-page">
      <aside className="auth-side">
        <button className="brand-btn" onClick={() => go('/')} aria-label="Margin home"><Brand /></button>
        <div className="auth-quote">
          <p className="auth-lead">Stop guessing how a codebase works. Ask it.</p>
          <div className="code-lines auth-code" aria-hidden="true">
            <div><b>14</b><code>String header = req.getHeader("Authorization");</code></div>
            <div className="hit"><b>15</b><code>if (header != null &amp;&amp; header.startsWith("Bearer ")) {'{'}</code></div>
            <div><b>16</b><code>  jwt.parse(header.substring(7))...</code></div>
          </div>
          <p className="auth-note">Every answer points to the file and line it came from.</p>
        </div>
        <p className="auth-foot">Public GitHub repositories only.</p>
      </aside>

      <main className="auth-main">
        <button className="back" onClick={() => go('/')}>Back to home</button>
        <form onSubmit={submit} className="auth-form">
          <div className="auth-brand"><Brand /></div>
          <h1>{signup ? 'Create your account' : 'Welcome back'}</h1>
          <p className="auth-sub">{signup ? 'Sign up to index a repository and start asking questions.' : 'Sign in to open your repositories.'}</p>

          <label className="field">
            <span>Email</span>
            <input type="email" required autoComplete="email" value={f.email} onChange={set('email')} placeholder="you@example.com" />
          </label>

          <PasswordInput label="Password" value={f.password} onChange={set('password')} show={show} onToggle={() => setShow(!show)}
                         autoComplete={signup ? 'new-password' : 'current-password'} minLength={signup ? 8 : undefined}
                         placeholder={signup ? 'At least 8 characters' : 'Your password'} />

          {signup && (
            <>
              <div className="meter" aria-live="polite">
                <span className={`bars l${level}`}><i /><i /><i /><i /></span>
                <span className="meter-text">{level ? `${LEVELS[level]} password` : 'Use 8 or more characters'}</span>
              </div>
              <PasswordInput label="Confirm password" value={f.confirm} onChange={set('confirm')} show={showConfirm} onToggle={() => setShowConfirm(!showConfirm)}
                             autoComplete="new-password" placeholder="Type it again" />
              <label className="field">
                <span>Invite code <em>(optional)</em></span>
                <input value={f.inviteCode} onChange={set('inviteCode')} placeholder="Only if the owner asked for one" />
              </label>
            </>
          )}

          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="solid wide" disabled={busy}>{busy ? 'One moment' : signup ? 'Create account' : 'Sign in'}</button>

          <p className="switch">
            {signup ? 'Already have an account?' : 'New to Margin?'}{' '}
            <button type="button" className="link" onClick={switchMode}>{signup ? 'Sign in' : 'Create an account'}</button>
          </p>
        </form>
      </main>
    </div>
  )
}
