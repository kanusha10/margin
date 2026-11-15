import { useEffect, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import * as api from '../api'

const SUGGESTIONS = [
  'How is this project structured?',
  'Where does the application start up?',
  'How is authentication or authorization handled?',
  'Which external services or APIs does it call?',
]

function describe(step) {
  const a = step.args || {}
  switch (step.tool) {
    case 'search_code': return ['Searched for', a.query]
    case 'grep_code': return ['Grepped for', a.text]
    case 'list_files': return ['Listed', a.directory || 'repository root']
    case 'read_file': return ['Opened', a.path + (a.start_line > 1 ? `:${a.start_line}` : '')]
    default: return [step.tool, '']
  }
}

function Trace({ steps, working, notice }) {
  if (!steps.length && !working) return null
  return (
    <ol className="trace" aria-label="What the agent did">
      {steps.map((s, i) => {
        const [verb, target] = describe(s)
        return (
          <li key={i}>
            <span className="verb">{verb}</span> <mark>{target}</mark>
            <span className="result">{s.summary}</span>
          </li>
        )
      })}
      {working && (
        <li className="live">
          {notice
            ? <span className="verb waiting">{notice}</span>
            : <span className="verb">{steps.length ? 'Thinking about what to check next' : 'Thinking'}</span>}
        </li>
      )}
    </ol>
  )
}

function BotMark() {
  return (
    <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="#26334F" />
      <rect x="7" y="9" width="12" height="3" rx="1" fill="#52617F" />
      <rect x="7" y="14.5" width="18" height="5" rx="1" fill="#F5C451" />
      <rect x="7" y="22" width="9" height="3" rx="1" fill="#52617F" />
    </svg>
  )
}

function Entry({ e }) {
  const working = e.status === 'working'
  return (
    <div className="exchange">
      <div className="msg user"><div className="bubble">{e.question}</div></div>
      <div className="msg bot">
        <span className="bot-avatar"><BotMark /></span>
        <div className="bot-body">
          {working
            ? <Trace steps={e.steps} working notice={e.notice} />
            : e.steps.length > 0 && (
              <details className="steps-box">
                <summary>Show {e.steps.length} {e.steps.length === 1 ? 'step' : 'steps'}</summary>
                <Trace steps={e.steps} working={false} />
              </details>
            )}
          {e.answer && <div className="answer"><ReactMarkdown>{e.answer}</ReactMarkdown></div>}
          {e.sources?.length > 0 && (
            <div className="sources" aria-label="Files used">
              {e.sources.map((s) => <code key={s} className="chip">{s}</code>)}
            </div>
          )}
         {e.status === 'failed' && (
           <div className="error"><strong>Couldn't get an answer</strong><span>{e.error}</span></div>
         )}
        </div>
      </div>
    </div>
  )
}
export default function Chat({ repo, entries, setEntries, onRemove }) {
  const [input, setInput] = useState('')
  const bottom = useRef(null)
  const field = useRef(null)
  const busy = entries.length > 0 && entries[entries.length - 1].status === 'working'

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [entries])

  if (repo.status === 'FAILED') {
    return (
      <section className="notice">
        <h1>Indexing didn't finish</h1>
        <p>{repo.message}</p>
        <button className="solid" onClick={() => onRemove(repo.id)}>Remove and start over</button>
      </section>
    )
  }
  if (repo.status !== 'READY') {
    const m = /(\d+) of (\d+)/.exec(repo.message || '')
    const pct = m && Number(m[2]) ? Math.min(100, Math.round((Number(m[1]) / Number(m[2])) * 100)) : null
    return (
      <section className="notice">
        <h1>Reading {repo.name}</h1>
        <p>{repo.message}. Large repos take a few minutes because the free Gemini tier limits how fast code can be embedded. Margin paces its requests and shows any wait here.</p>
        <div className="bar"><i style={pct != null ? { width: pct + '%', margin: 0, animation: 'none' } : undefined} /></div>
        {pct != null && <p className="pct">{pct}% embedded</p>}
      </section>
    )
  }

  async function send(text) {
    const q = text.trim()
    if (!q || busy) return
    const history = entries.filter((e) => e.answer)
      .flatMap((e) => [{ role: 'user', text: e.question }, { role: 'model', text: e.answer }]).slice(-12)
    const id = Date.now()
    setEntries((es) => [...es, { id, question: q, steps: [], notice: '', answer: '', sources: [], status: 'working' }])
    setInput('')
    if (field.current) field.current.style.height = 'auto'
    const patch = (fn) => setEntries((es) => es.map((e) => (e.id === id ? fn(e) : e)))
    try {
      await api.ask(repo.id, q, history, (event, data) => {
        if (event === 'notice') patch((e) => ({ ...e, notice: data.message }))
        if (event === 'step') patch((e) => ({ ...e, notice: '', steps: [...e.steps, data] }))
        if (event === 'answer') patch((e) => ({ ...e, notice: '', answer: data.text, sources: data.sources, status: 'done' }))
        if (event === 'error') patch((e) => ({ ...e, notice: '', status: 'failed', error: data.message }))
      })
      patch((e) => (e.status === 'working' ? { ...e, status: 'failed', error: 'The connection closed before an answer arrived. Try again.' } : e))
    } catch (err) {
      patch((e) => ({ ...e, status: 'failed', error: err.message }))
    }
  }

   return (
     <>
       <header className="chat-head">
         <span className="chat-title">{repo.name}</span>
         <span className="chat-meta">{repo.fileCount} files indexed</span>
       </header>
       <div className="thread">
         {entries.length === 0 && (
           <section className="start">
             <span className="bot-avatar big"><BotMark /></span>
             <h1>Ask anything about {repo.name.split('/')[1]}</h1>
             <p>Margin searches the code and answers with the files it used.</p>
             <div className="sugg">
               {SUGGESTIONS.map((s) => <button key={s} onClick={() => send(s)}>{s}</button>)}
             </div>
           </section>
         )}
         {entries.map((e) => <Entry key={e.id} e={e} />)}
         <div ref={bottom} />
       </div>
       <form className="composer" onSubmit={(ev) => { ev.preventDefault(); send(input) }}>
         <div className="composer-box">
           <textarea ref={field} rows={1} value={input} placeholder="Message Margin"
             onChange={(e) => { setInput(e.target.value); e.target.style.height = 'auto'; e.target.style.height = e.target.scrollHeight + 'px' }}
             onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(input) } }} />
           <button disabled={busy || !input.trim()} aria-label="Send">
             <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M8 13V3M3.5 7.5L8 3l4.5 4.5" /></svg>
           </button>
         </div>
         <p className="hint">Margin can make mistakes. Check the files it cites.</p>
       </form>
     </>
   )
 }

