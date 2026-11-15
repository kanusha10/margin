import { useState } from 'react'

const EXAMPLES = ['spring-projects/spring-petclinic', 'expressjs/express']

const TILES = [
  ['Ask in plain English', 'No need to know where things live. Describe what you want to understand.',
    <path key="a" d="M4 5h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H9l-4 3v-3H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z" />],
  ['Watch it work', 'Every search and every file it opens is shown as a step while it thinks.',
    <g key="b"><path d="M4 6h12M4 10h12M4 14h7" /></g>],
  ['Check the sources', 'Answers cite the files and lines they came from, so you can verify them.',
    <g key="c"><path d="M6 3h6l4 4v10H6z" /><path d="M12 3v4h4" /></g>],
]

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

export default function Dashboard({ repos, name, onAdd }) {
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    if (!url.trim() || busy) return
    setBusy(true); setError('')
    try { await onAdd(url.trim()); setUrl('') }
    catch (err) { setError(err.message) }
    setBusy(false)
  }

  return (
    <div className="dash-page">
      <div className="dash hello">
        <span className="hello-mark" aria-hidden="true">
          <svg width="52" height="52" viewBox="0 0 32 32">
            <rect width="32" height="32" rx="9" fill="#26334F" />
            <rect x="7" y="9" width="12" height="3" rx="1" fill="#52617F" />
            <rect x="7" y="14.5" width="18" height="5" rx="1" fill="#F5C451" />
            <rect x="7" y="22" width="9" height="3" rx="1" fill="#52617F" />
          </svg>
        </span>
        <h1>{greeting()}, {name}</h1>
        <p className="hello-sub">
          {repos.length ? 'What would you like to understand next?' : 'What codebase would you like to understand?'}
        </p>

        <form className="start-card" onSubmit={submit}>
          <div className="start-row">
            <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M8.5 11.5a3.5 3.5 0 0 0 5 0l2.5-2.5a3.5 3.5 0 0 0-5-5L9.8 5.2" /><path d="M11.5 8.5a3.5 3.5 0 0 0-5 0L4 11a3.5 3.5 0 0 0 5 5l1.2-1.2" />
            </svg>
            <input value={url} onChange={(e) => setUrl(e.target.value)} spellCheck="false"
                   placeholder="Paste a GitHub link to start, like https://github.com/owner/repo" aria-label="GitHub repository link" />
            <button className="solid" disabled={busy || !url.trim()}>{busy ? 'Adding' : 'Start'}</button>
          </div>
          {error && <p className="start-error">{error}</p>}
        </form>

        <p className="try center">
          Try:{' '}
          {EXAMPLES.map((x) => (
            <button key={x} type="button" className="chip-btn" onClick={() => setUrl('https://github.com/' + x)}>{x}</button>
          ))}
        </p>

        <ul className="tiles">
          {TILES.map(([title, text, icon]) => (
            <li key={title}>
              <span className="tile-ic">
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{icon}</svg>
              </span>
              <h3>{title}</h3>
              <p>{text}</p>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}