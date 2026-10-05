import { describe, expect, it } from 'vitest'
import { profileSectionScrollTarget } from './profileNavigation'

describe('profileSectionScrollTarget', () => {
  it('keeps the page at the top when no subsection is requested', () => {
    expect(profileSectionScrollTarget('#/profile')).toBeNull()
    expect(profileSectionScrollTarget('#/profile?section=center')).toBeNull()
  })

  it('returns only an explicitly requested non-default subsection', () => {
    expect(profileSectionScrollTarget('#/profile?section=resume')).toBe('resume')
    expect(profileSectionScrollTarget('#/settings?section=ai')).toBe('ai')
    expect(profileSectionScrollTarget('#/profile?section=unknown')).toBeNull()
    expect(profileSectionScrollTarget('#/home?section=resume')).toBeNull()
  })
})
