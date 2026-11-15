import { useCallback, useEffect, useState } from 'react'
import * as api from './api'
import Sidebar from './components/Sidebar'
import Chat from './components/Chat'
import NewRepoModal from './components/NewRepoModal'
import Auth from './pages/Auth'
import Home from './pages/Home'
import Dashboard from './pages/Dashboard'
import { useRoute, replace } from './router'

const WORKING = ['CLONING', 'INDEXING']
const PAGES = ['/', '/login', '/register', '/app']


// "sam.k99@gmail.com" -> "Sam K"
function displayName(email) {
  const base = (email || '').split('@')[0].replace(/\d+$/, '')
  const words = base.split(/[._-]+/).filter(Boolean)
  const name = words.map((w) => w[0].toUpperCase() + w.slice(1)).join(' ')
  return name || 'there'
}

export default function App() {
  const route = useRoute()
  const [user, setUser] = useState(() => api.getSession()?.email || null)
  const [repos, setRepos] = useState([])
  const [loaded, setLoaded] = useState(false)
  const [offline, setOffline] = useState(false)
  const [activeId, setActiveId] = useState(null)
  const [threads, setThreads] = useState({})
  const [drawer, setDrawer] = useState(false)
  const [adding, setAdding] = useState(false)

  const reset = useCallback(() => {
    setUser(null); setRepos([]); setActiveId(null); setThreads({}); setLoaded(false); setDrawer(false); setAdding(false)
  }, [])
  useEffect(() => { api.onAuthLost(reset) }, [reset])

  const signOut = () => { api.logout(); reset(); replace('/') }

  const refresh = useCallback(async () => {
    try { setRepos(await api.listRepos()); setOffline(false) }
    catch { setOffline(true) }
    setLoaded(true)
  }, [])

  useEffect(() => { if (user) refresh() }, [user, refresh])

  const indexing = repos.some((r) => WORKING.includes(r.status))
  useEffect(() => {
    if (!indexing) return
    const t = setInterval(refresh, 2000)
    return () => clearInterval(t)
  }, [indexing, refresh])

  let page = PAGES.includes(route) ? route : '/'
  if (user && (page === '/login' || page === '/register')) page = '/app'
  if (!user && page === '/app') page = '/login'
  useEffect(() => { if (page !== route) replace(page) }, [page, route])
  useEffect(() => { window.scrollTo(0, 0) }, [page])

  if (page === '/') return <Home user={user} />
  if (page === '/login' || page === '/register') return <Auth key={page} mode={page === '/login' ? 'login' : 'register'} onDone={setUser} />

  const name = displayName(user)
  const active = repos.find((r) => r.id === activeId)

  async function add(url) {
    const repo = await api.addRepo(url)
    await refresh()
    setActiveId(repo.id)
    setAdding(false)
    setDrawer(false)
  }

  async function remove(id) {
    await api.removeRepo(id)
    setThreads((t) => { const n = { ...t }; delete n[id]; return n })
    if (id === activeId) setActiveId(null)
    refresh()
  }

  const open = (id) => { setActiveId(id); setDrawer(false) }
  const home = () => { setActiveId(null); setDrawer(false) }
  const startNew = () => { setAdding(true); setDrawer(false) }
  const setEntries = (fn) => setThreads((t) => ({ ...t, [activeId]: fn(t[activeId] || []) }))

  return (
    <div className={`app ${drawer ? 'drawer-open' : ''}`}>
      <Sidebar repos={repos} activeId={activeId} user={user} name={name} onLogout={signOut}
               onHome={home} onNew={startNew} onSelect={open} onRemove={remove} />
      <main className="main">
        <header className="topbar">
          <button className="icon-btn" onClick={() => setDrawer(!drawer)} aria-label="Toggle repository list">
            <svg width="18" height="18" viewBox="0 0 18 18"><path d="M2 5h14M2 9h14M2 13h14" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>
          </button>
          <span className="topbar-title">{active ? active.name : 'Dashboard'}</span>
        </header>

        {offline && <p className="banner">Can't reach the backend on port 8080. Start it and this page will reconnect.</p>}

        {!loaded ? (
          <p className="loading">Loading your repositories</p>
        ) : !active ? (
          <div className="scroll"><Dashboard repos={repos} name={name} onAdd={add} /></div>
        ) : (
          <Chat key={active.id} repo={active} entries={threads[active.id] || []} setEntries={setEntries} onRemove={remove} />
        )}
      </main>
      {drawer && <div className="scrim" onClick={() => setDrawer(false)} />}
      {adding && <NewRepoModal onAdd={add} onClose={() => setAdding(false)} />}
    </div>
  )
}