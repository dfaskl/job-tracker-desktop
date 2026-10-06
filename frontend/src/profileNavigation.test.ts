import { describe, expect, it } from 'vitest'
import { profileSectionScrollTarget, profileToggleDestination } from './profileNavigation'

describe('profileSectionScrollTarget', () => {
  it('keeps the page at the top when no subsection is requested', () => {
    expect(profileSectionScrollTarget('#/profile')).toBeNull()
    expect(profileSectionScrollTarget('#/profile?section=center')).toBeNull()
  })

  it('returns only an explicitly requested non-default subsection', () => {
    expect(profileSectionScrollTarget('#/profile?section=resume')).toBe('resume')
    expect(profileSectionScrollTarget('#/settings?section=ai')).toBe('ai')
    expect(profileSectionScrollTarget('#/profile?section=mail')).toBe('mail')
    expect(profileSectionScrollTarget('#/profile?section=data')).toBe('data')
    expect(profileSectionScrollTarget('#/profile?section=unknown')).toBeNull()
    expect(profileSectionScrollTarget('#/home?section=resume')).toBeNull()
  })
})

describe('profileToggleDestination', () => {
  it('opens the profile from the current page', () => {
    expect(profileToggleDestination('applications', 'home')).toBe('profile')
  })

  it('returns to the page that opened the profile', () => {
    expect(profileToggleDestination('profile', 'calendar')).toBe('calendar')
  })

  it('falls back to home if the remembered page is profile itself', () => {
    expect(profileToggleDestination('profile', 'profile')).toBe('home')
  })
})
