const profileSections = new Set(['center', 'resume', 'ai', 'mail', 'data'])

/** The default profile section is the page entry point, so keep the page heading visible. */
export function profileSectionScrollTarget(hash: string): string | null {
  const route = hash.replace(/^#\/?/, '')
  if (!['profile', 'settings'].includes(route.split('?')[0] || '')) return null

  const section = new URLSearchParams(route.split('?')[1] || '').get('section')
  return section && profileSections.has(section) && section !== 'center' ? section : null
}
