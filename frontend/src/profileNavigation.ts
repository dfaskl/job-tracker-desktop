const profileSections = new Set(['center', 'resume', 'ai', 'mail', 'data'])

/** Profile is a toggle destination: return to the page it was opened from. */
export function profileToggleDestination(activePage: string, previousPage: string): string {
  if (activePage !== 'profile') return 'profile'
  return previousPage === 'profile' ? 'home' : previousPage
}

/** The default profile section is the page entry point, so keep the page heading visible. */
export function profileSectionScrollTarget(hash: string): string | null {
  const route = hash.replace(/^#\/?/, '')
  if (!['profile', 'settings'].includes(route.split('?')[0] || '')) return null

  const section = new URLSearchParams(route.split('?')[1] || '').get('section')
  return section && profileSections.has(section) && section !== 'center' ? section : null
}
