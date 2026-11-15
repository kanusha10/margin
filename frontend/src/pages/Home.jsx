import { Brand } from '../components/Sidebar'
import { go } from '../router'

const jump = (id) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })

const STEPS = [
  { title: 'Paste a GitHub link', text: 'Any public repository works. Margin clones it and reads the source files.' },
  { title: 'Margin indexes the code', text: 'The code is split into small pieces and embedded so it can be searched by meaning, not only by exact words.' },
  { title: 'Ask in plain English', text: 'Watch the agent search, grep and open files, then read an answer that cites path and line.' },
]

const FEATURES = [
  ['It shows its work', 'Every search, grep and file it opens appears as a step while it works, so you can see how it reached the answer.'],
  ['Answers point to the code', 'Locations are cited as path:line, and the files it used are listed under the answer so you can check them yourself.'],
  ['Meaning and exact text', 'Semantic search finds the right area of the code. Literal search finds the exact name. The agent uses both, then reads the file to confirm.'],
  ['Your repositories stay in your account', 'Each repository belongs to the account that added it. Remove it and the cloned files and index are deleted.'],
  ['Made to work on the free Gemini tier', 'Requests are paced to stay under the limits. When Margin has to wait, it tells you instead of failing.'],
]


const FAQ = [
  ['Which repositories can I use?', 'Public GitHub repositories, entered as https://github.com/owner/repo. Private repositories are not supported.'],
  ['How long does indexing take?', 'Small repositories take a minute or two. Large ones take longer because the free Gemini tier limits how fast code can be embedded. Margin paces itself and shows the progress.'],
  ['Does it change my repository?', 'No. Margin makes a read-only copy on the server to search and read. Removing the repository deletes that copy and its index.'],
  ['Can the answers be wrong?', 'Yes, like any AI assistant. That is why each answer lists the files and lines it used. Open them and confirm before you rely on an answer.'],
  ['Do I need an account?', 'Yes. Your account keeps your repositories and questions separate from everyone else’s.'],
  ['Is my code sent to Google?', 'Yes. To search and answer, pieces of the code are sent to the Gemini API. Only public repositories are supported, so this is code that is already public.'],
]

function Demo() {
  return (
    <figure className="demo" aria-label="Example of a Margin answer">
      <figcaption className="demo-bar">
        <span className="demo-dots" aria-hidden="true"><i /><i /><i /></span>
        <span className="demo-file">Example question</span>
      </figcaption>
      <div className="demo-body">
        <h3 className="demo-q">How is a request authenticated?</h3>
        <ol className="trace demo-trace">
          <li><span className="verb">Searched for</span> <mark>JWT authentication filter</mark><span className="result">6 matches</span></li>
          <li><span className="verb">Opened</span> <mark>JwtFilter.java</mark><span className="result">lines 1 to 31</span></li>
          <li><span className="verb">Opened</span> <mark>SecurityConfig.java</mark><span className="result">lines 1 to 38</span></li>
        </ol>
        <div className="demo-code">
          <div className="code-lines" aria-hidden="true">
            <div><b>18</b><code>String header = req.getHeader("Authorization");</code></div>
            <div className="hit"><b>19</b><code>if (header != null && header.startsWith("Bearer ")) {'{'}</code></div>
            <div className="hit"><b>20</b><code>  jwt.parse(header.substring(7)).ifPresent(id -&gt;</code></div>
            <div><b>21</b><code>    SecurityContextHolder.getContext()...</code></div>
          </div>
          <p className="margin-note"><strong>Margin note</strong>The filter reads the Bearer token and stores the user id for the rest of the request.</p>
        </div>
        <div className="sources">
          <code className="chip">JwtFilter.java:1-31</code>
          <code className="chip">SecurityConfig.java:1-38</code>
        </div>
      </div>
    </figure>
  )
}

export default function Home({ user }) {
  return (
    <div className="site">
      <header className="site-nav">
        <button className="brand-btn" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })} aria-label="Margin, back to top"><Brand /></button>
        <nav className="site-links" aria-label="Sections">
          <button onClick={() => jump('how')}>How it works</button>
          <button onClick={() => jump('features')}>Features</button>
          <button onClick={() => jump('faq')}>FAQ</button>
        </nav>
        <div className="site-actions">
          {user ? (
            <button className="solid" onClick={() => go('/app')}>Open dashboard</button>
          ) : (
            <>
              <button className="ghost" onClick={() => go('/login')}>Sign in</button>
              <button className="solid" onClick={() => go('/register')}>Get started</button>
            </>
          )}
        </div>
      </header>

      <main>
        <section className="hero">
          <div className="hero-copy">
            <h1>Ask your codebase how it works. Get answers with file and line.</h1>
            <p>Margin reads a public GitHub repository, then answers your questions by searching and opening the real code. Every answer cites where it found things.</p>
            <div className="hero-cta">
              {user ? (
                <button className="solid big" onClick={() => go('/app')}>Open dashboard</button>
              ) : (
                <>
                  <button className="solid big" onClick={() => go('/register')}>Create an account</button>
                  <button className="ghost big" onClick={() => go('/login')}>Sign in</button>
                </>
              )}
            </div>
            <p className="hero-small">Public GitHub repositories only.</p>
          </div>
          <Demo />
        </section>

        <section className="sec" id="how">
          <h2>From a link to an answer in three steps</h2>
          <ol className="steps">
            {STEPS.map((s, i) => (
              <li key={s.title}>
                <span className="step-n">{i + 1}</span>
                <h3>{s.title}</h3>
                <p>{s.text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="sec split" id="features">
          <div className="split-head">
            <h2>Built for reading unfamiliar code</h2>
            <p>New to a project, reviewing a dependency, or coming back after a year away. Margin does the searching so you can do the understanding.</p>
          </div>
          <dl className="rows">
            {FEATURES.map(([t, d]) => (
              <div key={t}><dt>{t}</dt><dd>{d}</dd></div>
            ))}
          </dl>
        </section>

        <section className="sec faq" id="faq">
          <h2>Common questions</h2>
          <div className="faq-list">
            {FAQ.map(([q, a]) => (
              <details key={q}>
                <summary>{q}</summary>
                <p>{a}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="cta-band">
          <h2>Point Margin at a repository.</h2>
          <p>Create an account, paste a link and ask your first question.</p>
          <div className="hero-cta">
            {user ? (
              <button className="solid big" onClick={() => go('/app')}>Open dashboard</button>
            ) : (
              <>
                <button className="solid big" onClick={() => go('/register')}>Create an account</button>
                <button className="ghost big" onClick={() => go('/login')}>Sign in</button>
              </>
            )}
          </div>
        </section>
      </main>

      <footer className="site-foot">
        <Brand />
        <p>Ask questions about any public GitHub repository.</p>
        <nav aria-label="Footer">
          <button onClick={() => jump('how')}>How it works</button>
          <button onClick={() => jump('features')}>Features</button>
          <button onClick={() => jump('faq')}>FAQ</button>
          <button onClick={() => go(user ? '/app' : '/login')}>{user ? 'Dashboard' : 'Sign in'}</button>
        </nav>
      </footer>
    </div>
  )
}
