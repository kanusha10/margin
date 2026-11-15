import { useEffect, useState } from 'react'

/** Tiny hash router so no extra dependency is needed: #/ , #/login , #/register , #/app */
const read = () => window.location.hash.replace(/^#/, '') || '/'

export function useRoute() {
  const [route, setRoute] = useState(read)
  useEffect(() => {
    const on = () => setRoute(read())
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  return route
}

export const go = (path) => { window.location.hash = path }

/** Changes the URL without adding a history entry (used for redirects). */
export function replace(path) {
  window.history.replaceState(null, '', '#' + path)
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}
