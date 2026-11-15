import { useEffect, useRef, useState } from 'react'
import { AddRepo } from './Sidebar'

const EXAMPLES = ['spring-projects/spring-petclinic', 'expressjs/express', 'pallets/flask']

export default function NewRepoModal({ onAdd, onClose }) {
  const [url, setUrl] = useState('')
  const box = useRef(null)
  const close = useRef(onClose)
  close.current = onClose

  useEffect(() => {
    box.current?.querySelector('input')?.focus()
    const key = (e) => { if (e.key === 'Escape') close.current() }
    window.addEventListener('keydown', key)
    return () => window.removeEventListener('keydown', key)
  }, [])

  return (
    <div className="modal-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="nr-title" ref={box}>
        <div className="modal-head">
          <h2 id="nr-title">New repository</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            <svg width="16" height="16" viewBox="0 0 14 14"><path d="M3 3l8 8M11 3l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
          </button>
        </div>
        <p>Paste the link of a public GitHub repository. Margin will read the code so you can ask questions about it.</p>
        <AddRepo onAdd={onAdd} value={url} onValue={setUrl} />
        <p className="try">
          Try one:{' '}
          {EXAMPLES.map((e) => (
            <button key={e} type="button" className="chip-btn" onClick={() => setUrl('https://github.com/' + e)}>{e}</button>
          ))}
        </p>
      </div>
    </div>
  )
}