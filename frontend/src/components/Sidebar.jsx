import { useEffect, useState } from 'react'

export function AddRepo({ onAdd, large, value, onValue }) {
  // Works on its own, or controlled by a parent (the dashboard fills it when an example is clicked).
  const [own, setOwn] = useState('')
  const url = value ?? own
  const setUrl = onValue ?? setOwn
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (!url.trim() || busy) return
    setBusy(true); setError('')
    try { await onAdd(url.trim()); setUrl('') }
    catch (err) { setError(err.message) }
    setBusy(false)
  }

  return (
    <form className={`add ${large ? 'add-large' : ''}`} onSubmit={submit}>
      <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://github.com/owner/repo"
             aria-label="GitHub repository URL" spellCheck="false" />
      <button disabled={busy || !url.trim()}>{busy ? 'Adding' : 'Index repo'}</button>
      {error && <p className="form-error">{error}</p>}
    </form>
  )
}

export function Brand() {
  return (
    <div className="brand">
      <svg width="26" height="26" viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill="#26334F" />
        <rect x="7" y="9" width="12" height="3" rx="1" fill="#52617F" />
        <rect x="7" y="14.5" width="18" height="5" rx="1" fill="#F5C451" />
        <rect x="7" y="22" width="9" height="3" rx="1" fill="#52617F" />
      </svg>
      <span>Margin</span>
    </div>
  )
}

function Status({ repo }) {
  if (repo.status === 'READY') return <span className="meta">{repo.fileCount} files, {repo.chunkCount} chunks</span>
  if (repo.status === 'FAILED') return <span className="meta bad">Indexing failed</span>
  return <span className="meta busy">{repo.message}</span>
}

export default function Sidebar({ repos, activeId, user, name, onLogout, onHome, onNew, onSelect, onRemove }) {
  const [menu, setMenu] = useState(false)
  useEffect(() => {
    if (!menu) return
    const close = () => setMenu(false)
    window.addEventListener('click', close)
    return () => window.removeEventListener('click', close)
  }, [menu])
  return (
    <aside className="sidebar">
      <button className="brand-btn" onClick={onHome} aria-label="Margin dashboard"><Brand /></button>

      <button className="solid new-btn" onClick={onNew}>
        <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M7 2v10M2 7h10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
        New repository
      </button>


      {repos.length > 0 && <p className="side-label">Repositories</p>}
      <ul className="repo-list">
        {repos.map((r) => (
          <li key={r.id} className={r.id === activeId ? 'active' : ''}>
            <button className="repo" onClick={() => onSelect(r.id)}>
              <span className={`dot ${r.status.toLowerCase()}`} />
              <span className="repo-text">
                <span className="repo-name">{r.name}</span>
                <Status repo={r} />
              </span>
            </button>
            <button className="del" onClick={() => onRemove(r.id)} aria-label={`Remove ${r.name}`} title="Remove">
              <svg width="14" height="14" viewBox="0 0 14 14"><path d="M3 3l8 8M11 3l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
            </button>
          </li>
        ))}
      </ul>
      {repos.length === 0 && <p className="side-empty">Repositories you add will show up here.</p>}

      <div className="account">
        {menu && (
          <div className="menu" role="menu">
            <p className="menu-email" title={user}>{user}</p>
            <button role="menuitem" onClick={onLogout}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M6 2.5H3.5a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1H6M10.5 11l3-3-3-3M13.5 8H6.5" /></svg>
              Sign out
            </button>
          </div>
        )}
        <button className="account-btn" onClick={(e) => { e.stopPropagation(); setMenu(!menu) }} aria-haspopup="menu" aria-expanded={menu}>
          <span className="avatar" aria-hidden="true">{name[0]}</span>
          <span className="meta">{name}</span>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3.5 8.5L7 5l3.5 3.5" /></svg>
        </button>
      </div>
    </aside>
  )
}