/** Password field with a show/hide button. `show` and `onToggle` come from the parent so two fields can share one switch. */
export default function PasswordInput({ label, value, onChange, show, onToggle, autoComplete, minLength, placeholder }) {
  return (
    <label className="field">
      <span>{label}</span>
      <span className="pw">
        <input type={show ? 'text' : 'password'} required value={value} onChange={onChange}
               autoComplete={autoComplete} minLength={minLength} placeholder={placeholder} />
        <button type="button" className="pw-toggle" onClick={onToggle}
                aria-label={show ? 'Hide password' : 'Show password'} aria-pressed={show} title={show ? 'Hide password' : 'Show password'}>
          {show ? (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M3 3l18 18" />
              <path d="M10.6 5.1A10.6 10.6 0 0 1 12 5c5 0 8.7 3.4 10 7a11.6 11.6 0 0 1-2.6 4.1M6.5 6.6A11.7 11.7 0 0 0 2 12c1.3 3.6 5 7 10 7 1.5 0 2.9-.3 4.1-.9" />
              <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
            </svg>
          ) : (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M2 12c1.3-3.6 5-7 10-7s8.7 3.4 10 7c-1.3 3.6-5 7-10 7S3.3 15.6 2 12z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          )}
        </button>
      </span>
    </label>
  )
}
